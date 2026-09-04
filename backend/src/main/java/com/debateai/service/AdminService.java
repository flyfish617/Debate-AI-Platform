package com.debateai.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.debateai.common.ApiException;
import com.debateai.dto.AdminReportHandleRequest;
import com.debateai.dto.AdminReportResponse;
import com.debateai.dto.AdminStatusRequest;
import com.debateai.dto.AdminUserResponse;
import com.debateai.dto.PageResponse;
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

import java.time.LocalDateTime;
import java.util.List;

/**
 * 管理端业务服务
 */
@Service
public class AdminService {

    private static final long MAX_PAGE_SIZE = 50;

    private final ReportMapper reportMapper;
    private final UserMapper userMapper;
    private final TopicMapper topicMapper;
    private final DebateMapper debateMapper;
    private final CommentMapper commentMapper;
    private final NotificationService notificationService;

    public AdminService(
            ReportMapper reportMapper,
            UserMapper userMapper,
            TopicMapper topicMapper,
            DebateMapper debateMapper,
            CommentMapper commentMapper,
            NotificationService notificationService
    ) {
        this.reportMapper = reportMapper;
        this.userMapper = userMapper;
        this.topicMapper = topicMapper;
        this.debateMapper = debateMapper;
        this.commentMapper = commentMapper;
        this.notificationService = notificationService;
    }

    /**
     * 获取举报分页列表
     * @param page
     * @param size
     * @param status
     * @return 业务结果
     */
    public PageResponse<AdminReportResponse> listReports(long page, long size, String status) {
        long normalizedPage = Math.max(page, 1);
        long normalizedSize = Math.max(1, Math.min(size, MAX_PAGE_SIZE));
        long offset = (normalizedPage - 1) * normalizedSize;

        LambdaQueryWrapper<Report> wrapper = new LambdaQueryWrapper<Report>()
                .orderByDesc(Report::getCreatedAt)
                .orderByDesc(Report::getId);
        if (status != null && !status.isBlank()) {
            wrapper.eq(Report::getStatus, normalizeReportStatus(status));
        }

        long total = reportMapper.selectCount(wrapper);
        List<AdminReportResponse> list = reportMapper.selectList(wrapper.last("LIMIT " + offset + ", " + normalizedSize))
                .stream()
                .map(this::toReportResponse)
                .toList();

        return new PageResponse<>(list, total, normalizedPage, normalizedSize);
    }

    /**
     * 处理举报
     * @param reportId
     * @param request
     * @return 业务结果
     */
    @Transactional
    public AdminReportResponse handleReport(Long reportId, AdminReportHandleRequest request) {
        Report report = requireReport(reportId);
        if (!"pending".equals(report.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "REPORT_ALREADY_HANDLED", "该举报已经处理过");
        }

        String action = normalizeReportAction(request.action());
        if ("reject".equals(action)) {
            report.setStatus("rejected");
        } else {
            if ("moderate".equals(action)) {
                moderateTarget(report.getTargetType(), report.getTargetId());
            }
            report.setStatus("resolved");
        }
        report.setUpdatedAt(LocalDateTime.now());
        reportMapper.updateById(report);
        notificationService.notifyUser(
                report.getReporterId(),
                "report_handled",
                "你的举报已处理",
                "reject".equals(action) ? "你的举报已被驳回。" : "你的举报已由管理员处理。",
                targetLink(report.getTargetType(), report.getTargetId())
        );
        return toReportResponse(reportMapper.selectById(reportId));
    }

    /**
     * 获取用户分页列表
     * @param page
     * @param size
     * @param status
     * @return 业务结果
     */
    public PageResponse<AdminUserResponse> listUsers(long page, long size, String status) {
        long normalizedPage = Math.max(page, 1);
        long normalizedSize = Math.max(1, Math.min(size, MAX_PAGE_SIZE));
        long offset = (normalizedPage - 1) * normalizedSize;

        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<User>()
                .isNull(User::getDeletedAt)
                .orderByDesc(User::getCreatedAt)
                .orderByDesc(User::getId);
        if (status != null && !status.isBlank()) {
            wrapper.eq(User::getStatus, normalizeUserStatus(status));
        }

        long total = userMapper.selectCount(wrapper);
        List<AdminUserResponse> list = userMapper.selectList(wrapper.last("LIMIT " + offset + ", " + normalizedSize))
                .stream()
                .map(AdminUserResponse::from)
                .toList();
        return new PageResponse<>(list, total, normalizedPage, normalizedSize);
    }

    /**
     * 更新用户状态
     * @param userId
     * @param request
     * @param adminId
     * @return 业务结果
     */
    @Transactional
    public AdminUserResponse updateUserStatus(Long userId, AdminStatusRequest request, Long adminId) {
        if (userId.equals(adminId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "不能禁用或修改自己的账号状态");
        }
        User user = requireUser(userId);
        user.setStatus(normalizeUserStatus(request.status()));
        userMapper.updateById(user);
        return AdminUserResponse.from(userMapper.selectById(userId));
    }

    /**
     * 更新话题状态
     * @param topicId
     * @param request
     * @return 业务结果
     */
    @Transactional
    public AdminReportResponse updateTopicStatus(Long topicId, AdminStatusRequest request) {
        Topic topic = requireTopic(topicId);
        topic.setStatus(normalizeTopicStatus(request.status()));
        topicMapper.updateById(topic);
        return findLatestReportFor("topic", topicId);
    }

    /**
     * 删除话题
     * @param topicId
     * @return 业务结果
     */
    @Transactional
    public AdminReportResponse deleteTopic(Long topicId) {
        Topic topic = requireTopic(topicId);
        LocalDateTime deletedAt = LocalDateTime.now();
        topic.setStatus("hidden");
        topic.setDeletedAt(deletedAt);
        topicMapper.updateById(topic);

        List<Debate> debates = debateMapper.selectList(new LambdaQueryWrapper<Debate>()
                .eq(Debate::getTopicId, topicId)
                .isNull(Debate::getDeletedAt));
        for (Debate debate : debates) {
            debate.setDeletedAt(deletedAt);
            debateMapper.updateById(debate);
        }

        return findLatestReportFor("topic", topicId);
    }

    /**
     * 更新评论状态
     * @param commentId
     * @param request
     * @return 业务结果
     */
    @Transactional
    public AdminReportResponse updateCommentStatus(Long commentId, AdminStatusRequest request) {
        Comment comment = requireComment(commentId);
        comment.setStatus(normalizeCommentStatus(request.status()));
        commentMapper.updateById(comment);
        return findLatestReportFor("comment", commentId);
    }

    /**
     * 隐藏辩论
     * @param debateId
     * @return 业务结果
     */
    @Transactional
    public AdminReportResponse hideDebate(Long debateId) {
        Debate debate = requireDebate(debateId);
        if (debate.getDeletedAt() == null) {
            debate.setDeletedAt(LocalDateTime.now());
            debateMapper.updateById(debate);
        }
        return findLatestReportFor("debate", debateId);
    }

    private void moderateTarget(String targetType, Long targetId) {
        switch (targetType) {
            case "topic" -> {
                Topic topic = requireTopic(targetId);
                topic.setStatus("hidden");
                topicMapper.updateById(topic);
            }
            case "comment" -> {
                Comment comment = requireComment(targetId);
                comment.setStatus("hidden");
                commentMapper.updateById(comment);
            }
            case "debate" -> {
                Debate debate = requireDebate(targetId);
                debate.setDeletedAt(LocalDateTime.now());
                debateMapper.updateById(debate);
            }
            case "user" -> {
                User user = requireUser(targetId);
                user.setStatus("disabled");
                userMapper.updateById(user);
            }
            default -> throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "不支持的举报对象类型");
        }
    }

    private AdminReportResponse toReportResponse(Report report) {
        User reporter = userMapper.selectById(report.getReporterId());
        TargetInfo target = targetInfo(report.getTargetType(), report.getTargetId());
        return AdminReportResponse.from(
                report,
                reporter == null ? "未知用户" : reporter.getUsername(),
                target.label(),
                target.status()
        );
    }

    private AdminReportResponse findLatestReportFor(String targetType, Long targetId) {
        Report report = reportMapper.selectOne(new LambdaQueryWrapper<Report>()
                .eq(Report::getTargetType, targetType)
                .eq(Report::getTargetId, targetId)
                .orderByDesc(Report::getCreatedAt)
                .orderByDesc(Report::getId)
                .last("LIMIT 1"));
        if (report == null) {
            TargetInfo target = targetInfo(targetType, targetId);
            return new AdminReportResponse(
                    null,
                    null,
                    null,
                    targetType,
                    targetId,
                    target.label(),
                    target.status(),
                    null,
                    "resolved",
                    LocalDateTime.now(),
                    LocalDateTime.now()
            );
        }
        return toReportResponse(report);
    }

    private TargetInfo targetInfo(String targetType, Long targetId) {
        return switch (targetType) {
            case "topic" -> {
                Topic topic = topicMapper.selectById(targetId);
                yield topic == null
                        ? new TargetInfo("话题已不存在", "missing")
                        : new TargetInfo(topic.getTitle(), topic.getDeletedAt() == null ? topic.getStatus() : "deleted");
            }
            case "debate" -> {
                Debate debate = debateMapper.selectById(targetId);
                if (debate == null) {
                    yield new TargetInfo("辩论已不存在", "missing");
                }
                Topic topic = topicMapper.selectById(debate.getTopicId());
                String label = topic == null ? "辩论 #" + targetId : "辩论 #" + targetId + "：" + topic.getTitle();
                yield new TargetInfo(label, debate.getDeletedAt() == null ? debate.getStatus() : "hidden");
            }
            case "comment" -> {
                Comment comment = commentMapper.selectById(targetId);
                yield comment == null
                        ? new TargetInfo("评论已不存在", "missing")
                        : new TargetInfo(truncate(comment.getContent()), comment.getStatus());
            }
            case "user" -> {
                User user = userMapper.selectById(targetId);
                yield user == null
                        ? new TargetInfo("用户已不存在", "missing")
                        : new TargetInfo(user.getUsername(), user.getStatus());
            }
            default -> new TargetInfo("未知对象", "unknown");
        };
    }

    private Report requireReport(Long id) {
        Report report = reportMapper.selectById(id);
        if (report == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "举报不存在");
        }
        return report;
    }

    private User requireUser(Long id) {
        User user = userMapper.selectById(id);
        if (user == null || user.getDeletedAt() != null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "用户不存在");
        }
        return user;
    }

    private Topic requireTopic(Long id) {
        Topic topic = topicMapper.selectById(id);
        if (topic == null || topic.getDeletedAt() != null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "话题不存在");
        }
        return topic;
    }

    private Debate requireDebate(Long id) {
        Debate debate = debateMapper.selectById(id);
        if (debate == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "辩论不存在");
        }
        return debate;
    }

    private Comment requireComment(Long id) {
        Comment comment = commentMapper.selectById(id);
        if (comment == null || comment.getDeletedAt() != null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "评论不存在");
        }
        return comment;
    }

    private String normalizeReportStatus(String status) {
        return switch (status.trim()) {
            case "待处理", "pending" -> "pending";
            case "已处理", "resolved" -> "resolved";
            case "已驳回", "rejected" -> "rejected";
            default -> throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "举报状态只能是 pending、resolved 或 rejected");
        };
    }

    private String normalizeReportAction(String action) {
        return switch (action.trim()) {
            case "驳回", "reject" -> "reject";
            case "仅标记处理", "resolve", "none" -> "resolve";
            case "处理并治理对象", "moderate", "hide", "disable" -> "moderate";
            default -> throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "处理动作只能是 reject、resolve 或 moderate");
        };
    }

    private String normalizeUserStatus(String status) {
        return switch (status.trim()) {
            case "启用", "active" -> "active";
            case "禁用", "disabled" -> "disabled";
            default -> throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "用户状态只能是 active 或 disabled");
        };
    }

    private String normalizeTopicStatus(String status) {
        return switch (status.trim()) {
            case "显示", "visible" -> "visible";
            case "隐藏", "hidden" -> "hidden";
            default -> throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "话题状态只能是 visible 或 hidden");
        };
    }

    private String normalizeCommentStatus(String status) {
        return switch (status.trim()) {
            case "显示", "visible" -> "visible";
            case "隐藏", "hidden" -> "hidden";
            default -> throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "评论状态只能是 visible 或 hidden");
        };
    }

    private String truncate(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        return trimmed.length() <= 40 ? trimmed : trimmed.substring(0, 40) + "...";
    }

    private String targetLink(String targetType, Long targetId) {
        return switch (targetType) {
            case "topic" -> "/topic/" + targetId;
            case "debate" -> "/debate/" + targetId;
            case "comment" -> {
                Comment comment = commentMapper.selectById(targetId);
                yield comment == null ? "/me" : "/debate/" + comment.getDebateId();
            }
            case "user" -> {
                User user = userMapper.selectById(targetId);
                yield user == null ? "/me" : "/profile/" + user.getUsername();
            }
            default -> "/me";
        };
    }

    private record TargetInfo(String label, String status) {
    }
}
