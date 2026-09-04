package com.debateai.dto;

import java.time.LocalDateTime;

/**
 * 结束辩论响应
 */
public record EndDebateResponse(
        Long debateId,
        String status,
        String winner,
        MessageResponse aiClosingMessage,
        LocalDateTime endedAt
) {
}
