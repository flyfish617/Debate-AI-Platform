package com.debateai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 创建举报请求
 */
public record CreateReportRequest(
        @NotBlank(message = "举报类型不能为空")
        String targetType,

        @NotNull(message = "举报对象不能为空")
        Long targetId,

        @NotBlank(message = "举报理由不能为空")
        @Size(max = 200, message = "举报理由不能超过 200 字")
        String reason
) {
}
