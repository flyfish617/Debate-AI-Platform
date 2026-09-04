package com.debateai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * 投票请求
 */
public record VoteRequest(
        @NotNull(message = "辩论不能为空")
        Long debateId,

        @NotBlank(message = "投票对象不能为空")
        @Pattern(regexp = "user|ai", message = "投票对象不合法")
        String votedFor
) {
}
