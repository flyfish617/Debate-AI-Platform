package com.debateai.controller;

import com.debateai.common.ApiResponse;
import com.debateai.dto.CreateTopicRequest;
import com.debateai.dto.PageResponse;
import com.debateai.dto.TopicResponse;
import com.debateai.security.AuthUser;
import com.debateai.service.TopicService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * TopicController话题功能相关接口
 */
@RestController
@RequestMapping("/api/topic")
public class TopicController {

    private final TopicService topicService;

    public TopicController(TopicService topicService) {
        this.topicService = topicService;
    }

    /**
     * 获取列表
     * @param page
     * @param size
     * @param category
     * @return 业务结果
     */
    @GetMapping
    public ApiResponse<PageResponse<TopicResponse>> list(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) String category
    ) {
        return ApiResponse.ok(topicService.list(page, size, category));
    }

    /**
     * 获取详情
     * @param id
     * @return 业务结果
     */
    @GetMapping("/{id}")
    public ApiResponse<TopicResponse> detail(@PathVariable Long id) {
        return ApiResponse.ok(topicService.detail(id));
    }

    /**
     * 创建数据
     * @param authUser
     * @param request
     * @return 业务结果
     */
    @PostMapping
    public ApiResponse<TopicResponse> create(
            @AuthenticationPrincipal AuthUser authUser,
            @Valid @RequestBody CreateTopicRequest request
    ) {
        return ApiResponse.ok(topicService.create(request, authUser.id()));
    }
}
