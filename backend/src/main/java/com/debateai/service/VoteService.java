package com.debateai.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.debateai.common.ApiException;
import com.debateai.dto.VoteRequest;
import com.debateai.dto.VoteResponse;
import com.debateai.entity.Debate;
import com.debateai.entity.User;
import com.debateai.entity.Vote;
import com.debateai.mapper.DebateMapper;
import com.debateai.mapper.UserMapper;
import com.debateai.mapper.VoteMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 投票业务服务
 */
@Service
public class VoteService {

    private final VoteMapper voteMapper;
    private final DebateMapper debateMapper;
    private final UserMapper userMapper;
    private final UserStatsService userStatsService;
    private final NotificationService notificationService;

    public VoteService(
            VoteMapper voteMapper,
            DebateMapper debateMapper,
            UserMapper userMapper,
            UserStatsService userStatsService,
            NotificationService notificationService
    ) {
        this.voteMapper = voteMapper;
        this.debateMapper = debateMapper;
        this.userMapper = userMapper;
        this.userStatsService = userStatsService;
        this.notificationService = notificationService;
    }

    /**
     * 提交投票
     * @param voterId
     * @param request
     * @return 业务结果
     */
    @Transactional
    public VoteResponse vote(Long voterId, VoteRequest request) {
        Debate debate = requireVotableDebate(request.debateId(), voterId);
        String votedFor = request.votedFor().trim();

        Vote vote = voteMapper.selectOne(new LambdaQueryWrapper<Vote>()
                .eq(Vote::getDebateId, debate.getId())
                .eq(Vote::getVoterId, voterId));

        if (vote == null) {
            vote = new Vote();
            vote.setDebateId(debate.getId());
            vote.setVoterId(voterId);
            vote.setVotedFor(votedFor);
            voteMapper.insert(vote);
        } else {
            vote.setVotedFor(votedFor);
            voteMapper.updateById(vote);
        }

        int userVotes = Math.toIntExact(voteMapper.selectCount(new LambdaQueryWrapper<Vote>()
                .eq(Vote::getDebateId, debate.getId())
                .eq(Vote::getVotedFor, "user")));
        int aiVotes = Math.toIntExact(voteMapper.selectCount(new LambdaQueryWrapper<Vote>()
                .eq(Vote::getDebateId, debate.getId())
                .eq(Vote::getVotedFor, "ai")));

        debate.setUserVoteCount(userVotes);
        debate.setAiVoteCount(aiVotes);
        debate.setWinner(resolveWinner(userVotes, aiVotes));
        debateMapper.updateById(debate);
        userStatsService.recalculate(debate.getUserId());

        User voter = userMapper.selectById(voterId);
        notificationService.notifyUser(
                debate.getUserId(),
                "vote",
                "你的辩论收到投票",
                (voter == null ? "有用户" : voter.getUsername()) + " 投给了" + ("user".equals(votedFor) ? "用户方" : "AI 方") + "。",
                "/debate/" + debate.getId()
        );

        return new VoteResponse(debate.getId(), votedFor, userVotes, aiVotes, debate.getWinner());
    }

    private Debate requireVotableDebate(Long debateId, Long voterId) {
        Debate debate = debateMapper.selectById(debateId);
        if (debate == null || debate.getDeletedAt() != null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "辩论不存在");
        }
        if (!"public".equals(debate.getVisibility()) && !debate.getUserId().equals(voterId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "无权参与该辩论投票");
        }
        if (!"ended".equals(debate.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "DEBATE_NOT_ENDED", "辩论结束后才能投票");
        }
        if (debate.getUserId().equals(voterId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "不能给自己的辩论投票");
        }
        return debate;
    }

    private String resolveWinner(int userVotes, int aiVotes) {
        if (userVotes > aiVotes) {
            return "user";
        }
        if (aiVotes > userVotes) {
            return "ai";
        }
        return "draw";
    }
}
