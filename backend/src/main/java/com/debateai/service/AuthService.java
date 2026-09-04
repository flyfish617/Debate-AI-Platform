package com.debateai.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.debateai.common.ApiException;
import com.debateai.dto.AuthResponse;
import com.debateai.dto.LoginRequest;
import com.debateai.dto.RegisterRequest;
import com.debateai.dto.UpdateMeRequest;
import com.debateai.dto.UserProfileResponse;
import com.debateai.entity.User;
import com.debateai.mapper.UserMapper;
import com.debateai.security.JwtService;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 认证业务服务
 */
@Service
public class AuthService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserMapper userMapper, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    /**
     * 注册
     * @param request
     * @return 业务结果
     */
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        // .trim() 是 Java String 类的方法，用于去除字符串首尾的空白字符（如空格、制表符、换行符等）
        String username = request.username().trim();
        String email = request.email().trim().toLowerCase();

        if (existsByUsername(username)) {
            throw new ApiException(HttpStatus.CONFLICT, "CONFLICT", "用户名已存在");
        }
        if (existsByEmail(email)) {
            throw new ApiException(HttpStatus.CONFLICT, "CONFLICT", "邮箱已存在");
        }

        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRole("user");
        user.setStatus("active");
        user.setPoints(0);
        user.setWins(0);
        user.setLosses(0);
        userMapper.insert(user);

        JwtService.TokenResult token = jwtService.generate(user);
        return new AuthResponse(token.token(), token.expiresIn(), UserProfileResponse.from(user));
    }

    /**
     * 登录
     * @param request
     * @return 业务结果
     */
    public AuthResponse login(LoginRequest request) {
        User user = findByAccount(request.account().trim());
        if (user == null || !passwordMatches(request.password(), user)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "账号或密码错误");
        }
        if (!"active".equals(user.getStatus())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "用户已被禁用");
        }
    //生成jwt令牌
        JwtService.TokenResult token = jwtService.generate(user);
        return new AuthResponse(token.token(), token.expiresIn(), UserProfileResponse.from(user));
    }

    /**
     * 获取当前用户信息
     * @param userId
     * @return 业务结果
     */
    public UserProfileResponse me(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null || user.getDeletedAt() != null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "登录已失效");
        }
        return UserProfileResponse.from(user);
    }

    /**
     * 更新当前用户信息
     * @param userId
     * @param request
     * @return 业务结果
     */
    @Transactional
    public UserProfileResponse updateMe(Long userId, UpdateMeRequest request) {
        User user = userMapper.selectById(userId);
        if (user == null || user.getDeletedAt() != null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "登录已失效");
        }
        if (!"active".equals(user.getStatus())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "用户已被禁用");
        }

        user.setAvatarUrl(trimToNull(request.avatarUrl()));
        user.setBio(trimToNull(request.bio()));
        userMapper.updateById(user);
        return UserProfileResponse.from(user);
    }

    private boolean existsByUsername(String username) {
        return userMapper.selectCount(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, username)
                .isNull(User::getDeletedAt)) > 0;
    }

    private boolean existsByEmail(String email) {
        return userMapper.selectCount(new LambdaQueryWrapper<User>()
                .eq(User::getEmail, email)
                .isNull(User::getDeletedAt)) > 0;
    }

    private User findByAccount(String account) {
        return userMapper.selectOne(new LambdaQueryWrapper<User>()
                .isNull(User::getDeletedAt)
                .and(wrapper -> wrapper
                        .eq(User::getUsername, account)
                        .or()
                        .eq(User::getEmail, account.toLowerCase()))
                .last("LIMIT 1"));
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private boolean passwordMatches(String rawPassword, User user) {
        String stored = user.getPasswordHash();
        if (stored == null || stored.isBlank()) {
            return false;
        }

        if (stored.startsWith("$2a$") || stored.startsWith("$2b$") || stored.startsWith("$2y$")) {
            return passwordEncoder.matches(rawPassword, stored);
        }

        boolean matchedLegacyPlainText = rawPassword.equals(stored);
        if (matchedLegacyPlainText) {
            user.setPasswordHash(passwordEncoder.encode(rawPassword));
            userMapper.updateById(user);
        }
        return matchedLegacyPlainText;
    }
}
