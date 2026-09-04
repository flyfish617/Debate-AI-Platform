package com.debateai.dto;

import com.debateai.entity.Debate;

import java.time.LocalDateTime;

/**
 * 辩论摘要响应
 */
public record DebateSummaryResponse(
        Long id,
        TopicResponse topic,
        Long userId,
        String userStance,
        String aiStance,
        String aiModel,
        String style,
        String visibility,
        String status,
        Integer currentRound,
        Integer maxRounds,
        String winner,
        Integer userVoteCount,
        Integer aiVoteCount,
        LocalDateTime createdAt,
        LocalDateTime endedAt
) {

    public static DebateSummaryResponse from(Debate debate, TopicResponse topic) {
        return new DebateSummaryResponse(
                debate.getId(),
                topic,
                debate.getUserId(),
                debate.getUserStance(),
                debate.getAiStance(),
                debate.getAiModel(),
                debate.getStyle(),
                debate.getVisibility(),
                debate.getStatus(),
                debate.getCurrentRound(),
                debate.getMaxRounds(),
                debate.getWinner(),
                debate.getUserVoteCount(),
                debate.getAiVoteCount(),
                debate.getCreatedAt(),
                debate.getEndedAt()
        );
    }
}
