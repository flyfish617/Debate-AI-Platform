package com.debateai.dto;

import com.debateai.entity.Debate;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 辩论详情响应
 */
public record DebateDetailResponse(
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
        String viewerVote,
        List<MessageResponse> messages,
        LocalDateTime createdAt,
        LocalDateTime endedAt
) {

    public static DebateDetailResponse from(Debate debate, TopicResponse topic, List<MessageResponse> messages) {
        return from(debate, topic, messages, null);
    }

    public static DebateDetailResponse from(Debate debate, TopicResponse topic, List<MessageResponse> messages, String viewerVote) {
        return new DebateDetailResponse(
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
                viewerVote,
                messages,
                debate.getCreatedAt(),
                debate.getEndedAt()
        );
    }
}
