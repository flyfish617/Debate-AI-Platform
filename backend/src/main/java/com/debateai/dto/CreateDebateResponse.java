package com.debateai.dto;

/**
 * 创建辩论响应
 */
public record CreateDebateResponse(
        Long debateId,
        Long topicId,
        String userStance,
        String aiStance,
        String style,
        String visibility,
        Integer maxRounds,
        MessageResponse aiFirstMessage
) {
}
