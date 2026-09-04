package com.debateai.dto;

import java.time.LocalDateTime;

/**
 * 排行榜刷新消息
 */
public record LeaderboardRefreshMessage(
        String trigger,
        LocalDateTime requestedAt
) {
}
