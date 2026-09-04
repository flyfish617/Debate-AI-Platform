package com.debateai.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

/**
 * 创建辩论请求
 */
public record CreateDebateRequest(
        @NotNull(message = "话题不能为空")
        Long topicId,

        @Pattern(regexp = "pro|con|正方|反方", message = "用户立场不合法")
        String userStance,

        @Pattern(regexp = "mild|intense|温和|激烈", message = "辩论风格不合法")
        String style,

        @Pattern(regexp = "public|private|公开|私密", message = "可见性不合法")
        String visibility,

        @Positive(message = "最大轮次必须大于0")
        Integer maxRounds
) {
}
