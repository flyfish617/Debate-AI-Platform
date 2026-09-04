package com.debateai.dto;

import com.debateai.entity.User;

import java.time.LocalDateTime;

/**
 * 排行榜条目响应
 */
public record LeaderboardEntryResponse(
        long rank,
        Long userId,
        String username,
        String avatarUrl,
        String bio,
        Integer points,
        Integer wins,
        Integer losses,
        double winRate,
        long publicDebateCount,
        long endedPublicDebateCount,
        LocalDateTime createdAt
) {

    public static LeaderboardEntryResponse from(
            long rank,
            User user,
            long publicDebateCount,
            long endedPublicDebateCount
    ) {
        int wins = user.getWins() == null ? 0 : user.getWins();
        int losses = user.getLosses() == null ? 0 : user.getLosses();
        int totalDecided = wins + losses;
        double winRate = totalDecided == 0 ? 0 : Math.round((wins * 10000.0 / totalDecided)) / 100.0;
        return new LeaderboardEntryResponse(
                rank,
                user.getId(),
                user.getUsername(),
                user.getAvatarUrl(),
                user.getBio(),
                user.getPoints(),
                wins,
                losses,
                winRate,
                publicDebateCount,
                endedPublicDebateCount,
                user.getCreatedAt()
        );
    }
}
