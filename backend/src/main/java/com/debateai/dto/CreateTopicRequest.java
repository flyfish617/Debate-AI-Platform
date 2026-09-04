package com.debateai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 创建话题请求
 */
public record CreateTopicRequest(
        @NotBlank(message = "标题不能为空")
        @Size(max = 100, message = "标题不能超过 100 字")
        String title,

        @Size(max = 2000, message = "描述不能超过 2000 字")
        String description,

        @NotBlank(message = "分类不能为空")
        @Pattern(regexp = "科技|社会|哲学|教育|娱乐|其他|tech|society|philosophy|education|entertainment|other", message = "分类不合法")
        String category
) {
}
