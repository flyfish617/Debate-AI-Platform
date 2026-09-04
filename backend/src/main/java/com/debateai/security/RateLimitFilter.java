package com.debateai.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * 接口限流过滤器
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final List<RateLimitRule> RULES = List.of(
            new RateLimitRule("auth-register", HttpMethod.POST, Pattern.compile("^/api/auth/register$"), 5, Duration.ofMinutes(10)),
            new RateLimitRule("auth-login", HttpMethod.POST, Pattern.compile("^/api/auth/login$"), 10, Duration.ofMinutes(1)),
            new RateLimitRule("profile-update", HttpMethod.PATCH, Pattern.compile("^/api/auth/me$"), 10, Duration.ofMinutes(1)),
            new RateLimitRule("topic-create", HttpMethod.POST, Pattern.compile("^/api/topic$"), 10, Duration.ofMinutes(1)),
            new RateLimitRule("debate-create", HttpMethod.POST, Pattern.compile("^/api/debate$"), 5, Duration.ofMinutes(1)),
            new RateLimitRule("debate-message", HttpMethod.POST, Pattern.compile("^/api/debate/\\d+/message$"), 8, Duration.ofMinutes(1)),
            new RateLimitRule("debate-message-retry", HttpMethod.POST, Pattern.compile("^/api/debate/\\d+/message/retry$"), 5, Duration.ofMinutes(1)),
            new RateLimitRule("debate-end", HttpMethod.POST, Pattern.compile("^/api/debate/\\d+/end$"), 3, Duration.ofMinutes(1)),
            new RateLimitRule("comment-create", HttpMethod.POST, Pattern.compile("^/api/debate/\\d+/comment$"), 10, Duration.ofMinutes(1)),
            new RateLimitRule("vote", HttpMethod.POST, Pattern.compile("^/api/vote$"), 20, Duration.ofMinutes(1)),
            new RateLimitRule("report-create", HttpMethod.POST, Pattern.compile("^/api/report$"), 5, Duration.ofMinutes(1)),
            new RateLimitRule("admin-topic-delete", HttpMethod.POST, Pattern.compile("^/api/admin/topics/\\d+/delete$"), 10, Duration.ofMinutes(1))
    );

    private final Map<String, WindowCounter> counters = new ConcurrentHashMap<>();
    private final CorsConfigurationSource corsConfigurationSource;

    public RateLimitFilter(CorsConfigurationSource corsConfigurationSource) {
        this.corsConfigurationSource = corsConfigurationSource;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        RateLimitRule rule = matchRule(request);
        if (rule == null) {
            filterChain.doFilter(request, response);
            return;
        }

        String key = rule.name() + ":" + requesterKey(request);
        long now = System.currentTimeMillis();
        WindowCounter counter = counters.compute(key, (ignored, current) -> {
            if (current == null || current.expiresAt() <= now) {
                return new WindowCounter(1, now + rule.window().toMillis());
            }
            return new WindowCounter(current.count() + 1, current.expiresAt());
        });
        cleanupExpired(now);

        if (counter.count() > rule.limit()) {
            writeRateLimitResponse(request, response, Math.max(1, (counter.expiresAt() - now + 999) / 1000));
            return;
        }

        filterChain.doFilter(request, response);
    }

    private RateLimitRule matchRule(HttpServletRequest request) {
        String method = request.getMethod();
        String path = request.getRequestURI();
        return RULES.stream()
                .filter(rule -> rule.method().matches(method))
                .filter(rule -> rule.pathPattern().matcher(path).matches())
                .findFirst()
                .orElse(null);
    }

    private String requesterKey(HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AuthUser authUser) {
            return "user:" + authUser.id();
        }
        return "ip:" + clientIp(request);
    }

    private String clientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }

    private void cleanupExpired(long now) {
        if (counters.size() < 1000) {
            return;
        }
        counters.entrySet().removeIf(entry -> entry.getValue().expiresAt() <= now);
    }

    private void writeRateLimitResponse(HttpServletRequest request, HttpServletResponse response, long retryAfterSeconds) throws IOException {
        applyCorsHeaders(request, response);
        response.setStatus(429);
        response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/json;charset=UTF-8");
        byte[] body = """
                {"success":false,"data":null,"error":{"code":"RATE_LIMIT","message":"操作太频繁，请稍后再试","details":null}}
                """.getBytes(StandardCharsets.UTF_8);
        response.setContentLength(body.length);
        ServletOutputStream outputStream = response.getOutputStream();
        outputStream.write(body);
        outputStream.flush();
    }

    private void applyCorsHeaders(HttpServletRequest request, HttpServletResponse response) {
        String origin = request.getHeader("Origin");
        if (origin == null || origin.isBlank()) {
            return;
        }

        CorsConfiguration corsConfiguration = corsConfigurationSource.getCorsConfiguration(request);
        if (corsConfiguration == null || corsConfiguration.checkOrigin(origin) == null) {
            return;
        }

        response.setHeader("Access-Control-Allow-Origin", origin);
        response.setHeader("Access-Control-Allow-Credentials", "true");
        response.addHeader("Vary", "Origin");
        response.addHeader("Access-Control-Expose-Headers", "Retry-After");
    }

    private record RateLimitRule(String name, HttpMethod method, Pattern pathPattern, int limit, Duration window) {
    }

    private record WindowCounter(int count, long expiresAt) {
    }
}
