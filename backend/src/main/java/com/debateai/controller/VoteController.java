package com.debateai.controller;

import com.debateai.common.ApiResponse;
import com.debateai.dto.VoteRequest;
import com.debateai.dto.VoteResponse;
import com.debateai.security.AuthUser;
import com.debateai.service.VoteService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * VoteController投票功能相关接口
 */
@RestController
@RequestMapping("/api/vote")
public class VoteController {

    private final VoteService voteService;

    public VoteController(VoteService voteService) {
        this.voteService = voteService;
    }

    /**
     * 提交投票
     * @param authUser
     * @param request
     * @return 业务结果
     */
    @PostMapping
    public ApiResponse<VoteResponse> vote(
            @AuthenticationPrincipal AuthUser authUser,
            @Valid @RequestBody VoteRequest request
    ) {
        return ApiResponse.ok(voteService.vote(authUser.id(), request));
    }
}
