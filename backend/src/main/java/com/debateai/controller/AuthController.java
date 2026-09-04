package com.debateai.controller;

import com.debateai.common.ApiResponse;
import com.debateai.dto.AuthResponse;
import com.debateai.dto.LoginRequest;
import com.debateai.dto.RegisterRequest;
import com.debateai.dto.UpdateMeRequest;
import com.debateai.dto.UserProfileResponse;
import com.debateai.security.AuthUser;
import com.debateai.security.JwtService;
import com.debateai.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AuthController认证功能相关接口
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final JwtService jwtService;

    public AuthController(AuthService authService, JwtService jwtService) {
        this.authService = authService;
        this.jwtService = jwtService;
    }

    /**
     * 注册
     *
     * @param request 注册请求
     * @return 注册结果
     */
    @PostMapping("/register")
    public ApiResponse<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ApiResponse.ok(authService.register(request));
    }

    /**
     * 登录
     *
     * @param request 登录请求
     * @return 登录结果
     */
    @PostMapping("/login")
    public ApiResponse<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.ok(authService.login(request));
    }

    /**
     * 登出
     * @param authUser 当前登录用户
     * @return 登出结果
     */
    @PostMapping("/logout")
    public ApiResponse<String> logout(@AuthenticationPrincipal AuthUser authUser) {
        jwtService.blacklist(authUser.jti());
        return ApiResponse.ok("ok");
    }

    /**
     * 获取当前用户信息
     * @param authUser 当前登录用户
     * @return 当前用户信息
     */
    @GetMapping("/me")
    public ApiResponse<UserProfileResponse> me(@AuthenticationPrincipal AuthUser authUser) {
        return ApiResponse.ok(authService.me(authUser.id()));
    }

    /**
     * 更新当前用户信息（可部分更新）
     * @param authUser 当前登录用户
     * @param request 更新请求
     * @return 更新后的用户信息
     */
    @PatchMapping("/me")
    public ApiResponse<UserProfileResponse> updateMe(
            @AuthenticationPrincipal AuthUser authUser,
            @Valid @RequestBody UpdateMeRequest request
    ) {
        return ApiResponse.ok(authService.updateMe(authUser.id(), request));
    }
}
