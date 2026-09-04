package com.debateai.dto;

import com.debateai.entity.User;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 公开用户主页响应
 */
public record PublicUserProfileResponse(
        Long id,
        String username,
        String avatarUrl,
        String bio,
        String role,
        String status,
        Integer points,
        Integer wins,
        Integer losses,
        LocalDateTime createdAt,
        Long createdTopicCount,
        Long publicDebateCount,
        Long endedPublicDebateCount,
        Long commentCount,
        List<TopicResponse> recentTopics,
        List<DebateSummaryResponse> recentDebates
) {

    public static PublicUserProfileResponse from(
            User user,
            Long createdTopicCount,
            Long publicDebateCount,
            Long endedPublicDebateCount,
            Long commentCount,
            List<TopicResponse> recentTopics,
            List<DebateSummaryResponse> recentDebates
    ) {
        return new PublicUserProfileResponse(
                user.getId(),
                user.getUsername(),
                user.getAvatarUrl(),
                user.getBio(),
                user.getRole(),
                user.getStatus(),
                user.getPoints(),
                user.getWins(),
                user.getLosses(),
                user.getCreatedAt(),
                createdTopicCount,
                publicDebateCount,
                endedPublicDebateCount,
                commentCount,
                recentTopics,
                recentDebates
        );
    }
}
