package com.contractreview.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.contractreview.common.BusinessException;
import com.contractreview.config.RabbitMqConfig;
import com.contractreview.domain.dto.*;
import com.contractreview.domain.entity.ReviewReport;
import com.contractreview.domain.entity.ReviewTask;
import com.contractreview.domain.entity.RiskItem;
import com.contractreview.domain.entity.User;
import com.contractreview.domain.enums.ErrorCode;
import com.contractreview.domain.enums.TaskStatus;
import com.contractreview.domain.entity.ReviewProcessLog;
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
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
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
    private final ReviewProcessLogMapper processLogMapper;
    private final MinioClient minioClient;
    private final RedisTemplate<String, Object> redisTemplate;
    private final DefaultRedisScript<Long> quotaDeductScript;
    private final RabbitTemplate rabbitTemplate;

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
    public UploadResponse upload(MultipartFile file, Long userId, boolean desensitize) {
        fileUtil.validateFile(file);
        String rawText = fileUtil.extractText(file);
        String processedText = desensitize ? DesensitizationUtil.desensitize(rawText) : rawText;

        String fileUrl = uploadToMinio(file, userId);

        ReviewTask task = new ReviewTask();
        task.setUserId(userId);
        task.setFileName(file.getOriginalFilename());
        task.setFileSize(file.getSize());
        task.setPreviewText(processedText);
        task.setFileUrl(fileUrl);
        task.setStatus(TaskStatus.PENDING.name());
        task.setProgress(0);
        taskMapper.insert(task);

        return new UploadResponse(task.getId(), processedText);
    }

    @Override
    @Transactional
    public UploadResponse pasteText(String text, Long userId, boolean desensitize) {
        if (text == null || text.trim().isEmpty()) {
            throw new BusinessException(400, "合同文本不能为空");
        }
        String processedText = desensitize ? DesensitizationUtil.desensitize(text) : text;

        ReviewTask task = new ReviewTask();
        task.setUserId(userId);
        task.setFileName("粘贴文本_" + System.currentTimeMillis() + ".txt");
        task.setFileSize((long) text.getBytes().length);
        task.setPreviewText(processedText);
        task.setFileUrl(null);
        task.setStatus(TaskStatus.PENDING.name());
        task.setProgress(0);
        taskMapper.insert(task);

        return new UploadResponse(task.getId(), processedText);
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
        if (!TaskStatus.PENDING.name().equals(task.getStatus())) {
            throw new BusinessException(ErrorCode.INVALID_STATE);
        }

        String quotaKey = "user:quota:" + userId;
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.TASK_NOT_FOUND);
        }
        Long newQuota = redisTemplate.execute(quotaDeductScript,
                Collections.singletonList(quotaKey), 1, user.getReviewQuota());
        if (newQuota == null || newQuota < 0) {
            throw new BusinessException(ErrorCode.QUOTA_INSUFFICIENT);
        }

        ReviewMessage message = new ReviewMessage(taskId, userId, 0);
        rabbitTemplate.convertAndSend(RabbitMqConfig.EXCHANGE_REVIEW,
                RabbitMqConfig.ROUTING_KEY, message);
        log.info("Sent review message to MQ: taskId={}, userId={}", taskId, userId);
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
        if (!TaskStatus.SUCCESS.name().equals(task.getStatus())) {
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
                } catch (Exception e) {
                    log.warn("Failed to deserialize relatedLaws for risk item {}: {}", item.getId(), e.getMessage());
                }
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
    public HistoryResponse getHistory(Long userId, int page, int size, String status) {
        Page<ReviewTask> pageObj = new Page<>(page, size);
        LambdaQueryWrapper<ReviewTask> wrapper = new LambdaQueryWrapper<ReviewTask>()
                .eq(ReviewTask::getUserId, userId);
        if (status != null && !status.isEmpty() && !"ALL".equals(status)) {
            String[] statuses = status.split(",");
            if (statuses.length == 1) {
                wrapper.eq(ReviewTask::getStatus, statuses[0]);
            } else {
                wrapper.in(ReviewTask::getStatus, (Object[]) statuses);
            }
        }
        Page<ReviewTask> result = taskMapper.selectPage(pageObj, wrapper.orderByDesc(ReviewTask::getCreatedAt));

        List<HistoryResponse.HistoryItem> items = result.getRecords().stream()
                .map(t -> new HistoryResponse.HistoryItem(
                        t.getId(), t.getFileName(), t.getStatus(),
                        t.getProgress(),
                        t.getCreatedAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)))
                .collect(Collectors.toList());

        return new HistoryResponse(items, result.getTotal(), page, size);
    }

    @Override
    public String getPreviewText(Long taskId, Long userId) {
        ReviewTask task = taskMapper.selectById(taskId);
        if (task == null || !task.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.TASK_NOT_FOUND);
        }
        return task.getPreviewText();
    }

    @Override
    public List<ContractService.ReviewProcessLogDto> getProcessLogs(Long taskId, Long userId) {
        ReviewTask task = taskMapper.selectById(taskId);
        if (task == null || !task.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.TASK_NOT_FOUND);
        }
        List<ReviewProcessLog> logs = processLogMapper.selectList(
                new LambdaQueryWrapper<ReviewProcessLog>()
                        .eq(ReviewProcessLog::getTaskId, taskId)
                        .orderByAsc(ReviewProcessLog::getCreatedAt));
        return logs.stream()
                .map(l -> new ContractService.ReviewProcessLogDto(
                        l.getAgent(), l.getContent(),
                        l.getCreatedAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void retry(Long taskId, Long userId) {
        ReviewTask task = taskMapper.selectById(taskId);
        if (task == null || !task.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.TASK_NOT_FOUND);
        }
        if (!TaskStatus.FAILED.name().equals(task.getStatus())) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "仅失败状态的任务可重试");
        }

        task.setStatus(TaskStatus.PENDING.name());
        task.setProgress(0);
        task.setErrorMsg(null);
        task.setCompletedAt(null);
        taskMapper.updateById(task);

        ReviewMessage message = new ReviewMessage(taskId, userId, 0);
        rabbitTemplate.convertAndSend(RabbitMqConfig.EXCHANGE_REVIEW,
                RabbitMqConfig.ROUTING_KEY, message);
        log.info("Re-queued task {} via retry", taskId);
    }
}
