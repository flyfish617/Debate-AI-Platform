package com.debateai.common;

/**
 * 接口错误信息
 */
public record ApiError(String code, String message, Object details) {
}
