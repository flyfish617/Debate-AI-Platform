package com.debateai.controller;

import com.debateai.common.ApiResponse;
import com.debateai.dto.CommentResponse;
import com.debateai.dto.CreateCommentRequest;
import com.debateai.dto.PageResponse;
import com.debateai.security.AuthUser;
import com.debateai.service.CommentService;
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
 * 评论控制器，
 */
@RestController
@RequestMapping("/api/debate/{debateId}/comment")
public class CommentController {

    private final CommentService commentService;

    public CommentController(CommentService commentService) {
        this.commentService = commentService;
    }

    /**
     * 查看辩论场外评论；公开辩论可读，私密辩论仅创建者可读
     * @param authUser
     * @param debateId
     * @param page
     * @param size
     * @return
     */
    @GetMapping
    public ApiResponse<PageResponse<CommentResponse>> list(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long debateId,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size
    ) {
        Long viewerId = authUser == null ? null : authUser.id();
        return ApiResponse.ok(commentService.list(debateId, viewerId, page, size));
    }

    /**
     * 创建辩论场外评论
     * @param authUser
     * @param debateId
     * @param request
     * @return
     */
    @PostMapping
    public ApiResponse<CommentResponse> create(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long debateId,
            @Valid @RequestBody CreateCommentRequest request
    ) {
        return ApiResponse.ok(commentService.create(debateId, authUser.id(), request));
    }
}
