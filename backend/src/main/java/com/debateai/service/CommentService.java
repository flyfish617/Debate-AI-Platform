package com.debateai.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.debateai.common.ApiException;
import com.debateai.dto.CommentResponse;
import com.debateai.dto.CreateCommentRequest;
import com.debateai.dto.PageResponse;
import com.debateai.entity.Comment;
import com.debateai.entity.Debate;
import com.debateai.entity.User;
import com.debateai.mapper.CommentMapper;
import com.debateai.mapper.DebateMapper;
import com.debateai.mapper.UserMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 评论业务服务
 */
@Service
public class CommentService {

    private static final long MAX_PAGE_SIZE = 50;

    private final CommentMapper commentMapper;
    private final DebateMapper debateMapper;
    private final UserMapper userMapper;
    private final NotificationService notificationService;

    public CommentService(
            CommentMapper commentMapper,
            DebateMapper debateMapper,
            UserMapper userMapper,
            NotificationService notificationService
    ) {
        this.commentMapper = commentMapper;
        this.debateMapper = debateMapper;
        this.userMapper = userMapper;
        this.notificationService = notificationService;
    }

    /**
     * 获取列表
     * @param debateId
     * @param viewerId
     * @param page
     * @param size
     * @return 业务结果
     */
    public PageResponse<CommentResponse> list(Long debateId, Long viewerId, long page, long size) {
        //私密辩论仅创建者可读评论
        Debate debate = requireReadableDebate(debateId, viewerId);
        long normalizedPage = Math.max(page, 1);
        long normalizedSize = Math.max(1, Math.min(size, MAX_PAGE_SIZE));
        long offset = (normalizedPage - 1) * normalizedSize;

        LambdaQueryWrapper<Comment> wrapper = visibleComments(debate.getId())
                .orderByDesc(Comment::getCreatedAt)
                .orderByDesc(Comment::getId);

        long total = commentMapper.selectCount(wrapper);
        List<CommentResponse> list = commentMapper.selectList(wrapper.last("LIMIT " + offset + ", " + normalizedSize)).stream()
                .map(comment -> CommentResponse.from(comment, userMapper.selectById(comment.getUserId())))
                .toList();

        return new PageResponse<>(list, total, normalizedPage, normalizedSize);
    }

    /**
     * 创建数据
     * @param debateId
     * @param userId
     * @param request
     * @return 业务结果
     */
    @Transactional
    public CommentResponse create(Long debateId, Long userId, CreateCommentRequest request) {
        //辩论是否可读
        Debate debate = requireReadableDebate(debateId, userId);
        if (debate.getUserId().equals(userId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "辩论者不能发表场外评论");
        }

        String content = request.content().trim();
        if (content.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "评论不能为空");
        }

        Comment comment = new Comment();
        comment.setDebateId(debate.getId());
        comment.setUserId(userId);
        comment.setContent(content);
        comment.setStatus("visible");
        commentMapper.insert(comment);

        User commenter = userMapper.selectById(userId);
        notificationService.notifyUser(
                debate.getUserId(),
                "comment",
                "你的辩论收到新评论",
                (commenter == null ? "有用户" : commenter.getUsername()) + " 评论了你的辩论：" + preview(content),
                "/debate/" + debate.getId()
        );

        return CommentResponse.from(comment, userMapper.selectById(userId));
    }

    private LambdaQueryWrapper<Comment> visibleComments(Long debateId) {
        return new LambdaQueryWrapper<Comment>()
                .eq(Comment::getDebateId, debateId)
                .eq(Comment::getStatus, "visible")
                .isNull(Comment::getDeletedAt);
    }

    private Debate requireReadableDebate(Long debateId, Long userId) {
        Debate debate = debateMapper.selectById(debateId);
        if (debate == null || debate.getDeletedAt() != null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "辩论不存在");
        }
        if (!"public".equals(debate.getVisibility()) && (userId == null || !debate.getUserId().equals(userId))) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "无权查看该辩论评论");
        }
        return debate;
    }

    private String preview(String content) {
        return content.length() <= 60 ? content : content.substring(0, 60) + "...";
    }
}
