package com.debateai.controller;

import com.debateai.common.ApiResponse;
import com.debateai.dto.HealthResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * HealthController健康检查相关接口
 */
@RestController
@RequestMapping("/api/health")
public class HealthController {

    /**
     * 健康检查
     * @return 业务结果
     */
    @GetMapping
    public ApiResponse<HealthResponse> health() {
        return ApiResponse.ok(new HealthResponse("ok", "debate-ai-backend"));
    }
}
