package com.debateai.common;

/**
 * 统一接口响应
 */
public record ApiResponse<T>(boolean success, T data, ApiError error) {

    /**
     * 构造成功响应
     * @param data
     * @return 业务结果
     */
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null);
    }

    /**
     * 构造失败响应
     * @param code
     * @param message
     * @return 业务结果
     */
    public static <T> ApiResponse<T> fail(String code, String message) {
        return new ApiResponse<>(false, null, new ApiError(code, message, null));
    }
}
