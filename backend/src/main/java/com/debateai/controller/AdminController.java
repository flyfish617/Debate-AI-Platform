package com.debateai.controller;

import com.debateai.common.ApiResponse;
import com.debateai.dto.AdminReportHandleRequest;
import com.debateai.dto.AdminReportResponse;
import com.debateai.dto.AdminStatusRequest;
import com.debateai.dto.AdminUserResponse;
import com.debateai.dto.PageResponse;
import com.debateai.security.AuthUser;
import com.debateai.service.AdminService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * AdminController管理端功能相关接口
 */
@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    /**
     * 获取举报列表
     * @param page
     * @param size
     * @param status
     * @return 业务结果
     */
    @GetMapping("/reports")
    public ApiResponse<PageResponse<AdminReportResponse>> reports(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) String status
    ) {
        return ApiResponse.ok(adminService.listReports(page, size, status));
    }

    /**
     * 处理举报
     * @param id
     * @param request
     * @return 业务结果
     */
    @PostMapping("/reports/{id}/handle")
    public ApiResponse<AdminReportResponse> handleReport(
            @PathVariable Long id,
            @Valid @RequestBody AdminReportHandleRequest request
    ) {
        return ApiResponse.ok(adminService.handleReport(id, request));
    }

    /**
     * 获取用户列表
     * @param page
     * @param size
     * @param status
     * @return 业务结果
     */
    @GetMapping("/users")
    public ApiResponse<PageResponse<AdminUserResponse>> users(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) String status
    ) {
        return ApiResponse.ok(adminService.listUsers(page, size, status));
    }

    /**
     * 更新用户状态
     * @param id
     * @param request
     * @param authUser
     * @return 业务结果
     */
    @PatchMapping("/users/{id}/status")
    public ApiResponse<AdminUserResponse> updateUserStatus(
            @PathVariable Long id,
            @Valid @RequestBody AdminStatusRequest request,
            @AuthenticationPrincipal AuthUser authUser
    ) {
        return ApiResponse.ok(adminService.updateUserStatus(id, request, authUser.id()));
    }

    /**
     * 更新话题状态
     * @param id
     * @param request
     * @return 业务结果
     */
    @PatchMapping("/topics/{id}/status")
    public ApiResponse<AdminReportResponse> updateTopicStatus(
            @PathVariable Long id,
            @Valid @RequestBody AdminStatusRequest request
    ) {
        return ApiResponse.ok(adminService.updateTopicStatus(id, request));
    }

    /**
     * 删除话题
     * @param id
     * @return 业务结果
     */
    @PostMapping("/topics/{id}/delete")
    public ApiResponse<AdminReportResponse> deleteTopic(@PathVariable Long id) {
        return ApiResponse.ok(adminService.deleteTopic(id));
    }

    /**
     * 更新评论状态
     * @param id
     * @param request
     * @return 业务结果
     */
    @PatchMapping("/comments/{id}/status")
    public ApiResponse<AdminReportResponse> updateCommentStatus(
            @PathVariable Long id,
            @Valid @RequestBody AdminStatusRequest request
    ) {
        return ApiResponse.ok(adminService.updateCommentStatus(id, request));
    }

    /**
     * 隐藏辩论
     * @param id
     * @return 业务结果
     */
    @PostMapping("/debates/{id}/hide")
    public ApiResponse<AdminReportResponse> hideDebate(@PathVariable Long id) {
        return ApiResponse.ok(adminService.hideDebate(id));
    }
}
