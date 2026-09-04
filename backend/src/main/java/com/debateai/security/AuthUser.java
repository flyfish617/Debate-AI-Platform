package com.debateai.security;

/**
 * 认证用户上下文
 */
public record AuthUser(Long id, String username, String role, String jti) {
}
