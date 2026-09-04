package com.debateai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 发送辩论消息请求
 */
public record SendMessageRequest(
        @NotBlank(message = "发言不能为空")
        @Size(max = 2000, message = "发言不能超过 2000 字")
        String content
) {
}
