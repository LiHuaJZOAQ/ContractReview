package com.contractreview.service.impl;

import com.contractreview.config.RabbitMqConfig;
import com.contractreview.domain.dto.ReviewMessage;
import com.contractreview.domain.entity.ReviewTask;
import com.contractreview.domain.enums.TaskStatus;
import com.contractreview.mapper.ReviewTaskMapper;
import com.contractreview.mapper.UserMapper;
import com.contractreview.service.AgentOrchestrator;
import com.contractreview.service.ReviewMessageListener;
import com.contractreview.service.ReviewResultHandler;
import com.contractreview.service.ReviewStateMachine;
import com.contractreview.service.SseService;
import com.contractreview.common.BusinessException;
import com.contractreview.util.LogTruncator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReviewMessageListenerImpl implements ReviewMessageListener {

    private final ReviewStateMachine stateMachine;
    private final AgentOrchestrator agentOrchestrator;
    private final SseService sseService;
    private final ReviewTaskMapper taskMapper;
    private final UserMapper userMapper;
    private final RedisTemplate<String, Object> redisTemplate;
    private final RabbitTemplate rabbitTemplate;
    private final ReviewResultHandler reviewResultHandler;

    @Value("${contract.review.max-retry-count:3}")
    private int maxRetryCount;

    @Override
    @RabbitListener(queues = "${contract.review.queue.name:contract.review.queue}",
            ackMode = "MANUAL")
    public void handleReviewMessage(ReviewMessage message, Message amqpMessage,
                                    com.rabbitmq.client.Channel channel) throws Exception {
        long deliveryTag = amqpMessage.getMessageProperties().getDeliveryTag();
        Long taskId = message.getTaskId();
        Long userId = message.getUserId();
        log.info("MQ consumer received review message: taskId={}, userId={}, retryCount={}",
                taskId, userId, message.getRetryCount());

        try {
            stateMachine.transition(taskId, TaskStatus.PENDING, TaskStatus.PARSING);

            ReviewTask task = taskMapper.selectById(taskId);
            if (task == null) {
                log.error("Task not found: {}", taskId);
                stateMachine.transition(taskId, TaskStatus.PARSING, TaskStatus.FAILED);
                sseService.sendError(taskId, "任务不存在");
                channel.basicAck(deliveryTag, false);
                return;
            }

            String fullText = task.getPreviewText();
            Long finalUserId = userId;
            agentOrchestrator.executeReview(taskId, fullText, sseService)
                    .whenComplete((result, throwable) -> {
                        try {
                            if (throwable != null) {
                                reviewResultHandler.handleFailure(taskId, finalUserId, message, throwable);
                            } else {
                                reviewResultHandler.handleSuccess(taskId, result);
                            }
                            channel.basicAck(deliveryTag, false);
                        } catch (Exception e) {
                            log.error("Failed to ack/nack message for task {}: {}", taskId, LogTruncator.truncate(e.getMessage(), 200));
                            try {
                                channel.basicNack(deliveryTag, false, false);
                            } catch (Exception ex) {
                                log.error("Failed to nack message for task {}", taskId, ex);
                            }
                        }
                    });

        } catch (BusinessException e) {
            log.warn("Invalid state transition for task {}: {}", taskId, e.getMessage());
            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            log.error("Failed to start review for task {}: {}", taskId, LogTruncator.truncate(e.getMessage(), 200));
            reviewResultHandler.handleFailure(taskId, userId, message, e);
            channel.basicNack(deliveryTag, false, false);
        }
    }

    @Override
    @RabbitListener(queues = "${contract.review.dlx.name:contract.review.dlx}")
    public void handleDlxMessage(ReviewMessage message) {
        Long taskId = message.getTaskId();
        log.info("DLX consumer received: taskId={}, retryCount={}", taskId, message.getRetryCount());

        if (message.getRetryCount() < maxRetryCount) {
            message.setRetryCount(message.getRetryCount() + 1);
            rabbitTemplate.convertAndSend(
                    RabbitMqConfig.EXCHANGE_REVIEW,
                    RabbitMqConfig.ROUTING_KEY,
                    message);
            log.info("Re-queued task {} for retry {}/{}", taskId, message.getRetryCount(), maxRetryCount);
        } else {
            try {
                ReviewTask task = taskMapper.selectById(taskId);
                if (task == null || TaskStatus.FAILED.name().equals(task.getStatus())
                        || TaskStatus.SUCCESS.name().equals(task.getStatus())) {
                    log.warn("Task {} already in terminal state, skipping DLX", taskId);
                    return;
                }

                stateMachine.transition(taskId, TaskStatus.valueOf(task.getStatus()), TaskStatus.FAILED);
                ReviewTask failedTask = taskMapper.selectById(taskId);
                failedTask.setErrorMsg("重试次数已达上限 (" + maxRetryCount + " 次)");
                taskMapper.updateById(failedTask);

                String quotaKey = "user:quota:" + task.getUserId();
                redisTemplate.opsForValue().increment(quotaKey);
                com.contractreview.domain.entity.User user = userMapper.selectById(task.getUserId());
                if (user != null) {
                    user.setReviewQuota(user.getReviewQuota() + 1);
                    userMapper.updateById(user);
                }

                sseService.sendError(taskId, "审查失败，重试次数已达上限");
                log.warn("Task {} failed after {} retries", taskId, maxRetryCount);
            } catch (Exception e) {
                log.error("Failed to mark task {} as FAILED: {}", taskId, LogTruncator.truncate(e.getMessage(), 200));
            }
        }
    }
}
