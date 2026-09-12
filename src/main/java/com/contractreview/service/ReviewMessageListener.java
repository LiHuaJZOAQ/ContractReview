package com.contractreview.service;

import com.contractreview.domain.dto.ReviewMessage;
import com.rabbitmq.client.Channel;
import org.springframework.amqp.core.Message;

import java.io.IOException;

public interface ReviewMessageListener {
    void handleReviewMessage(ReviewMessage message, Message amqpMessage, Channel channel) throws Exception;
    void handleDlxMessage(ReviewMessage message);
}
