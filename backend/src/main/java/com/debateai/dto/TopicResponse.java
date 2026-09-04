package com.debateai.dto;

import com.debateai.entity.Topic;

import java.time.LocalDateTime;

/**
 * 话题响应
 */
public record TopicResponse(
        Long id,
        String title,
        String description,
        String category,
        Long creatorId,
        String status,
        Integer debateCount,
        Integer viewCount,
        LocalDateTime createdAt
) {

    public static TopicResponse from(Topic topic) {
        return new TopicResponse(
                topic.getId(),
                topic.getTitle(),
                topic.getDescription(),
                categoryLabel(topic.getCategory()),
                topic.getCreatorId(),
                topic.getStatus(),
                topic.getDebateCount(),
                topic.getViewCount(),
                topic.getCreatedAt()
        );
    }

    private static String categoryLabel(String category) {
        return switch (category) {
            case "tech" -> "科技";
            case "society" -> "社会";
            case "philosophy" -> "哲学";
            case "education" -> "教育";
            case "entertainment" -> "娱乐";
            case "other" -> "其他";
            default -> category;
        };
    }
}
