package com.debateai.service;

import com.debateai.config.AsyncQueueConfig;
import com.debateai.dto.LeaderboardRefreshMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 排行榜快照定时触发器
 */
@Component
public class LeaderboardRefreshScheduler {

    private static final Logger log = LoggerFactory.getLogger(LeaderboardRefreshScheduler.class);

    private final RabbitTemplate rabbitTemplate;

    public LeaderboardRefreshScheduler(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    /**
     * 每5分钟投递一次排行榜刷新任务
     */
    @Scheduled(fixedDelayString = "${app.leaderboard.refresh-interval-ms:300000}", initialDelayString = "${app.leaderboard.initial-delay-ms:10000}")
    public void scheduleRefresh() {
        LeaderboardRefreshMessage message = new LeaderboardRefreshMessage("scheduled", LocalDateTime.now());
        rabbitTemplate.convertAndSend(
                AsyncQueueConfig.LEADERBOARD_EXCHANGE,
                AsyncQueueConfig.LEADERBOARD_REFRESH_ROUTING_KEY,
                message
        );
        log.debug("已投递排行榜刷新任务");
    }
}
