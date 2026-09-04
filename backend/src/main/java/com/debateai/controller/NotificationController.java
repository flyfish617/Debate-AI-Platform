package com.debateai.controller;

import com.debateai.common.ApiResponse;
import com.debateai.dto.NotificationResponse;
import com.debateai.dto.PageResponse;
import com.debateai.dto.UnreadNotificationCountResponse;
import com.debateai.security.AuthUser;
import com.debateai.service.NotificationService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * NotificationController通知功能相关接口
 */
@RestController
@RequestMapping("/api/notification")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    /**
     * 获取列表
     * @param authUser
     * @param page
     * @param size
     * @param status
     * @return 业务结果
     */
    @GetMapping
    public ApiResponse<PageResponse<NotificationResponse>> list(
            @AuthenticationPrincipal AuthUser authUser,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) String status
    ) {
        return ApiResponse.ok(notificationService.list(authUser.id(), page, size, status));
    }

    /**
     * 获取未读通知数量
     * @param authUser
     * @return 业务结果
     */
    @GetMapping("/unread-count")
    public ApiResponse<UnreadNotificationCountResponse> unreadCount(@AuthenticationPrincipal AuthUser authUser) {
        return ApiResponse.ok(notificationService.unreadCount(authUser.id()));
    }

    /**
     * 标记通知已读
     * @param authUser
     * @param id
     * @return 业务结果
     */
    @PostMapping("/{id}/read")
    public ApiResponse<NotificationResponse> markRead(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long id
    ) {
        return ApiResponse.ok(notificationService.markRead(id, authUser.id()));
    }

    /**
     * 全部标记已读
     * @param authUser
     * @return 业务结果
     */
    @PostMapping("/read-all")
    public ApiResponse<UnreadNotificationCountResponse> markAllRead(@AuthenticationPrincipal AuthUser authUser) {
        return ApiResponse.ok(notificationService.markAllRead(authUser.id()));
    }
}
