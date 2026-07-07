package com.contractreview.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.contractreview.common.BusinessException;
import com.contractreview.domain.dto.*;
import com.contractreview.domain.entity.ReviewReport;
import com.contractreview.domain.entity.ReviewTask;
import com.contractreview.domain.entity.RiskItem;
import com.contractreview.domain.entity.User;
import com.contractreview.domain.enums.ErrorCode;
import com.contractreview.mapper.*;
import com.contractreview.service.ContractService;
import com.contractreview.util.DesensitizationUtil;
import com.contractreview.util.FileUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ContractServiceImpl implements ContractService {

    private final ObjectMapper objectMapper;
    private final FileUtil fileUtil;
    private final ReviewTaskMapper taskMapper;
    private final RiskItemMapper riskItemMapper;
    private final ReviewReportMapper reportMapper;
    private final UserMapper userMapper;
    private final MinioClient minioClient;
    private final RedisTemplate<String, Object> redisTemplate;
    private final LLMReviewService llmReviewService;

    @Value("${minio.bucket}")
    private String bucket;

    @PostConstruct
    public void init() {
        try {
            boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
            if (!exists) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                log.info("Created MinIO bucket: {}", bucket);
            }
        } catch (Exception e) {
            log.warn("MinIO bucket init failed (might not be running yet): {}", e.getMessage());
        }
    }

    @Override
    @Transactional
    public UploadResponse upload(MultipartFile file, Long userId) {
        fileUtil.validateFile(file);
        String rawText = fileUtil.extractText(file);
        String desensitizedText = DesensitizationUtil.desensitize(rawText);

        String fileUrl = uploadToMinio(file, userId);

        ReviewTask task = new ReviewTask();
        task.setUserId(userId);
        task.setFileName(file.getOriginalFilename());
        task.setFileSize(file.getSize());
        task.setPreviewText(desensitizedText);
        task.setFileUrl(fileUrl);
        task.setStatus("PENDING");
        task.setProgress(0);
        taskMapper.insert(task);

        return new UploadResponse(task.getId(), desensitizedText);
    }

    private String uploadToMinio(MultipartFile file, Long userId) {
        try (InputStream is = file.getInputStream()) {
            String objectName = userId + "/" + UUID.randomUUID() + "_" + file.getOriginalFilename();
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectName)
                    .stream(is, file.getSize(), -1)
                    .contentType(file.getContentType())
                    .build());
            return objectName;
        } catch (Exception e) {
            log.error("MinIO upload failed", e);
            throw new BusinessException(500, "文件上传失败");
        }
    }

    @Override
    @Transactional
    public void submit(Long taskId, Long userId) {
        ReviewTask task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException(ErrorCode.TASK_NOT_FOUND);
        }
        if (!task.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.TASK_NOT_FOUND);
        }
        if (!"PENDING".equals(task.getStatus())) {
            throw new BusinessException(ErrorCode.INVALID_STATE);
        }

        String quotaKey = "user:quota:" + userId;
        Integer quota = (Integer) redisTemplate.opsForValue().get(quotaKey);
        if (quota == null) {
            User user = userMapper.selectById(userId);
            if (user == null) {
                throw new BusinessException(ErrorCode.TASK_NOT_FOUND);
            }
            quota = user.getReviewQuota();
            redisTemplate.opsForValue().set(quotaKey, quota);
        }
        if (quota <= 0) {
            throw new BusinessException(ErrorCode.QUOTA_INSUFFICIENT);
        }
        redisTemplate.opsForValue().decrement(quotaKey);

        task.setStatus("PROCESSING");
        task.setProgress(5);
        taskMapper.updateById(task);

        startReviewAsync(taskId, userId);
    }

    @Async("taskExecutor")
    public void startReviewAsync(Long taskId, Long userId) {
        try {
            ReviewTask task = taskMapper.selectById(taskId);
            task.setProgress(30);
            taskMapper.updateById(task);

            String text = task.getPreviewText();
            String result = llmReviewService.reviewContract(text);

            task.setProgress(70);
            taskMapper.updateById(task);

            saveReviewResult(taskId, result);

            task.setStatus("SUCCESS");
            task.setProgress(100);
            task.setCompletedAt(LocalDateTime.now());
            taskMapper.updateById(task);
        } catch (Exception e) {
            log.error("Review failed for task {}: {}", taskId, e.getMessage());
            ReviewTask task = taskMapper.selectById(taskId);
            if (task != null) {
                task.setStatus("FAILED");
                task.setProgress(-1);
                task.setErrorMsg(e.getMessage());
                task.setCompletedAt(LocalDateTime.now());
                taskMapper.updateById(task);

                String quotaKey = "user:quota:" + userId;
                redisTemplate.opsForValue().increment(quotaKey);

                User user = userMapper.selectById(userId);
                if (user != null) {
                    user.setReviewQuota(user.getReviewQuota() + 1);
                    userMapper.updateById(user);
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void saveReviewResult(Long taskId, String llmResult) {
        Map<String, Object> result;
        try {
            result = objectMapper.readValue(llmResult, Map.class);
        } catch (Exception e) {
            log.warn("LLM result may not be valid JSON, storing raw: {}", e.getMessage());
            result = new HashMap<>();
            result.put("summary", llmResult);
            result.put("riskCount", Map.of("high", 0, "medium", 0, "low", 0));
            result.put("risks", List.of());
        }

        String summary = (String) result.getOrDefault("summary", "");

        Map<String, Integer> riskCount;
        Object rc = result.get("riskCount");
        if (rc instanceof Map) {
            riskCount = (Map<String, Integer>) rc;
        } else {
            riskCount = new HashMap<>();
            riskCount.put("high", 0);
            riskCount.put("medium", 0);
            riskCount.put("low", 0);
        }

        List<Map<String, Object>> risks;
        Object r = result.get("risks");
        if (r instanceof List) {
            risks = (List<Map<String, Object>>) r;
        } else {
            risks = List.of();
        }

        for (Map<String, Object> riskMap : risks) {
            RiskItem item = new RiskItem();
            item.setTaskId(taskId);
            item.setClauseIndex((Integer) riskMap.getOrDefault("clauseIndex", 0));
            item.setClauseContent((String) riskMap.getOrDefault("clauseContent", ""));
            item.setRiskLevel((String) riskMap.getOrDefault("riskLevel", "LOW"));
            item.setRiskType((String) riskMap.getOrDefault("riskType", ""));
            item.setDescription((String) riskMap.getOrDefault("description", ""));
            item.setSuggestion((String) riskMap.getOrDefault("suggestion", ""));
            Object laws = riskMap.get("relatedLaws");
            if (laws instanceof List) {
                try {
                    item.setRelatedLaws(objectMapper.writeValueAsString(laws));
                } catch (JsonProcessingException e) {
                    log.warn("Failed to serialize relatedLaws", e);
                }
            }
            riskItemMapper.insert(item);
        }

        ReviewReport report = new ReviewReport();
        report.setTaskId(taskId);
        report.setSummary(summary);
        report.setRiskCountHigh(riskCount.getOrDefault("high", 0));
        report.setRiskCountMedium(riskCount.getOrDefault("medium", 0));
        report.setRiskCountLow(riskCount.getOrDefault("low", 0));
        try {
            report.setReportJson(objectMapper.writeValueAsString(result));
        } catch (Exception e) {
            report.setReportJson("{}");
        }
        reportMapper.insert(report);
    }

    @Override
    public StatusResponse getStatus(Long taskId, Long userId) {
        ReviewTask task = taskMapper.selectById(taskId);
        if (task == null || !task.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.TASK_NOT_FOUND);
        }
        return new StatusResponse(task.getId(), task.getStatus(), task.getProgress());
    }

    @Override
    public ReportResponse getReport(Long taskId, Long userId) {
        ReviewTask task = taskMapper.selectById(taskId);
        if (task == null || !task.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.TASK_NOT_FOUND);
        }
        if (!"SUCCESS".equals(task.getStatus())) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "任务尚未完成");
        }

        ReviewReport report = reportMapper.selectOne(
                new LambdaQueryWrapper<ReviewReport>().eq(ReviewReport::getTaskId, taskId));
        if (report == null) {
            throw new BusinessException(ErrorCode.TASK_NOT_FOUND, "报告不存在");
        }

        List<RiskItem> riskItems = riskItemMapper.selectList(
                new LambdaQueryWrapper<RiskItem>().eq(RiskItem::getTaskId, taskId));
        List<RiskItemDto> riskDtos = riskItems.stream().map(item -> {
            List<String> laws = List.of();
            if (item.getRelatedLaws() != null && !item.getRelatedLaws().isEmpty()) {
                try {
                    laws = objectMapper.readValue(item.getRelatedLaws(), List.class);
                } catch (Exception ignored) {}
            }
            return new RiskItemDto(item.getClauseIndex(), item.getClauseContent(),
                    item.getRiskLevel(), item.getRiskType(), item.getDescription(),
                    item.getSuggestion(), laws);
        }).collect(Collectors.toList());

        Map<String, Integer> riskCount = Map.of(
                "high", report.getRiskCountHigh(),
                "medium", report.getRiskCountMedium(),
                "low", report.getRiskCountLow()
        );

        return new ReportResponse(taskId, report.getSummary(), riskCount, riskDtos,
                report.getCreatedAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
    }

    @Override
    public HistoryResponse getHistory(Long userId, int page, int size) {
        Page<ReviewTask> pageObj = new Page<>(page, size);
        Page<ReviewTask> result = taskMapper.selectPage(pageObj,
                new LambdaQueryWrapper<ReviewTask>()
                        .eq(ReviewTask::getUserId, userId)
                        .orderByDesc(ReviewTask::getCreatedAt));

        List<HistoryResponse.HistoryItem> items = result.getRecords().stream()
                .map(t -> new HistoryResponse.HistoryItem(
                        t.getId(), t.getFileName(), t.getStatus(),
                        t.getProgress(),
                        t.getCreatedAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)))
                .collect(Collectors.toList());

        return new HistoryResponse(items, result.getTotal(), page, size);
    }

    @Override
    @Transactional
    public void retry(Long taskId, Long userId) {
        ReviewTask task = taskMapper.selectById(taskId);
        if (task == null || !task.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.TASK_NOT_FOUND);
        }
        if (!"FAILED".equals(task.getStatus())) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "仅失败状态的任务可重试");
        }

        task.setStatus("PENDING");
        task.setProgress(0);
        task.setErrorMsg(null);
        task.setCompletedAt(null);
        taskMapper.updateById(task);
    }
}
