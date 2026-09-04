package com.debateai.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.debateai.common.ApiException;
import com.debateai.dto.NotificationResponse;
import com.debateai.dto.PageResponse;
import com.debateai.dto.UnreadNotificationCountResponse;
import com.debateai.entity.Notification;
import com.debateai.entity.User;
import com.debateai.mapper.NotificationMapper;
import com.debateai.mapper.UserMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 通知业务服务
 */
@Service
public class NotificationService {

    private static final long MAX_PAGE_SIZE = 50;

    private final NotificationMapper notificationMapper;
    private final UserMapper userMapper;

    public NotificationService(NotificationMapper notificationMapper, UserMapper userMapper) {
        this.notificationMapper = notificationMapper;
        this.userMapper = userMapper;
    }

    /**
     * 获取通知列表
     * @param userId 用户ID
     * @param page 页码
     * @param size 每页数量
     * @param status 通知状态
     * @return 分页通知
     */
    public PageResponse<NotificationResponse> list(Long userId, long page, long size, String status) {
        long normalizedPage = Math.max(page, 1);
        long normalizedSize = Math.max(1, Math.min(size, MAX_PAGE_SIZE));
        long offset = (normalizedPage - 1) * normalizedSize;

        LambdaQueryWrapper<Notification> wrapper = baseUserNotifications(userId)
                .orderByDesc(Notification::getCreatedAt)
                .orderByDesc(Notification::getId);
        if (status != null && !status.isBlank()) {
            wrapper.eq(Notification::getStatus, normalizeStatus(status));
        }

        long total = notificationMapper.selectCount(wrapper);
        List<NotificationResponse> list = notificationMapper.selectList(wrapper.last("LIMIT " + offset + ", " + normalizedSize))
                .stream()
                .map(NotificationResponse::from)
                .toList();
        return new PageResponse<>(list, total, normalizedPage, normalizedSize);
    }

    /**
     * 获取未读通知数量
     * @param userId 用户ID
     * @return 未读数量
     */
    public UnreadNotificationCountResponse unreadCount(Long userId) {
        long count = notificationMapper.selectCount(baseUserNotifications(userId)
                .eq(Notification::getStatus, "unread"));
        return new UnreadNotificationCountResponse(count);
    }

    /**
     * 标记单条通知已读
     * @param notificationId 通知ID
     * @param userId 用户ID
     * @return 更新后的通知
     */
    @Transactional
    public NotificationResponse markRead(Long notificationId, Long userId) {
        Notification notification = notificationMapper.selectById(notificationId);
        if (notification == null || notification.getDeletedAt() != null || !notification.getUserId().equals(userId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "通知不存在");
        }
        markRead(notification);
        notificationMapper.updateById(notification);
        return NotificationResponse.from(notification);
    }

    /**
     * 标记所有通知已读
     * @param userId 用户ID
     * @return 未读数量
     */
    @Transactional
    public UnreadNotificationCountResponse markAllRead(Long userId) {
        List<Notification> unread = notificationMapper.selectList(baseUserNotifications(userId)
                .eq(Notification::getStatus, "unread"));
        unread.forEach(notification -> {
            markRead(notification);
            notificationMapper.updateById(notification);
        });
        return unreadCount(userId);
    }

    /**
     * 同步写入用户通知
     * @param userId 用户ID
     * @param type 通知类型
     * @param title 标题
     * @param content 内容
     * @param linkUrl 跳转链接
     */
    @Transactional
    public void notifyUser(Long userId, String type, String title, String content, String linkUrl) {
        insertForUser(userId, type, title, content, linkUrl);
    }

    /**
     * 同步写入所有管理员通知
     * @param title 标题
     * @param content 内容
     * @param linkUrl 跳转链接
     */
    @Transactional
    public void notifyAdmins(String title, String content, String linkUrl) {
        List<User> admins = userMapper.selectList(new LambdaQueryWrapper<User>()
                .eq(User::getRole, "admin")
                .eq(User::getStatus, "active")
                .isNull(User::getDeletedAt));
        admins.forEach(admin -> insertForUser(admin.getId(), "report_created", title, content, linkUrl));
    }

    private LambdaQueryWrapper<Notification> baseUserNotifications(Long userId) {
        return new LambdaQueryWrapper<Notification>()
                .eq(Notification::getUserId, userId)
                .isNull(Notification::getDeletedAt);
    }

    private void markRead(Notification notification) {
        if (!"read".equals(notification.getStatus())) {
            notification.setStatus("read");
            notification.setReadAt(LocalDateTime.now());
        }
    }

    private void insertForUser(Long userId, String type, String title, String content, String linkUrl) {
        User user = userMapper.selectById(userId);
        if (user == null || user.getDeletedAt() != null || !"active".equals(user.getStatus())) {
            return;
        }

        Notification notification = new Notification();
        notification.setUserId(userId);
        notification.setType(normalizeType(type));
        notification.setTitle(truncate(title, 80));
        notification.setContent(truncate(content, 500));
        notification.setLinkUrl(truncate(linkUrl, 255));
        notification.setStatus("unread");
        notificationMapper.insert(notification);
    }

    private String normalizeStatus(String status) {
        return switch (status.trim()) {
            case "未读", "unread" -> "unread";
            case "已读", "read" -> "read";
            default -> throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "通知状态只能是 unread 或 read");
        };
    }

    private String normalizeType(String type) {
        return switch (type == null ? "" : type.trim()) {
            case "comment" -> "comment";
            case "vote" -> "vote";
            case "report_created" -> "report_created";
            case "report_handled" -> "report_handled";
            default -> "system";
        };
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= maxLength ? trimmed : trimmed.substring(0, maxLength);
    }
}
