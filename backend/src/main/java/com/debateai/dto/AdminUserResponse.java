package com.debateai.dto;

import com.debateai.entity.User;

import java.time.LocalDateTime;

/**
 * 管理员用户列表响应
 */
public record AdminUserResponse(
        Long id,
        String username,
        String email,
        String role,
        String status,
        Integer points,
        Integer wins,
        Integer losses,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static AdminUserResponse from(User user) {
        return new AdminUserResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getRole(),
                user.getStatus(),
                user.getPoints(),
                user.getWins(),
                user.getLosses(),
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }
}
