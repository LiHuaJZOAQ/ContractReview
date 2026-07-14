package com.contractreview.service.impl;

import com.contractreview.config.RabbitMqConfig;
import com.contractreview.domain.dto.ReviewMessage;
import com.contractreview.domain.entity.ReviewReport;
import com.contractreview.domain.entity.ReviewTask;
import com.contractreview.domain.entity.RiskItem;
import com.contractreview.domain.entity.User;
import com.contractreview.mapper.ReviewReportMapper;
import com.contractreview.mapper.ReviewTaskMapper;
import com.contractreview.mapper.RiskItemMapper;
import com.contractreview.mapper.UserMapper;
import com.contractreview.service.ReviewStateMachine;
import com.contractreview.service.SseService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReviewMessageListenerTest {

    @Mock private ReviewStateMachine stateMachine;
    @Mock private SseService sseService;
    @Mock private ReviewTaskMapper taskMapper;
    @Mock private ReviewReportMapper reportMapper;
    @Mock private RiskItemMapper riskItemMapper;
    @Mock private UserMapper userMapper;
    @Mock private RedisTemplate<String, Object> redisTemplate;
    @Mock private RabbitTemplate rabbitTemplate;
    @Mock private ValueOperations<String, Object> valueOps;

    private ObjectMapper objectMapper;
    private ReviewMessageListener listener;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);

        listener = new ReviewMessageListener(
                stateMachine, null, sseService, taskMapper,
                reportMapper, riskItemMapper, userMapper,
                objectMapper, redisTemplate, rabbitTemplate);
        ReflectionTestUtils.setField(listener, "maxRetryCount", 3);
    }

    // ==================== handleSuccess ====================

    @Test
    @DisplayName("handleSuccess: 正常保存审查结果")
    void testHandleSuccess() {
        ReviewTask task = new ReviewTask();
        task.setId(1L);
        task.setStatus("SUMMARIZING");
        when(taskMapper.selectById(1L)).thenReturn(task);
        when(taskMapper.selectById(1L)).thenReturn(task);

        Map<String, Object> result = new HashMap<>();
        result.put("summary", "审查完成");
        result.put("contractType", "租赁");
        result.put("userStance", "承租方");
        List<Map<String, Object>> risks = List.of(
                Map.of("clauseIndex", 1, "clauseContent", "条款1", "riskLevel", "HIGH",
                        "riskType", "违约金", "description", "风险", "suggestion", "修改",
                        "relatedLaws", List.of("民法典第585条"))
        );
        result.put("risks", risks);

        listener.handleSuccess(1L, result);

        verify(stateMachine).transition(1L, "SUMMARIZING", "SUCCESS");
        verify(riskItemMapper).insert(any(RiskItem.class));
        verify(reportMapper).insert(any(ReviewReport.class));
        verify(taskMapper).updateById(argThat((ReviewTask t) -> "租赁".equals(t.getContractType())));
        verify(sseService).sendComplete(1L, "1");
    }

    @Test
    @DisplayName("handleSuccess: 任务不存在时跳过")
    void testHandleSuccessTaskNotFound() {
        when(taskMapper.selectById(1L)).thenReturn(null);

        listener.handleSuccess(1L, Map.of());

        verify(stateMachine, never()).transition(any(), any(), any());
    }

    @Test
    @DisplayName("handleSuccess: 无 risks 时创建空报告")
    void testHandleSuccessNoRisks() {
        ReviewTask task = new ReviewTask();
        task.setId(1L);
        when(taskMapper.selectById(1L)).thenReturn(task);

        Map<String, Object> result = new HashMap<>();
        result.put("summary", "无风险");
        result.put("risks", List.of());

        listener.handleSuccess(1L, result);

        verify(reportMapper).insert(argThat((ReviewReport r) -> r.getRiskCountHigh() == 0));
    }

    // ==================== handleFailure ====================

    @Test
    @DisplayName("handleFailure: 标记失败并回滚配额")
    void testHandleFailure() {
        ReviewTask task = new ReviewTask();
        task.setId(1L);
        task.setUserId(10L);
        task.setStatus("REVIEWING");
        when(taskMapper.selectById(1L)).thenReturn(task).thenReturn(task);
        User user = new User();
        user.setId(10L);
        user.setReviewQuota(5);
        when(userMapper.selectById(10L)).thenReturn(user);

        listener.handleFailure(1L, 10L, new ReviewMessage(1L, 10L, 0), new RuntimeException("LLM API error"));

        verify(stateMachine).transition(1L, "REVIEWING", "FAILED");
        verify(taskMapper).updateById(argThat((ReviewTask t) -> t.getErrorMsg() != null));
        verify(redisTemplate.opsForValue()).increment("user:quota:10");
        verify(userMapper).updateById(argThat((User u) -> u.getReviewQuota() == 6));
        verify(sseService).sendError(eq(1L), contains("LLM API error"));
    }

    @Test
    @DisplayName("handleFailure: 任务已成功时跳过")
    void testHandleFailureAlreadySuccess() {
        ReviewTask task = new ReviewTask();
        task.setStatus("SUCCESS");
        when(taskMapper.selectById(1L)).thenReturn(task);

        listener.handleFailure(1L, 1L, new ReviewMessage(1L, 1L, 0), new RuntimeException("error"));

        verify(stateMachine, never()).transition(any(), any(), any());
    }

    // ==================== handleDlxMessage ====================

    @Test
    @DisplayName("DLX: 重试次数未超限时重新入队")
    void testDlxRetry() {
        ReviewMessage msg = new ReviewMessage(1L, 1L, 1);

        listener.handleDlxMessage(msg);

        verify(rabbitTemplate).convertAndSend(eq(RabbitMqConfig.EXCHANGE_REVIEW),
                eq(RabbitMqConfig.ROUTING_KEY), argThat((ReviewMessage m) -> m.getRetryCount() == 2));
    }

    @Test
    @DisplayName("DLX: 重试次数超限时标记 FAILED")
    void testDlxMaxRetriesExceeded() {
        ReviewTask task = new ReviewTask();
        task.setId(1L);
        task.setUserId(10L);
        task.setStatus("REVIEWING");
        User user = new User();
        user.setId(10L);
        user.setReviewQuota(3);
        when(taskMapper.selectById(1L)).thenReturn(task).thenReturn(task);
        when(userMapper.selectById(10L)).thenReturn(user);

        ReviewMessage msg = new ReviewMessage(1L, 10L, 3);
        listener.handleDlxMessage(msg);

        verify(stateMachine).transition(1L, "REVIEWING", "FAILED");
        verify(redisTemplate.opsForValue()).increment("user:quota:10");
        verify(sseService).sendError(eq(1L), contains("重试次数已达上限"));
    }

    // ==================== saveReviewResult (via handleSuccess) ====================

    @Test
    @DisplayName("风险计数从实际 risks 列表统计，不依赖 LLM 返回的 riskCount")
    void testRiskCountFromActualRisks() {
        ReviewTask task = new ReviewTask();
        task.setId(1L);
        when(taskMapper.selectById(1L)).thenReturn(task);

        Map<String, Object> result = new HashMap<>();
        result.put("summary", "test");
        List<Map<String, Object>> risks = List.of(
                Map.of("clauseIndex", 1, "riskLevel", "HIGH", "clauseContent", "c1", "riskType", "r1", "description", "d1", "suggestion", "s1"),
                Map.of("clauseIndex", 2, "riskLevel", "MEDIUM", "clauseContent", "c2", "riskType", "r2", "description", "d2", "suggestion", "s2"),
                Map.of("clauseIndex", 3, "riskLevel", "LOW", "clauseContent", "c3", "riskType", "r3", "description", "d3", "suggestion", "s3"),
                Map.of("clauseIndex", 4, "riskLevel", "MEDIUM", "clauseContent", "c4", "riskType", "r4", "description", "d4", "suggestion", "s4")
        );
        result.put("risks", risks);
        result.put("riskCount", Map.of("high", 99, "medium", 99, "low", 99));

        listener.handleSuccess(1L, result);

        verify(reportMapper).insert(argThat((ReviewReport r) ->
                r.getRiskCountHigh() == 1 &&
                r.getRiskCountMedium() == 2 &&
                r.getRiskCountLow() == 1
        ));
    }
}
