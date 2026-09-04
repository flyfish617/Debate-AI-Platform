package com.debateai.dto;

import com.debateai.entity.Message;

import java.time.LocalDateTime;

/**
 * 辩论消息响应
 */
public record MessageResponse(
        Long id,
        Long debateId,
        Long userId,
        String role,
        String content,
        Integer round,
        String aiProvider,
        String aiModel,
        Integer latencyMs,
        String status,
        LocalDateTime createdAt
) {

    public static MessageResponse from(Message message) {
        return new MessageResponse(
                message.getId(),
                message.getDebateId(),
                message.getUserId(),
                message.getRole(),
                message.getContent(),
                message.getRound(),
                message.getAiProvider(),
                message.getAiModel(),
                message.getLatencyMs(),
                message.getStatus(),
                message.getCreatedAt()
        );
    }
}
