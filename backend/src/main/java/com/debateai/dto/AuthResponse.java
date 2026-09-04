package com.debateai.dto;

/**
 * 认证成功响应
 */
public record AuthResponse(String token, long expiresIn, UserProfileResponse user) {
}
