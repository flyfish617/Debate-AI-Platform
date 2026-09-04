package com.debateai.dto;

import com.debateai.entity.Notification;

import java.time.LocalDateTime;

/**
 * 通知响应
 */
public record NotificationResponse(
        Long id,
        String type,
        String title,
        String content,
        String linkUrl,
        String status,
        LocalDateTime createdAt,
        LocalDateTime readAt
) {

    public static NotificationResponse from(Notification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getType(),
                notification.getTitle(),
                notification.getContent(),
                notification.getLinkUrl(),
                notification.getStatus(),
                notification.getCreatedAt(),
                notification.getReadAt()
        );
    }
}
