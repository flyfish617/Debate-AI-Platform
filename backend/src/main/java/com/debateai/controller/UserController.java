package com.debateai.controller;

import com.debateai.common.ApiResponse;
import com.debateai.dto.PublicUserProfileResponse;
import com.debateai.service.UserService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * UserController用户主页相关接口
 */
@RestController
@RequestMapping("/api/user")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    /**
     * 获取公开用户主页
     * @param username
     * @return 业务结果
     */
    @GetMapping("/{username}")
    public ApiResponse<PublicUserProfileResponse> publicProfile(@PathVariable String username) {
        return ApiResponse.ok(userService.publicProfile(username));
    }
}
