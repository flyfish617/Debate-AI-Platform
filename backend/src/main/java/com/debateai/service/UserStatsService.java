package com.debateai.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.debateai.entity.Debate;
import com.debateai.entity.User;
import com.debateai.mapper.DebateMapper;
import com.debateai.mapper.UserMapper;
import org.springframework.stereotype.Service;

/**
 * 用户战绩统计服务
 */
@Service
public class UserStatsService {

    public static final int WIN_POINTS = 10;
    public static final int DRAW_POINTS = 3;
    public static final int LOSS_POINTS = 0;

    private final DebateMapper debateMapper;
    private final UserMapper userMapper;

    public UserStatsService(DebateMapper debateMapper, UserMapper userMapper) {
        this.debateMapper = debateMapper;
        this.userMapper = userMapper;
    }

    /**
     * 重新计算用户战绩
     * @param userId
     */
    public void recalculate(Long userId) {
        if (userId == null) {
            return;
        }

        long wins = debateMapper.selectCount(endedDebates(userId).eq(Debate::getWinner, "user"));
        long losses = debateMapper.selectCount(endedDebates(userId).eq(Debate::getWinner, "ai"));
        long draws = debateMapper.selectCount(endedDebates(userId).eq(Debate::getWinner, "draw"));
        int points = Math.toIntExact(wins * WIN_POINTS + draws * DRAW_POINTS + losses * LOSS_POINTS);

        User user = userMapper.selectById(userId);
        if (user == null || user.getDeletedAt() != null) {
            return;
        }

        user.setWins(Math.toIntExact(wins));
        user.setLosses(Math.toIntExact(losses));
        user.setPoints(points);
        userMapper.updateById(user);
    }

    private LambdaQueryWrapper<Debate> endedDebates(Long userId) {
        return new LambdaQueryWrapper<Debate>()
                .eq(Debate::getUserId, userId)
                .eq(Debate::getStatus, "ended")
                .isNull(Debate::getDeletedAt);
    }
}
