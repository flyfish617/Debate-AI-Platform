package com.debateai.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 管理员状态变更请求
 */
public record AdminStatusRequest(
        @NotBlank(message = "状态不能为空")
        String status
) {
}
