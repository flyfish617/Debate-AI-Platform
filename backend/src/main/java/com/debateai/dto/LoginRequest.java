package com.debateai.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 登录请求
 */
public record LoginRequest(
        @NotBlank(message = "账号不能为空")
        String account,

        @NotBlank(message = "密码不能为空")
        String password
) {
}
