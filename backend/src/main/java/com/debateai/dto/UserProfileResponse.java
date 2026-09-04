package com.debateai.dto;

import com.debateai.entity.User;

/**
 * 用户资料响应
 */
public record UserProfileResponse(
        Long id,
        String username,
        String email,
        String avatarUrl,
        String bio,
        String role,
        String status,
        Integer points,
        Integer wins,
        Integer losses
) {

    public static UserProfileResponse from(User user) {
        return new UserProfileResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getAvatarUrl(),
                user.getBio(),
                user.getRole(),
                user.getStatus(),
                user.getPoints(),
                user.getWins(),
                user.getLosses()
        );
    }
}
