package com.debateai.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.debateai.common.ApiException;
import com.debateai.dto.CreateReportRequest;
import com.debateai.dto.ReportResponse;
import com.debateai.entity.Comment;
import com.debateai.entity.Debate;
import com.debateai.entity.Report;
import com.debateai.entity.Topic;
import com.debateai.entity.User;
import com.debateai.mapper.CommentMapper;
import com.debateai.mapper.DebateMapper;
import com.debateai.mapper.ReportMapper;
import com.debateai.mapper.TopicMapper;
import com.debateai.mapper.UserMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 举报业务服务
 */
@Service
public class ReportService {

    private final ReportMapper reportMapper;
    private final TopicMapper topicMapper;
    private final DebateMapper debateMapper;
    private final CommentMapper commentMapper;
    private final UserMapper userMapper;
    private final NotificationService notificationService;

    public ReportService(
            ReportMapper reportMapper,
            TopicMapper topicMapper,
            DebateMapper debateMapper,
            CommentMapper commentMapper,
            UserMapper userMapper,
            NotificationService notificationService
    ) {
        this.reportMapper = reportMapper;
        this.topicMapper = topicMapper;
        this.debateMapper = debateMapper;
        this.commentMapper = commentMapper;
        this.userMapper = userMapper;
        this.notificationService = notificationService;
    }

    /**
     * 创建数据
     * @param request
     * @param reporterId
     * @return 业务结果
     */
    @Transactional
    public ReportResponse create(CreateReportRequest request, Long reporterId) {
        String targetType = normalizeTargetType(request.targetType());
        Long targetId = request.targetId();
        validateTarget(targetType, targetId, reporterId);

        Long duplicate = reportMapper.selectCount(new LambdaQueryWrapper<Report>()
                .eq(Report::getReporterId, reporterId)
                .eq(Report::getTargetType, targetType)
                .eq(Report::getTargetId, targetId)
                .eq(Report::getStatus, "pending"));
        if (duplicate > 0) {
            throw new ApiException(HttpStatus.CONFLICT, "REPORT_EXISTS", "你已经举报过该内容，管理员处理前无需重复提交");
        }

        Report report = new Report();
        report.setReporterId(reporterId);
        report.setTargetType(targetType);
        report.setTargetId(targetId);
        report.setReason(request.reason().trim());
        report.setStatus("pending");
        reportMapper.insert(report);

        notificationService.notifyAdmins(
                "收到新的内容举报",
                "用户提交了 " + targetType + " #" + targetId + " 的举报，请及时处理。",
                "/admin"
        );

        return ReportResponse.from(report);
    }

    private void validateTarget(String targetType, Long targetId, Long reporterId) {
        switch (targetType) {
            case "topic" -> validateTopic(targetId);
            case "debate" -> validateDebate(targetId, reporterId);
            case "comment" -> validateComment(targetId, reporterId);
            case "user" -> validateUser(targetId, reporterId);
            default -> throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "不支持的举报类型");
        }
    }

    private void validateTopic(Long targetId) {
        Topic topic = topicMapper.selectById(targetId);
        if (topic == null || topic.getDeletedAt() != null || !"visible".equals(topic.getStatus())) {
            throw new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "举报的话题不存在");
        }
    }

    private void validateDebate(Long targetId, Long reporterId) {
        Debate debate = debateMapper.selectById(targetId);
        if (debate == null || debate.getDeletedAt() != null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "举报的辩论不存在");
        }
        if (!"public".equals(debate.getVisibility()) && !debate.getUserId().equals(reporterId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "无权举报该私密辩论");
        }
    }

    private void validateComment(Long targetId, Long reporterId) {
        Comment comment = commentMapper.selectById(targetId);
        if (comment == null || comment.getDeletedAt() != null || !"visible".equals(comment.getStatus())) {
            throw new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "举报的评论不存在");
        }
        if (comment.getUserId().equals(reporterId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "不能举报自己发布的评论");
        }

        Debate debate = debateMapper.selectById(comment.getDebateId());
        if (debate == null || debate.getDeletedAt() != null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "评论所属辩论不存在");
        }
        if (!"public".equals(debate.getVisibility()) && !debate.getUserId().equals(reporterId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "无权举报该私密辩论下的评论");
        }
    }

    private void validateUser(Long targetId, Long reporterId) {
        if (targetId.equals(reporterId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "不能举报自己");
        }

        User user = userMapper.selectById(targetId);
        if (user == null || user.getDeletedAt() != null || !"active".equals(user.getStatus())) {
            throw new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "举报的用户不存在");
        }
    }

    private String normalizeTargetType(String targetType) {
        return switch (targetType == null ? "" : targetType.trim()) {
            case "话题", "topic" -> "topic";
            case "辩论", "debate" -> "debate";
            case "评论", "comment" -> "comment";
            case "用户", "user" -> "user";
            default -> throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "举报类型只能是 topic、debate、comment 或 user");
        };
    }
}
