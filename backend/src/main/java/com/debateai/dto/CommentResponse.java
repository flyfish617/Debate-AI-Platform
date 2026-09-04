package com.debateai.dto;

import com.debateai.entity.Comment;
import com.debateai.entity.User;

import java.time.LocalDateTime;

/**
 * 评论响应
 */
public record CommentResponse(
        Long id,
        Long debateId,
        Long userId,
        String username,
        String content,
        String status,
        LocalDateTime createdAt
) {

    public static CommentResponse from(Comment comment, User user) {
        return new CommentResponse(
                comment.getId(),
                comment.getDebateId(),
                comment.getUserId(),
                user == null ? "未知用户" : user.getUsername(),
                comment.getContent(),
                comment.getStatus(),
                comment.getCreatedAt()
        );
    }
}
