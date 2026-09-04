package com.debateai.common;

import org.springframework.http.HttpStatus;

/**
 * 接口业务异常
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public ApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    /**
     * 获取HTTP状态码
     * @return 业务结果
     */
    public HttpStatus getStatus() {
        return status;
    }

    /**
     * 获取业务错误码
     * @return 业务结果
     */
    public String getCode() {
        return code;
    }
}
