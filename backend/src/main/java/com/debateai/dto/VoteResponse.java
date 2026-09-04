package com.debateai.dto;

/**
 * 投票响应
 */
public record VoteResponse(
        Long debateId,
        String votedFor,
        Integer userVoteCount,
        Integer aiVoteCount,
        String winner
) {
}
