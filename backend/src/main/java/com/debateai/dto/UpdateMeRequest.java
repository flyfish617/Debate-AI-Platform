package com.debateai.dto;

import jakarta.validation.constraints.Size;

/**
 * 更新当前用户请求
 */
public record UpdateMeRequest(
        @Size(max = 500, message = "头像 URL 不能超过 500 字")
        String avatarUrl,

        @Size(max = 200, message = "简介不能超过 200 字")
        String bio
) {
}
