package com.debateai.dto;

/**
 * AI结辩生成消息
 */
public record DebateClosingMessage(
        Long debateId,
        Long userId,
        String trigger
) {
}
