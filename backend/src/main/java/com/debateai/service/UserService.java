package com.debateai.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.debateai.common.ApiException;
import com.debateai.dto.DebateSummaryResponse;
import com.debateai.dto.PublicUserProfileResponse;
import com.debateai.dto.TopicResponse;
import com.debateai.entity.Comment;
import com.debateai.entity.Debate;
import com.debateai.entity.Topic;
import com.debateai.entity.User;
import com.debateai.mapper.CommentMapper;
import com.debateai.mapper.DebateMapper;
import com.debateai.mapper.TopicMapper;
import com.debateai.mapper.UserMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 用户主页业务服务
 */
@Service
public class UserService {

    private final UserMapper userMapper;
    private final TopicMapper topicMapper;
    private final DebateMapper debateMapper;
    private final CommentMapper commentMapper;
    private final UserStatsService userStatsService;

    public UserService(
            UserMapper userMapper,
            TopicMapper topicMapper,
            DebateMapper debateMapper,
            CommentMapper commentMapper,
            UserStatsService userStatsService
    ) {
        this.userMapper = userMapper;
        this.topicMapper = topicMapper;
        this.debateMapper = debateMapper;
        this.commentMapper = commentMapper;
        this.userStatsService = userStatsService;
    }

    /**
     * 获取公开用户主页
     * @param username
     * @return 业务结果
     */
    public PublicUserProfileResponse publicProfile(String username) {
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, username)
                .eq(User::getStatus, "active")
                .isNull(User::getDeletedAt));

        if (user == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "用户不存在");
        }
        userStatsService.recalculate(user.getId());
        user = userMapper.selectById(user.getId());

        Long createdTopicCount = topicMapper.selectCount(visibleTopics(user.getId()));
        Long publicDebateCount = debateMapper.selectCount(publicDebates(user.getId()));
        Long endedPublicDebateCount = debateMapper.selectCount(publicDebates(user.getId()).eq(Debate::getStatus, "ended"));
        Long commentCount = commentMapper.selectCount(new LambdaQueryWrapper<Comment>()
                .eq(Comment::getUserId, user.getId())
                .eq(Comment::getStatus, "visible")
                .isNull(Comment::getDeletedAt));

        List<TopicResponse> recentTopics = topicMapper.selectList(visibleTopics(user.getId())
                        .orderByDesc(Topic::getCreatedAt)
                        .orderByDesc(Topic::getId)
                        .last("LIMIT 5"))
                .stream()
                .map(TopicResponse::from)
                .toList();

        List<DebateSummaryResponse> recentDebates = debateMapper.selectList(publicDebates(user.getId())
                        .orderByDesc(Debate::getCreatedAt)
                        .orderByDesc(Debate::getId)
                        .last("LIMIT 10"))
                .stream()
                .map(this::toDebateSummary)
                .toList();

        return PublicUserProfileResponse.from(
                user,
                createdTopicCount,
                publicDebateCount,
                endedPublicDebateCount,
                commentCount,
                recentTopics,
                recentDebates
        );
    }

    private DebateSummaryResponse toDebateSummary(Debate debate) {
        Topic topic = topicMapper.selectById(debate.getTopicId());
        TopicResponse topicResponse = topic == null ? null : TopicResponse.from(topic);
        return DebateSummaryResponse.from(debate, topicResponse);
    }

    private LambdaQueryWrapper<Topic> visibleTopics(Long userId) {
        return new LambdaQueryWrapper<Topic>()
                .eq(Topic::getCreatorId, userId)
                .eq(Topic::getStatus, "visible")
                .isNull(Topic::getDeletedAt);
    }

    private LambdaQueryWrapper<Debate> publicDebates(Long userId) {
        return new LambdaQueryWrapper<Debate>()
                .eq(Debate::getUserId, userId)
                .eq(Debate::getVisibility, "public")
                .isNull(Debate::getDeletedAt);
    }
}
