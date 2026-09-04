package com.debateai.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 管理员处理举报请求
 */
public record AdminReportHandleRequest(
        @NotBlank(message = "处理动作不能为空")
        String action
) {
}
