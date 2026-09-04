package com.debateai.controller;

import com.debateai.common.ApiResponse;
import com.debateai.dto.LeaderboardEntryResponse;
import com.debateai.dto.PageResponse;
import com.debateai.service.LeaderboardService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * LeaderboardController排行榜相关接口
 */
@RestController
@RequestMapping("/api/leaderboard")
public class LeaderboardController {

    private final LeaderboardService leaderboardService;

    public LeaderboardController(LeaderboardService leaderboardService) {
        this.leaderboardService = leaderboardService;
    }

    /**
     * 获取列表
     * @param page
     * @param size
     * @param sort
     * @return 业务结果
     */
    @GetMapping
    public ApiResponse<PageResponse<LeaderboardEntryResponse>> list(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(defaultValue = "points") String sort
    ) {
        return ApiResponse.ok(leaderboardService.list(page, size, sort));
    }
}
