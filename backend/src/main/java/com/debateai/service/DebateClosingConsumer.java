package com.debateai.service;

import com.debateai.config.AsyncQueueConfig;
import com.debateai.dto.DebateClosingMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * AI结辩消息消费者
 */
@Component
public class DebateClosingConsumer {

    private static final Logger log = LoggerFactory.getLogger(DebateClosingConsumer.class);

    private final DebateService debateService;

    public DebateClosingConsumer(DebateService debateService) {
        this.debateService = debateService;
    }

    /**
     * 消费AI结辩生成任务
     * @param message 结辩消息
     */
    @RabbitListener(queues = AsyncQueueConfig.DEBATE_CLOSING_QUEUE)
    public void handle(DebateClosingMessage message) {
        log.info("收到AI结辩生成任务，debateId={}, trigger={}", message.debateId(), message.trigger());
        debateService.generateClosingStatement(message.debateId(), message.trigger());
    }
}
