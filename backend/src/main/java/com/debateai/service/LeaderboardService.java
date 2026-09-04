package com.debateai.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.debateai.common.ApiException;
import com.debateai.dto.LeaderboardEntryResponse;
import com.debateai.dto.PageResponse;
import com.debateai.entity.Debate;
import com.debateai.entity.User;
import com.debateai.mapper.DebateMapper;
import com.debateai.mapper.UserMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.stream.IntStream;

/**
 * 排行榜业务服务
 */
@Service
public class LeaderboardService {

    private static final Logger log = LoggerFactory.getLogger(LeaderboardService.class);
    private static final long MAX_PAGE_SIZE = 50;
    private static final Duration SNAPSHOT_TTL = Duration.ofMinutes(10);
    private static final String CACHE_KEY_PREFIX = "debateai:leaderboard:";
    private static final List<String> SORTS = List.of("points", "wins", "debates");

    private final UserMapper userMapper;
    private final DebateMapper debateMapper;
    private final UserStatsService userStatsService;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public LeaderboardService(
            UserMapper userMapper,
            DebateMapper debateMapper,
            UserStatsService userStatsService,
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper
    ) {
        this.userMapper = userMapper;
        this.debateMapper = debateMapper;
        this.userStatsService = userStatsService;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * 获取排行榜列表
     * @param page 页码
     * @param size 每页数量
     * @param sort 排序方式
     * @return 分页排行榜
     */
    public PageResponse<LeaderboardEntryResponse> list(long page, long size, String sort) {
        long normalizedPage = Math.max(page, 1);
        long normalizedSize = Math.max(1, Math.min(size, MAX_PAGE_SIZE));
        String normalizedSort = normalizeSort(sort);

        List<LeaderboardEntryResponse> snapshot = readSnapshot(normalizedSort);
        if (snapshot.isEmpty()) {
            refreshSnapshots("cache_miss");
            snapshot = readSnapshot(normalizedSort);
        }

        long total = snapshot.size();
        int from = Math.toIntExact(Math.min((normalizedPage - 1) * normalizedSize, total));
        int to = Math.toIntExact(Math.min(from + normalizedSize, total));
        return new PageResponse<>(snapshot.subList(from, to), total, normalizedPage, normalizedSize);
    }

    /**
     * 刷新全部排行榜快照
     * @param trigger 触发来源
     */
    public void refreshSnapshots(String trigger) {
        List<UserLeaderboardStats> stats = userMapper.selectList(new LambdaQueryWrapper<User>()
                        .eq(User::getStatus, "active")
                        .isNull(User::getDeletedAt))
                .stream()
                .map(this::toStats)
                .toList();

        SORTS.forEach(sort -> writeSnapshot(sort, buildSnapshot(stats, sort)));
        log.info("排行榜快照刷新完成，trigger={}, users={}", trigger, stats.size());
    }

    private List<LeaderboardEntryResponse> readSnapshot(String sort) {
        String raw = redisTemplate.opsForValue().get(cacheKey(sort));
        if (raw == null || raw.isBlank()) {
            return List.of();
        }

        try {
            return objectMapper.readValue(raw, new TypeReference<List<LeaderboardEntryResponse>>() {
            });
        } catch (JsonProcessingException exception) {
            log.warn("读取排行榜缓存失败，sort={}", sort, exception);
            return List.of();
        }
    }

    private void writeSnapshot(String sort, List<LeaderboardEntryResponse> snapshot) {
        try {
            redisTemplate.opsForValue().set(cacheKey(sort), objectMapper.writeValueAsString(snapshot), SNAPSHOT_TTL);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("排行榜快照序列化失败", exception);
        }
    }

    private List<LeaderboardEntryResponse> buildSnapshot(List<UserLeaderboardStats> stats, String sort) {
        List<UserLeaderboardStats> sorted = stats.stream()
                .sorted(comparator(sort))
                .toList();
        return IntStream.range(0, sorted.size())
                .mapToObj(index -> {
                    UserLeaderboardStats item = sorted.get(index);
                    return LeaderboardEntryResponse.from(
                            index + 1L,
                            item.user(),
                            item.publicDebateCount(),
                            item.endedPublicDebateCount()
                    );
                })
                .toList();
    }

    private UserLeaderboardStats toStats(User user) {
        userStatsService.recalculate(user.getId());
        User refreshed = userMapper.selectById(user.getId());
        long publicDebateCount = debateMapper.selectCount(publicDebates(user.getId()));
        long endedPublicDebateCount = debateMapper.selectCount(publicDebates(user.getId()).eq(Debate::getStatus, "ended"));
        return new UserLeaderboardStats(refreshed == null ? user : refreshed, publicDebateCount, endedPublicDebateCount);
    }

    private Comparator<UserLeaderboardStats> comparator(String sort) {
        Comparator<UserLeaderboardStats> base = switch (sort) {
            case "wins" -> Comparator.comparingInt(item -> safeInt(item.user().getWins()));
            case "debates" -> Comparator.comparingLong(UserLeaderboardStats::publicDebateCount);
            default -> Comparator.comparingInt(item -> safeInt(item.user().getPoints()));
        };

        return base
                .thenComparingInt(item -> safeInt(item.user().getWins()))
                .thenComparingLong(UserLeaderboardStats::endedPublicDebateCount)
                .thenComparing(item -> item.user().getCreatedAt(), Comparator.nullsLast(Comparator.naturalOrder()))
                .reversed();
    }

    private LambdaQueryWrapper<Debate> publicDebates(Long userId) {
        return new LambdaQueryWrapper<Debate>()
                .eq(Debate::getUserId, userId)
                .eq(Debate::getVisibility, "public")
                .isNull(Debate::getDeletedAt);
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    private String normalizeSort(String sort) {
        if (sort == null || sort.isBlank()) {
            return "points";
        }
        return switch (sort.trim()) {
            case "积分", "points" -> "points";
            case "胜场", "wins" -> "wins";
            case "辩论数", "debates" -> "debates";
            default -> throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "排行榜排序只能是 points、wins 或 debates");
        };
    }

    private String cacheKey(String sort) {
        return CACHE_KEY_PREFIX + sort;
    }

    private record UserLeaderboardStats(User user, long publicDebateCount, long endedPublicDebateCount) {
    }
}
