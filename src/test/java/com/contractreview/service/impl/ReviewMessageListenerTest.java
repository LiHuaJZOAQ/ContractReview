package com.contractreview.service.impl;

import com.contractreview.config.RabbitMqConfig;
import com.contractreview.domain.dto.ReviewMessage;
import com.contractreview.domain.dto.SummarizeResult;
import com.contractreview.domain.entity.ReviewTask;
import com.contractreview.domain.enums.TaskStatus;
import com.contractreview.domain.entity.User;
import com.contractreview.mapper.ReviewTaskMapper;
import com.contractreview.mapper.UserMapper;
import com.contractreview.service.AgentOrchestrator;
import com.contractreview.service.ReviewStateMachine;
import com.contractreview.service.SseService;
import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReviewMessageListenerTest {

    @Mock private ReviewStateMachine stateMachine;
    @Mock private AgentOrchestrator agentOrchestrator;
    @Mock private SseService sseService;
    @Mock private ReviewTaskMapper taskMapper;
    @Mock private UserMapper userMapper;
    @Mock private RedisTemplate<String, Object> redisTemplate;
    @Mock private RabbitTemplate rabbitTemplate;
    @Mock private ReviewResultHandler reviewResultHandler;
    @Mock private Channel channel;
    @Mock private org.springframework.data.redis.core.ValueOperations<String, Object> valueOps;

    private ReviewMessageListener listener;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
        listener = new ReviewMessageListener(
                stateMachine, agentOrchestrator, sseService, taskMapper,
                userMapper, redisTemplate, rabbitTemplate, reviewResultHandler);
        ReflectionTestUtils.setField(listener, "maxRetryCount", 3);
    }

    private Message createAmqpMessage(long deliveryTag) {
        MessageProperties props = new MessageProperties();
        props.setDeliveryTag(deliveryTag);
        return new Message(new byte[0], props);
    }

    // ==================== handleReviewMessage ====================

    @Test
    @DisplayName("正常消费消息：状态转为PARSING，启动异步审查，手动ack")
    void testHandleReviewMessageSuccess() throws Exception {
        ReviewTask task = new ReviewTask();
        task.setId(1L);
        task.setPreviewText("合同内容");
        when(taskMapper.selectById(1L)).thenReturn(task);

        @SuppressWarnings("unchecked")
        CompletableFuture<SummarizeResult> future = CompletableFuture.completedFuture(new SummarizeResult());
        when(agentOrchestrator.executeReview(eq(1L), eq("合同内容"), eq(sseService))).thenReturn(future);

        ReviewMessage msg = new ReviewMessage(1L, 10L, 0);
        Message amqpMsg = createAmqpMessage(1L);

        listener.handleReviewMessage(msg, amqpMsg, channel);

        verify(stateMachine).transition(1L, TaskStatus.PENDING, TaskStatus.PARSING);
        verify(agentOrchestrator).executeReview(eq(1L), eq("合同内容"), eq(sseService));
        verify(channel).basicAck(1L, false);
    }

    @Test
    @DisplayName("任务不存在时：状态转FAILED，手动ack")
    void testHandleReviewMessageTaskNotFound() throws Exception {
        when(taskMapper.selectById(1L)).thenReturn(null);

        ReviewMessage msg = new ReviewMessage(1L, 10L, 0);
        Message amqpMsg = createAmqpMessage(2L);

        listener.handleReviewMessage(msg, amqpMsg, channel);

        verify(stateMachine).transition(1L, TaskStatus.PARSING, TaskStatus.FAILED);
        verify(sseService).sendError(1L, "任务不存在");
        verify(channel).basicAck(2L, false);
    }

    @Test
    @DisplayName("无效状态转换时：ack消息不重试")
    void testHandleReviewMessageInvalidState() throws Exception {
        doThrow(new com.contractreview.common.BusinessException(
                com.contractreview.domain.enums.ErrorCode.INVALID_STATE, "非法状态"))
                .when(stateMachine).transition(1L, TaskStatus.PENDING, TaskStatus.PARSING);

        ReviewMessage msg = new ReviewMessage(1L, 10L, 0);
        Message amqpMsg = createAmqpMessage(3L);

        listener.handleReviewMessage(msg, amqpMsg, channel);

        verify(channel).basicAck(3L, false);
        verify(channel, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
    }

    @Test
    @DisplayName("异步处理失败时：调用reviewResultHandler.handleFailure，nack消息")
    void testHandleReviewMessageAsyncFailure() throws Exception {
        ReviewTask task = new ReviewTask();
        task.setId(1L);
        task.setPreviewText("合同内容");
        when(taskMapper.selectById(1L)).thenReturn(task);

        @SuppressWarnings("unchecked")
        CompletableFuture<SummarizeResult> future = new CompletableFuture<>();
        future.completeExceptionally(new RuntimeException("LLM error"));
        when(agentOrchestrator.executeReview(eq(1L), eq("合同内容"), eq(sseService))).thenReturn(future);

        ReviewMessage msg = new ReviewMessage(1L, 10L, 0);
        Message amqpMsg = createAmqpMessage(4L);

        listener.handleReviewMessage(msg, amqpMsg, channel);

        verify(reviewResultHandler).handleFailure(eq(1L), eq(10L), eq(msg), any(RuntimeException.class));
        verify(channel).basicAck(4L, false);
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
    @DisplayName("DLX: 重试次数超限时标记 FAILED 并回滚配额")
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

        verify(stateMachine).transition(1L, TaskStatus.REVIEWING, TaskStatus.FAILED);
        verify(redisTemplate.opsForValue()).increment("user:quota:10");
        verify(sseService).sendError(eq(1L), contains("重试次数已达上限"));
    }

    @Test
    @DisplayName("DLX: 任务已处于终态时跳过")
    void testDlxTaskAlreadyTerminal() {
        ReviewTask task = new ReviewTask();
        task.setId(1L);
        task.setStatus("FAILED");
        when(taskMapper.selectById(1L)).thenReturn(task);

        ReviewMessage msg = new ReviewMessage(1L, 10L, 3);
        listener.handleDlxMessage(msg);

        verify(stateMachine, never()).transition(any(), any(), any());
    }
}
