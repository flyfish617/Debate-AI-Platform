package com.debateai.security;

import com.debateai.entity.User;
import com.debateai.mapper.UserMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * JWT认证过滤器
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    public static final String REFRESHED_TOKEN_HEADER = "X-New-Token";
    public static final String REFRESHED_TOKEN_EXPIRES_IN_HEADER = "X-New-Token-Expires-In";

    private final JwtService jwtService;
    private final UserMapper userMapper;

    public JwtAuthenticationFilter(JwtService jwtService, UserMapper userMapper) {
        this.jwtService = jwtService;
        this.userMapper = userMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            JwtService.TokenPayload tokenPayload = jwtService.parseToken(header.substring("Bearer ".length()));
            AuthUser authUser = tokenPayload.authUser();
            User user = userMapper.selectById(authUser.id());
            if (!jwtService.isBlacklisted(authUser.jti())
                    && user != null
                    && user.getDeletedAt() == null
                    && "active".equals(user.getStatus())) {
                AuthUser currentUser = new AuthUser(user.getId(), user.getUsername(), user.getRole(), authUser.jti());
                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                        currentUser,
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().toUpperCase()))
                );
                SecurityContextHolder.getContext().setAuthentication(authentication);
                refreshTokenIfNecessary(response, tokenPayload, user);
            }
        } catch (Exception ignored) {
            SecurityContextHolder.clearContext();
        }

        filterChain.doFilter(request, response);
    }

    private void refreshTokenIfNecessary(HttpServletResponse response, JwtService.TokenPayload tokenPayload, User user) {
        if (!jwtService.shouldRefresh(tokenPayload)) {
            return;
        }
        JwtService.TokenResult refreshedToken = jwtService.generate(user);
        response.setHeader(REFRESHED_TOKEN_HEADER, refreshedToken.token());
        response.setHeader(REFRESHED_TOKEN_EXPIRES_IN_HEADER, String.valueOf(refreshedToken.expiresIn()));
    }
}
