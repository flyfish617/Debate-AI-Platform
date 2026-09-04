package com.debateai.service;

import com.debateai.config.AsyncQueueConfig;
import com.debateai.dto.LeaderboardRefreshMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * 排行榜刷新消息消费者
 */
@Component
public class LeaderboardRefreshConsumer {

    private static final Logger log = LoggerFactory.getLogger(LeaderboardRefreshConsumer.class);

    private final LeaderboardService leaderboardService;

    public LeaderboardRefreshConsumer(LeaderboardService leaderboardService) {
        this.leaderboardService = leaderboardService;
    }

    /**
     * 消费排行榜快照刷新任务
     * @param message 刷新消息
     */
    @RabbitListener(queues = AsyncQueueConfig.LEADERBOARD_REFRESH_QUEUE)
    public void handle(LeaderboardRefreshMessage message) {
        log.info("收到排行榜刷新任务，trigger={}, requestedAt={}", message.trigger(), message.requestedAt());
        leaderboardService.refreshSnapshots(message.trigger());
    }
}
