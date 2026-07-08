package com.contractreview.service.impl;

import com.contractreview.common.BusinessException;
import com.contractreview.domain.dto.*;
import com.contractreview.domain.entity.ReviewReport;
import com.contractreview.domain.entity.ReviewTask;
import com.contractreview.domain.entity.RiskItem;
import com.contractreview.domain.entity.User;
import com.contractreview.domain.enums.ErrorCode;
import com.contractreview.mapper.*;
import com.contractreview.util.FileUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.minio.MinioClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ContractServiceImplTest {

    @Mock private ObjectMapper objectMapper;
    @Mock private FileUtil fileUtil;
    @Mock private ReviewTaskMapper taskMapper;
    @Mock private RiskItemMapper riskItemMapper;
    @Mock private ReviewReportMapper reportMapper;
    @Mock private UserMapper userMapper;
    @Mock private MinioClient minioClient;
    @Mock private RedisTemplate<String, Object> redisTemplate;
    @Mock private DefaultRedisScript<Long> quotaDeductScript;
    @Mock private RabbitTemplate rabbitTemplate;

    @SuppressWarnings("unchecked")
    private org.springframework.data.redis.core.ValueOperations<String, Object> valueOps;

    private ContractServiceImpl contractService;
    private final Long userId = 1L;
    private final Long taskId = 100L;

    @BeforeEach
    void setUp() {
        valueOps = mock(org.springframework.data.redis.core.ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        contractService = new ContractServiceImpl(
                objectMapper, fileUtil, taskMapper, riskItemMapper,
                reportMapper, userMapper, minioClient, redisTemplate, quotaDeductScript, rabbitTemplate);
        ReflectionTestUtils.setField(contractService, "bucket", "test-bucket");
    }

    // ==================== upload ====================

    @Test
    @DisplayName("上传成功（脱敏）")
    void testUploadSuccessWithDesensitize() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "contract.pdf",
                "application/pdf", "甲方：张三，身份证号：110101199001011234".getBytes());
        doNothing().when(fileUtil).validateFile(any());
        when(fileUtil.extractText(any())).thenReturn("甲方：***，身份证号：***");
        when(minioClient.putObject(any())).thenReturn(null);
        doAnswer(inv -> {
            ReviewTask t = inv.getArgument(0);
            t.setId(taskId);
            return 1;
        }).when(taskMapper).insert(any(ReviewTask.class));

        UploadResponse resp = contractService.upload(file, userId, true);

        assertNotNull(resp);
        assertEquals(taskId, resp.getTaskId());
        assertEquals("甲方：***，身份证号：***", resp.getPreviewText());
        ArgumentCaptor<ReviewTask> captor = ArgumentCaptor.forClass(ReviewTask.class);
        verify(taskMapper).insert(captor.capture());
        assertEquals("PENDING", captor.getValue().getStatus());
        assertEquals(userId, captor.getValue().getUserId());
    }

    @Test
    @DisplayName("上传成功（不脱敏）")
    void testUploadWithoutDesensitize() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "contract.pdf",
                "application/pdf", "甲方：张三，电话：13800138000".getBytes());
        doNothing().when(fileUtil).validateFile(any());
        when(fileUtil.extractText(any())).thenReturn("甲方：张三，电话：13800138000");
        when(minioClient.putObject(any())).thenReturn(null);
        doAnswer(inv -> {
            ReviewTask t = inv.getArgument(0);
            t.setId(taskId);
            return 1;
        }).when(taskMapper).insert(any(ReviewTask.class));

        UploadResponse resp = contractService.upload(file, userId, false);

        assertEquals("甲方：张三，电话：13800138000", resp.getPreviewText());
    }

    @Test
    @DisplayName("上传文件校验失败")
    void testUploadFileValidationFails() {
        MockMultipartFile file = new MockMultipartFile("file", "bad.exe",
                "application/octet-stream", "bad content".getBytes());
        doThrow(new BusinessException(ErrorCode.FORMAT_NOT_SUPPORTED))
                .when(fileUtil).validateFile(any());

        assertThrows(BusinessException.class, () -> contractService.upload(file, userId, true));
        verify(taskMapper, never()).insert(any(ReviewTask.class));
    }

    @Test
    @DisplayName("上传 MinIO 失败")
    void testUploadMinioFails() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "contract.pdf",
                "application/pdf", "content".getBytes());
        doNothing().when(fileUtil).validateFile(any());
        when(fileUtil.extractText(any())).thenReturn("content");
        when(minioClient.putObject(any())).thenThrow(new RuntimeException("MinIO error"));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> contractService.upload(file, userId, true));
        assertEquals(500, ex.getCode());
    }

    // ==================== submit ====================

    private ReviewTask createPendingTask() {
        ReviewTask task = new ReviewTask();
        task.setId(taskId);
        task.setUserId(userId);
        task.setStatus("PENDING");
        task.setProgress(0);
        task.setPreviewText("合同内容");
        return task;
    }

    @Test
    @DisplayName("提交成功（发送 MQ 消息）")
    void testSubmitSuccess() {
        ReviewTask task = createPendingTask();
        when(taskMapper.selectById(taskId)).thenReturn(task);
        when(redisTemplate.execute(eq(quotaDeductScript), eq(Collections.singletonList("user:quota:" + userId)), eq(1)))
                .thenReturn(4L);

        contractService.submit(taskId, userId);

        verify(redisTemplate).execute(eq(quotaDeductScript), eq(Collections.singletonList("user:quota:" + userId)), eq(1));
        verify(rabbitTemplate).convertAndSend(anyString(), anyString(), any(ReviewMessage.class));
    }

    @Test
    @DisplayName("提交时任务不存在")
    void testSubmitTaskNotFound() {
        when(taskMapper.selectById(taskId)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> contractService.submit(taskId, userId));
        assertEquals(ErrorCode.TASK_NOT_FOUND.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("提交时任务不属于当前用户")
    void testSubmitWrongUser() {
        ReviewTask task = createPendingTask();
        task.setUserId(999L);
        when(taskMapper.selectById(taskId)).thenReturn(task);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> contractService.submit(taskId, userId));
        assertEquals(ErrorCode.TASK_NOT_FOUND.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("提交时任务状态不是 PENDING")
    void testSubmitInvalidState() {
        ReviewTask task = createPendingTask();
        task.setStatus("PROCESSING");
        when(taskMapper.selectById(taskId)).thenReturn(task);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> contractService.submit(taskId, userId));
        assertEquals(ErrorCode.INVALID_STATE.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("提交时 Redis 无配额缓存且用户也不存在")
    void testSubmitNoQuotaInRedisAndUserNotFound() {
        ReviewTask task = createPendingTask();
        when(taskMapper.selectById(taskId)).thenReturn(task);
        when(redisTemplate.execute(eq(quotaDeductScript), eq(Collections.singletonList("user:quota:" + userId)), eq(1)))
                .thenReturn(-1L);
        when(redisTemplate.hasKey("user:quota:" + userId)).thenReturn(false);
        when(userMapper.selectById(userId)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> contractService.submit(taskId, userId));
        assertEquals(ErrorCode.TASK_NOT_FOUND.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("提交时配额为 0 抛出异常")
    void testSubmitQuotaInsufficient() {
        ReviewTask task = createPendingTask();
        when(taskMapper.selectById(taskId)).thenReturn(task);
        when(redisTemplate.execute(eq(quotaDeductScript), eq(Collections.singletonList("user:quota:" + userId)), eq(1)))
                .thenReturn(-1L);
        User user = new User();
        user.setId(userId);
        user.setReviewQuota(0);
        when(userMapper.selectById(userId)).thenReturn(user);
        when(redisTemplate.hasKey("user:quota:" + userId)).thenReturn(true);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> contractService.submit(taskId, userId));
        assertEquals(ErrorCode.QUOTA_INSUFFICIENT.getCode(), ex.getCode());
    }

    // ==================== getStatus ====================

    @Test
    @DisplayName("查询状态成功")
    void testGetStatusSuccess() {
        ReviewTask task = createPendingTask();
        when(taskMapper.selectById(taskId)).thenReturn(task);

        StatusResponse resp = contractService.getStatus(taskId, userId);

        assertEquals(taskId, resp.getTaskId());
        assertEquals("PENDING", resp.getStatus());
        assertEquals(0, resp.getProgress());
    }

    @Test
    @DisplayName("查询状态时任务不存在")
    void testGetStatusNotFound() {
        when(taskMapper.selectById(taskId)).thenReturn(null);

        assertThrows(BusinessException.class, () -> contractService.getStatus(taskId, userId));
    }

    @Test
    @DisplayName("查询状态时任务不属于当前用户")
    void testGetStatusWrongUser() {
        ReviewTask task = createPendingTask();
        task.setUserId(999L);
        when(taskMapper.selectById(taskId)).thenReturn(task);

        assertThrows(BusinessException.class, () -> contractService.getStatus(taskId, userId));
    }

    // ==================== getReport ====================

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("获取报告成功")
    void testGetReportSuccess() throws Exception {
        ReviewTask task = createPendingTask();
        task.setStatus("SUCCESS");
        when(taskMapper.selectById(taskId)).thenReturn(task);

        ReviewReport report = new ReviewReport();
        report.setSummary("审查总结");
        report.setRiskCountHigh(1);
        report.setRiskCountMedium(0);
        report.setRiskCountLow(2);
        report.setCreatedAt(LocalDateTime.of(2026, 7, 8, 12, 0));
        when(reportMapper.selectOne(any())).thenReturn(report);

        RiskItem riskItem = new RiskItem();
        riskItem.setClauseIndex(1);
        riskItem.setClauseContent("条款内容");
        riskItem.setRiskLevel("HIGH");
        riskItem.setRiskType("违约金");
        riskItem.setDescription("风险描述");
        riskItem.setSuggestion("修改建议");
        riskItem.setRelatedLaws("[\"民法典第585条\"]");
        when(riskItemMapper.selectList(any())).thenReturn(List.of(riskItem));
        when(objectMapper.readValue(anyString(), eq(List.class)))
                .thenReturn(List.of("民法典第585条"));

        ReportResponse resp = contractService.getReport(taskId, userId);

        assertEquals(taskId, resp.getTaskId());
        assertEquals("审查总结", resp.getSummary());
        assertEquals(1, resp.getRiskCount().get("high"));
        assertEquals(2, resp.getRiskCount().get("low"));
        assertEquals(1, resp.getRisks().size());
        assertEquals("条款内容", resp.getRisks().get(0).getClauseContent());
        assertEquals(List.of("民法典第585条"), resp.getRisks().get(0).getRelatedLaws());
    }

    @Test
    @DisplayName("获取报告时任务未完成")
    void testGetReportTaskNotCompleted() {
        ReviewTask task = createPendingTask();
        task.setStatus("PROCESSING");
        when(taskMapper.selectById(taskId)).thenReturn(task);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> contractService.getReport(taskId, userId));
        assertEquals(ErrorCode.INVALID_STATE.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("获取报告时报告不存在")
    void testGetReportNotFound() {
        ReviewTask task = createPendingTask();
        task.setStatus("SUCCESS");
        when(taskMapper.selectById(taskId)).thenReturn(task);
        when(reportMapper.selectOne(any())).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> contractService.getReport(taskId, userId));
        assertEquals(ErrorCode.TASK_NOT_FOUND.getCode(), ex.getCode());
    }

    // ==================== getHistory ====================

    @Test
    @DisplayName("查询历史记录成功")
    void testGetHistorySuccess() {
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<ReviewTask> pageResult =
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(1, 10, 1);
        ReviewTask task = createPendingTask();
        task.setCreatedAt(LocalDateTime.of(2026, 7, 8, 10, 0));
        pageResult.setRecords(List.of(task));
        when(taskMapper.selectPage(any(), any())).thenReturn(pageResult);

        HistoryResponse resp = contractService.getHistory(userId, 1, 10);

        assertEquals(1, resp.getTasks().size());
        assertEquals(taskId, resp.getTasks().get(0).getTaskId());
        assertEquals("PENDING", resp.getTasks().get(0).getStatus());
        assertEquals(1, resp.getTotal());
    }

    // ==================== retry ====================

    @Test
    @DisplayName("重试成功（发送 MQ 消息）")
    void testRetrySuccess() {
        ReviewTask task = createPendingTask();
        task.setStatus("FAILED");
        task.setErrorMsg("LLM error");
        task.setCompletedAt(LocalDateTime.now());
        when(taskMapper.selectById(taskId)).thenReturn(task);

        contractService.retry(taskId, userId);

        assertEquals("PENDING", task.getStatus());
        assertEquals(0, task.getProgress());
        assertNull(task.getErrorMsg());
        assertNull(task.getCompletedAt());
        verify(taskMapper).updateById(task);
        verify(rabbitTemplate).convertAndSend(anyString(), anyString(), any(ReviewMessage.class));
    }

    @Test
    @DisplayName("重试时任务不存在")
    void testRetryTaskNotFound() {
        when(taskMapper.selectById(taskId)).thenReturn(null);

        assertThrows(BusinessException.class, () -> contractService.retry(taskId, userId));
    }

    @Test
    @DisplayName("重试时任务不是 FAILED 状态")
    void testRetryNotFailedState() {
        ReviewTask task = createPendingTask();
        task.setStatus("SUCCESS");
        when(taskMapper.selectById(taskId)).thenReturn(task);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> contractService.retry(taskId, userId));
        assertEquals(ErrorCode.INVALID_STATE.getCode(), ex.getCode());
    }
}
