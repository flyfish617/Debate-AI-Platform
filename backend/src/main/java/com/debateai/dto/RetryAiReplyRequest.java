package com.debateai.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 重试AI回复请求
 */
public record RetryAiReplyRequest(
        @NotNull(message = "用户消息 ID 不能为空")
        Long userMessageId
) {
}
