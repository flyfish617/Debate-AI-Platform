package com.debateai.controller;

import com.debateai.common.ApiResponse;
import com.debateai.dto.CreateReportRequest;
import com.debateai.dto.ReportResponse;
import com.debateai.security.AuthUser;
import com.debateai.service.ReportService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * ReportController举报功能相关接口
 */
@RestController
@RequestMapping("/api/report")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    /**
     * 创建数据
     * @param authUser
     * @param request
     * @return 业务结果
     */
    @PostMapping
    public ApiResponse<ReportResponse> create(
            @AuthenticationPrincipal AuthUser authUser,
            @Valid @RequestBody CreateReportRequest request
    ) {
        return ApiResponse.ok(reportService.create(request, authUser.id()));
    }
}
