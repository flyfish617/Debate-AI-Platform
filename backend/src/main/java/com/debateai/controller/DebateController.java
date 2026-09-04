package com.debateai.controller;

import com.debateai.common.ApiResponse;
import com.debateai.dto.CreateDebateRequest;
import com.debateai.dto.CreateDebateResponse;
import com.debateai.dto.DebateDetailResponse;
import com.debateai.dto.DebateSummaryResponse;
import com.debateai.dto.EndDebateResponse;
import com.debateai.dto.MessageResponse;
import com.debateai.dto.PageResponse;
import com.debateai.dto.RetryAiReplyRequest;
import com.debateai.dto.SendMessageRequest;
import com.debateai.common.ApiException;
import com.debateai.security.AuthUser;
import com.debateai.security.JwtService;
import com.debateai.service.DebateService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.Map;

/**
 * DebateController辩论功能相关接口
 */
@RestController
@RequestMapping("/api/debate")
public class DebateController {

    private final DebateService debateService;
    private final JwtService jwtService;

    public DebateController(DebateService debateService, JwtService jwtService) {
        this.debateService = debateService;
        this.jwtService = jwtService;
    }

    /**
     * 获取公开辩论列表
     * @param page
     * @param size
     * @param status
     * @return
     */
    @GetMapping
    public ApiResponse<PageResponse<DebateSummaryResponse>> list(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) String status
    ) {
        return ApiResponse.ok(debateService.listPublic(page, size, status));
    }

    /**
     * 创建辩论
     * @param authUser
     * @param request
     * @return
     */
    @PostMapping
    public ApiResponse<CreateDebateResponse> create(
            @AuthenticationPrincipal AuthUser authUser,
            @Valid @RequestBody CreateDebateRequest request
    ) {
        return ApiResponse.ok(debateService.create(request, authUser.id()));
    }

    /**
     * 获取辩论详情
     * @param authUser
     * @param id
     * @return
     */
    @GetMapping("/{id}")
    public ApiResponse<DebateDetailResponse> detail(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long id
    ) {
        Long userId = authUser == null ? null : authUser.id();
        return ApiResponse.ok(debateService.detail(id, userId));
    }

    /**
     * 结束辩论
     * @param authUser
     * @param id
     * @return
     */
    @PostMapping("/{id}/end")
    public ApiResponse<EndDebateResponse> end(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long id
    ) {
        return ApiResponse.ok(debateService.end(id, authUser.id()));
    }

    /**
     * 用户发送消息并保存
     * @param servletRequest
     * @param id
     * @param request
     * @return
     */
    @PostMapping(value = "/{id}/message", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<Map<String, Object>>> sendMessage(
            HttpServletRequest servletRequest,
            @PathVariable("id") Long id,
            @Valid @RequestBody SendMessageRequest request
    ) {
        //解析JWT令牌，获取用户
        AuthUser authUser = resolveAuthUser(servletRequest);
        //保存用户发言
        MessageResponse userMessage = debateService.saveUserMessage(id, authUser.id(), request);
        Flux<ServerSentEvent<Map<String, Object>>> accepted = Flux.just(ServerSentEvent.<Map<String, Object>>builder()
                .event("accepted")
                .data(Map.of(
                        "user_message_id", userMessage.id(),
                        "round", userMessage.round()
                ))
                .build());

        return accepted.concatWith(streamAiReply(id, authUser.id(), userMessage.id(), false));
    }

    /**
     * 用户重试AI回复并保存
     * @param servletRequest
     * @param id
     * @param request
     * @return
     */
    @PostMapping(value = "/{id}/message/retry", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<Map<String, Object>>> retryAiReply(
            HttpServletRequest servletRequest,
            @PathVariable("id") Long id,
            @Valid @RequestBody RetryAiReplyRequest request
    ) {
        AuthUser authUser = resolveAuthUser(servletRequest);
        return streamAiReply(id, authUser.id(), request.userMessageId(), true);
    }

    private Flux<ServerSentEvent<Map<String, Object>>> streamAiReply(
            Long debateId,
            Long userId,
            Long userMessageId,
            boolean retry
    ) {
        long startedAt = System.currentTimeMillis();
        StringBuilder aiContent = new StringBuilder();

        Flux<String> aiTokens = retry
                ? debateService.retryAiReply(debateId, userId, userMessageId)
                : debateService.streamAiReply(debateId, userId);

        return aiTokens
                .doOnNext(aiContent::append)
                .map(token -> ServerSentEvent.<Map<String, Object>>builder()
                        .event("token")
                        .data(Map.of("text", token))
                        .build())
                .concatWith(Flux.defer(() -> {
                    MessageResponse saved = retry
                            ? debateService.saveRetriedAiReply(debateId, userId, userMessageId, aiContent.toString(), startedAt)
                            : debateService.saveAiReply(debateId, userId, aiContent.toString(), startedAt);
                    return Flux.just(ServerSentEvent.<Map<String, Object>>builder()
                            .event("done")
                            .data(Map.of(
                                    "user_message_id", userMessageId,
                                    "ai_message_id", saved.id(),
                                    "round", saved.round()
                            ))
                            .build());
                }))
                .onErrorResume(error -> {
                    String message = error.getMessage() == null || error.getMessage().isBlank()
                            ? "AI 回复生成失败，请稍后重试"
                            : error.getMessage();
                    MessageResponse failed = debateService.saveFailedAiReply(debateId, userId, userMessageId, message, startedAt);
                    return Flux.just(ServerSentEvent.<Map<String, Object>>builder()
                            .event("error")
                            .data(Map.of(
                                    "code", "AI_ERROR",
                                    "message", message,
                                    "user_message_id", userMessageId,
                                    "failed_ai_message_id", failed.id()
                            ))
                            .build());
                });
    }

    private AuthUser resolveAuthUser(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "请先登录");
        }

        try {
            AuthUser authUser = jwtService.parse(header.substring("Bearer ".length()));
            if (jwtService.isBlacklisted(authUser.jti())) {
                throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "登录已失效");
            }
            return authUser;
        } catch (ApiException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "请先登录");
        }
    }
}
