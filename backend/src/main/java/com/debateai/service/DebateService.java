package com.debateai.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.debateai.common.ApiException;
import com.debateai.config.AsyncQueueConfig;
import com.debateai.dto.CreateDebateRequest;
import com.debateai.dto.CreateDebateResponse;
import com.debateai.dto.DebateClosingMessage;
import com.debateai.dto.DebateDetailResponse;
import com.debateai.dto.DebateSummaryResponse;
import com.debateai.dto.EndDebateResponse;
import com.debateai.dto.MessageResponse;
import com.debateai.dto.PageResponse;
import com.debateai.dto.SendMessageRequest;
import com.debateai.dto.TopicResponse;
import com.debateai.entity.Debate;
import com.debateai.entity.Message;
import com.debateai.entity.Topic;
import com.debateai.entity.Vote;
import com.debateai.mapper.DebateMapper;
import com.debateai.mapper.MessageMapper;
import com.debateai.mapper.TopicMapper;
import com.debateai.mapper.VoteMapper;
import com.debateai.service.ai.AiProvider;
import com.debateai.service.ai.DebateContext;
import com.debateai.service.ai.DebateMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import reactor.core.publisher.Flux;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 辩论业务服务
 */
@Service
public class DebateService {

    private static final Logger log = LoggerFactory.getLogger(DebateService.class);
    private static final long MAX_PAGE_SIZE = 50;
    private static final int MIN_MAX_ROUNDS = 5;
    private static final int DEFAULT_MAX_ROUNDS = 5;
    private static final int MAX_MAX_ROUNDS = 15;
    private static final int HISTORY_SUMMARY_THRESHOLD = 20;
    private static final int RECENT_HISTORY_LIMIT = 10;
    private static final int SUMMARY_ITEM_LIMIT = 4;
    private static final int SUMMARY_SNIPPET_LENGTH = 120;
    private static final String CLOSING_PENDING_CONTENT = "辩论已结束，AI 结辩正在生成中，请稍后刷新查看。";
    private static final String AI_FAILED_CONTENT = "AI 回复生成失败，请稍后重试。";
    private static final String CLOSING_FAILED_CONTENT = "AI 结辩生成失败，请稍后重试。";

    private final DebateMapper debateMapper;
    private final MessageMapper messageMapper;
    private final TopicMapper topicMapper;
    private final VoteMapper voteMapper;
    private final AiProvider aiProvider;
    private final UserStatsService userStatsService;
    private final RabbitTemplate rabbitTemplate;
    private final String aiModel;

    public DebateService(
            DebateMapper debateMapper,
            MessageMapper messageMapper,
            TopicMapper topicMapper,
            VoteMapper voteMapper,
            AiProvider aiProvider,
            UserStatsService userStatsService,
            RabbitTemplate rabbitTemplate,
            @Value("${spring.ai.openai.chat.options.model:deepseek-chat}") String aiModel
    ) {
        this.debateMapper = debateMapper;
        this.messageMapper = messageMapper;
        this.topicMapper = topicMapper;
        this.voteMapper = voteMapper;
        this.aiProvider = aiProvider;
        this.userStatsService = userStatsService;
        this.rabbitTemplate = rabbitTemplate;
        this.aiModel = aiModel;
    }

    /**
     * 获取公开辩论列表
     * @param page 页码
     * @param size 每页数量
     * @param status 状态
     * @return 分页结果
     */
    public PageResponse<DebateSummaryResponse> listPublic(long page, long size, String status) {
        long normalizedPage = Math.max(page, 1);
        long normalizedSize = Math.max(1, Math.min(size, MAX_PAGE_SIZE));
        long offset = (normalizedPage - 1) * normalizedSize;

        LambdaQueryWrapper<Debate> wrapper = new LambdaQueryWrapper<Debate>()
                .eq(Debate::getVisibility, "public")
                .isNull(Debate::getDeletedAt)
                .orderByDesc(Debate::getCreatedAt)
                .orderByDesc(Debate::getId);

        if (status != null && !status.isBlank()) {
            wrapper.eq(Debate::getStatus, normalizeStatus(status));
        }

        long total = debateMapper.selectCount(wrapper);
        List<DebateSummaryResponse> list = debateMapper.selectList(wrapper.last("LIMIT " + offset + ", " + normalizedSize)).stream()
                .map(debate -> DebateSummaryResponse.from(debate, TopicResponse.from(requireVisibleTopic(debate.getTopicId()))))
                .toList();

        return new PageResponse<>(list, total, normalizedPage, normalizedSize);
    }

    /**
     * 创建辩论
     * @param request 创建请求
     * @param userId 用户ID
     * @return 创建结果
     */
    @Transactional
    public CreateDebateResponse create(CreateDebateRequest request, Long userId) {
        Topic topic = requireVisibleTopic(request.topicId());
        String userStance = normalizeStance(request.userStance());
        String aiStance = "pro".equals(userStance) ? "con" : "pro";
        String style = normalizeStyle(request.style());
        String visibility = normalizeVisibility(request.visibility());
        int maxRounds = normalizeMaxRounds(request.maxRounds());

        Debate debate = new Debate();
        debate.setTopicId(topic.getId());
        debate.setUserId(userId);
        debate.setUserStance(userStance);
        debate.setAiStance(aiStance);
        debate.setAiModel(aiModel);
        debate.setStyle(style);
        debate.setVisibility(visibility);
        debate.setStatus("active");
        debate.setCurrentRound(1);
        debate.setMaxRounds(maxRounds);
        debate.setUserVoteCount(0);
        debate.setAiVoteCount(0);
        debateMapper.insert(debate);

        long startedAt = System.currentTimeMillis();
        String content = aiProvider.openingStatement(new DebateContext(
                debate.getId(),
                topicForPrompt(topic),
                userStance,
                aiStance,
                style,
                1,
                List.of()
        ));

        Message message = new Message();
        message.setDebateId(debate.getId());
        message.setRole("ai");
        message.setContent(content);
        message.setRound(1);
        message.setAiProvider(aiProvider.name());
        message.setAiModel(aiModel);
        message.setLatencyMs((int) (System.currentTimeMillis() - startedAt));
        message.setStatus("complete");
        messageMapper.insert(message);

        topic.setDebateCount((topic.getDebateCount() == null ? 0 : topic.getDebateCount()) + 1);
        topicMapper.updateById(topic);

        return new CreateDebateResponse(
                debate.getId(),
                topic.getId(),
                userStance,
                aiStance,
                style,
                visibility,
                maxRounds,
                MessageResponse.from(message)
        );
    }

    /**
     * 获取辩论详情
     * @param id 辩论ID
     * @param userId 当前用户ID
     * @return 详情
     */
    public DebateDetailResponse detail(Long id, Long userId) {
        Debate debate = requireReadableDebate(id, userId);
        Topic topic = requireVisibleTopic(debate.getTopicId());
        List<MessageResponse> messages = listMessages(debate.getId()).stream()
                .map(MessageResponse::from)
                .toList();
        String viewerVote = userId == null ? null : currentVote(debate.getId(), userId);

        return DebateDetailResponse.from(debate, TopicResponse.from(topic), messages, viewerVote);
    }

    /**
     * 保存用户发言
     * @param debateId 辩论ID
     * @param userId 用户ID
     * @param request 发送请求
     * @return 用户消息
     */
    @Transactional
    public MessageResponse saveUserMessage(Long debateId, Long userId, SendMessageRequest request) {
        Debate debate = requireOwnedActiveDebate(debateId, userId);
        Message message = new Message();
        message.setDebateId(debate.getId());
        message.setUserId(userId);
        message.setRole("user");
        message.setContent(request.content().trim());
        message.setRound(nextRound(debate));
        message.setStatus("complete");
        messageMapper.insert(message);
        return MessageResponse.from(message);
    }

    /**
     * 流式生成AI回复
     * @param debateId 辩论ID
     * @param userId 用户ID
     * @return AI回复流
     */
    public Flux<String> streamAiReply(Long debateId, Long userId) {
        Debate debate = requireOwnedActiveDebate(debateId, userId);
        Message userMessage = latestUserMessage(debate.getId(), userId);
        return streamAiReplyForUserMessage(debate, userMessage);
    }

    /**
     * 重试AI回复
     * @param debateId 辩论ID
     * @param userId 用户ID
     * @param userMessageId 用户消息ID
     * @return AI回复流
     */
    public Flux<String> retryAiReply(Long debateId, Long userId, Long userMessageId) {
        Debate debate = requireOwnedActiveDebate(debateId, userId);
        Message userMessage = requireRetryableUserMessage(debate.getId(), userId, userMessageId);
        return streamAiReplyForUserMessage(debate, userMessage);
    }

    /**
     * 手动结束辩论并异步生成结辩
     * @param debateId 辩论ID
     * @param userId 用户ID
     * @return 结束响应
     */
    @Transactional
    public EndDebateResponse end(Long debateId, Long userId) {
        Debate debate = requireOwnedActiveDebate(debateId, userId);
        Message pendingMessage = startClosing(debate, "manual");
        return new EndDebateResponse(
                debate.getId(),
                debate.getStatus(),
                debate.getWinner(),
                MessageResponse.from(pendingMessage),
                debate.getEndedAt()
        );
    }

    /**
     * 保存AI回复
     * @param debateId 辩论ID
     * @param userId 用户ID
     * @param content 回复内容
     * @param startedAt 开始时间戳
     * @return AI消息
     */
    @Transactional
    public MessageResponse saveAiReply(Long debateId, Long userId, String content, long startedAt) {
        Debate debate = requireOwnedActiveDebate(debateId, userId);
        int round = nextRound(debate);
        return saveAiReplyForRound(debate, content, startedAt, round, "complete");
    }

    /**
     * 保存重试后的AI回复
     * @param debateId 辩论ID
     * @param userId 用户ID
     * @param userMessageId 用户消息ID
     * @param content 回复内容
     * @param startedAt 开始时间戳
     * @return AI消息
     */
    @Transactional
    public MessageResponse saveRetriedAiReply(Long debateId, Long userId, Long userMessageId, String content, long startedAt) {
        Debate debate = requireOwnedActiveDebate(debateId, userId);
        Message userMessage = requireRetryableUserMessage(debate.getId(), userId, userMessageId);
        return saveAiReplyForRound(debate, content, startedAt, userMessage.getRound(), "complete");
    }

    /**
     * 保存失败的AI回复
     * @param debateId 辩论ID
     * @param userId 用户ID
     * @param userMessageId 用户消息ID
     * @param errorMessage 错误内容
     * @param startedAt 开始时间戳
     * @return AI失败消息
     */
    @Transactional
    public MessageResponse saveFailedAiReply(Long debateId, Long userId, Long userMessageId, String errorMessage, long startedAt) {
        Debate debate = requireOwnedActiveDebate(debateId, userId);
        Message userMessage = requireOwnedUserMessage(debate.getId(), userId, userMessageId);
        String content = errorMessage == null || errorMessage.isBlank() ? AI_FAILED_CONTENT : errorMessage;
        return saveAiReplyForRound(debate, content, startedAt, userMessage.getRound(), "failed");
    }

    /**
     * 由MQ消费者生成AI结辩
     * @param debateId 辩论ID
     * @param trigger 触发来源
     */
    @Transactional
    public void generateClosingStatement(Long debateId, String trigger) {
        Debate debate = debateMapper.selectById(debateId);
        if (debate == null || debate.getDeletedAt() != null || !"ending".equals(debate.getStatus())) {
            return;
        }

        Message pendingMessage = latestPendingClosingMessage(debate.getId());
        int round = pendingMessage == null ? nextRound(debate) : pendingMessage.getRound();
        Topic topic = requireVisibleTopic(debate.getTopicId());
        List<Message> messages = listMessages(debate.getId()).stream()
                .filter(message -> pendingMessage == null || !pendingMessage.getId().equals(message.getId()))
                .toList();

        long startedAt = System.currentTimeMillis();
        try {
            String content = aiProvider.closingStatement(new DebateContext(
                    debate.getId(),
                    topicForPrompt(topic),
                    debate.getUserStance(),
                    debate.getAiStance(),
                    debate.getStyle(),
                    round,
                    buildContextHistory(messages)
            ));

            Message closingMessage = pendingMessage == null ? new Message() : pendingMessage;
            closingMessage.setDebateId(debate.getId());
            closingMessage.setRole("ai");
            closingMessage.setContent(content);
            closingMessage.setRound(round);
            closingMessage.setAiProvider(aiProvider.name());
            closingMessage.setAiModel(aiModel);
            closingMessage.setLatencyMs((int) (System.currentTimeMillis() - startedAt));
            closingMessage.setStatus("complete");
            if (closingMessage.getId() == null) {
                messageMapper.insert(closingMessage);
            } else {
                messageMapper.updateById(closingMessage);
            }

            LocalDateTime endedAt = LocalDateTime.now();
            debate.setCurrentRound(round);
            debate.setStatus("ended");
            debate.setWinner(resolveWinner(debate));
            debate.setEndedAt(endedAt);
            debateMapper.updateById(debate);
            userStatsService.recalculate(debate.getUserId());
            log.info("AI结辩生成完成，debateId={}, trigger={}", debateId, trigger);
        } catch (RuntimeException exception) {
            markClosingFailed(debate, pendingMessage, round, exception);
        }
    }

    private Flux<String> streamAiReplyForUserMessage(Debate debate, Message userMessage) {
        Topic topic = requireVisibleTopic(debate.getTopicId());
        List<Message> messages = listMessagesUpToRound(debate.getId(), userMessage.getRound());

        DebateContext context = new DebateContext(
                debate.getId(),
                topicForPrompt(topic),
                debate.getUserStance(),
                debate.getAiStance(),
                debate.getStyle(),
                userMessage.getRound(),
                buildContextHistory(messages)
        );

        return aiProvider.debate(context);
    }

    private MessageResponse saveAiReplyForRound(Debate debate, String content, long startedAt, int round, String status) {
        Message message = new Message();
        message.setDebateId(debate.getId());
        message.setRole("ai");
        message.setContent(content == null || content.isBlank() ? AI_FAILED_CONTENT : content);
        message.setRound(round);
        message.setAiProvider(aiProvider.name());
        message.setAiModel(aiModel);
        message.setLatencyMs((int) (System.currentTimeMillis() - startedAt));
        message.setStatus(status);
        messageMapper.insert(message);

        if ("complete".equals(status) && (debate.getCurrentRound() == null || debate.getCurrentRound() < round)) {
            debate.setCurrentRound(round);
            if (round >= normalizeMaxRounds(debate.getMaxRounds())) {
                startClosing(debate, "max_rounds");
            } else {
                debateMapper.updateById(debate);
            }
        }

        return MessageResponse.from(message);
    }

    private Message startClosing(Debate debate, String trigger) {
        if (!"active".equals(debate.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "DEBATE_NOT_ACTIVE", "辩论已经结束或正在结辩");
        }

        int round = nextRound(debate);
        Message pendingMessage = new Message();
        pendingMessage.setDebateId(debate.getId());
        pendingMessage.setRole("ai");
        pendingMessage.setContent(CLOSING_PENDING_CONTENT);
        pendingMessage.setRound(round);
        pendingMessage.setAiProvider(aiProvider.name());
        pendingMessage.setAiModel(aiModel);
        pendingMessage.setStatus("pending");
        messageMapper.insert(pendingMessage);

        debate.setCurrentRound(Math.max(debate.getCurrentRound() == null ? 1 : debate.getCurrentRound(), round - 1));
        debate.setStatus("ending");
        debateMapper.updateById(debate);
        publishClosingAfterCommit(debate.getId(), debate.getUserId(), trigger);
        return pendingMessage;
    }

    private void publishClosingAfterCommit(Long debateId, Long userId, String trigger) {
        DebateClosingMessage message = new DebateClosingMessage(debateId, userId, trigger);
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            publishClosing(message);
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                publishClosing(message);
            }
        });
    }

    private void publishClosing(DebateClosingMessage message) {
        rabbitTemplate.convertAndSend(
                AsyncQueueConfig.DEBATE_EXCHANGE,
                AsyncQueueConfig.DEBATE_CLOSING_ROUTING_KEY,
                message
        );
    }

    private void markClosingFailed(Debate debate, Message pendingMessage, int round, RuntimeException exception) {
        Message failedMessage = pendingMessage == null ? new Message() : pendingMessage;
        failedMessage.setDebateId(debate.getId());
        failedMessage.setRole("ai");
        failedMessage.setContent(CLOSING_FAILED_CONTENT);
        failedMessage.setRound(round);
        failedMessage.setAiProvider(aiProvider.name());
        failedMessage.setAiModel(aiModel);
        failedMessage.setStatus("failed");
        if (failedMessage.getId() == null) {
            messageMapper.insert(failedMessage);
        } else {
            messageMapper.updateById(failedMessage);
        }

        debate.setStatus("failed");
        debateMapper.updateById(debate);
        log.warn("AI结辩生成失败，debateId={}", debate.getId(), exception);
    }

    private Message latestPendingClosingMessage(Long debateId) {
        return messageMapper.selectList(new LambdaQueryWrapper<Message>()
                        .eq(Message::getDebateId, debateId)
                        .eq(Message::getRole, "ai")
                        .eq(Message::getStatus, "pending")
                        .orderByDesc(Message::getRound)
                        .orderByDesc(Message::getId)
                        .last("LIMIT 1"))
                .stream()
                .findFirst()
                .orElse(null);
    }

    private Debate requireReadableDebate(Long id, Long userId) {
        Debate debate = debateMapper.selectById(id);
        if (debate == null || debate.getDeletedAt() != null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "辩论不存在");
        }
        if (!"public".equals(debate.getVisibility()) && (userId == null || !debate.getUserId().equals(userId))) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "无权查看该辩论");
        }
        return debate;
    }

    private Debate requireOwnedActiveDebate(Long id, Long userId) {
        Debate debate = requireReadableDebate(id, userId);
        if (!debate.getUserId().equals(userId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "只能在自己的辩论中发言");
        }
        if (!"active".equals(debate.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "DEBATE_NOT_ACTIVE", "辩论已经结束或不可继续");
        }
        return debate;
    }

    private Topic requireVisibleTopic(Long topicId) {
        Topic topic = topicMapper.selectById(topicId);
        if (topic == null || topic.getDeletedAt() != null || !"visible".equals(topic.getStatus())) {
            throw new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "话题不存在");
        }
        return topic;
    }

    private String topicForPrompt(Topic topic) {
        String description = topic.getDescription();
        if (description == null || description.isBlank()) {
            return topic.getTitle();
        }
        return topic.getTitle() + "\n话题补充说明：" + description.trim();
    }

    private List<Message> listMessages(Long debateId) {
        return messageMapper.selectList(new LambdaQueryWrapper<Message>()
                .eq(Message::getDebateId, debateId)
                .orderByAsc(Message::getRound)
                .orderByAsc(Message::getCreatedAt)
                .orderByAsc(Message::getId));
    }

    private List<Message> listMessagesUpToRound(Long debateId, Integer round) {
        return messageMapper.selectList(new LambdaQueryWrapper<Message>()
                .eq(Message::getDebateId, debateId)
                .le(Message::getRound, round)
                .and(wrapper -> wrapper.eq(Message::getStatus, "complete").or().eq(Message::getRole, "user"))
                .orderByAsc(Message::getRound)
                .orderByAsc(Message::getCreatedAt)
                .orderByAsc(Message::getId));
    }

    private Message latestUserMessage(Long debateId, Long userId) {
        Message message = messageMapper.selectList(new LambdaQueryWrapper<Message>()
                        .eq(Message::getDebateId, debateId)
                        .eq(Message::getUserId, userId)
                        .eq(Message::getRole, "user")
                        .orderByDesc(Message::getRound)
                        .orderByDesc(Message::getId)
                        .last("LIMIT 1"))
                .stream()
                .findFirst()
                .orElse(null);
        if (message == null) {
            throw new ApiException(HttpStatus.CONFLICT, "MESSAGE_NOT_FOUND", "未找到可回复的用户发言");
        }
        return message;
    }

    private Message requireRetryableUserMessage(Long debateId, Long userId, Long userMessageId) {
        Message message = requireOwnedUserMessage(debateId, userId, userMessageId);
        Long completedAiCount = messageMapper.selectCount(new LambdaQueryWrapper<Message>()
                .eq(Message::getDebateId, debateId)
                .eq(Message::getRole, "ai")
                .eq(Message::getRound, message.getRound())
                .eq(Message::getStatus, "complete"));
        if (completedAiCount > 0) {
            throw new ApiException(HttpStatus.CONFLICT, "AI_REPLY_EXISTS", "这一轮AI已经完成回复，无需重试");
        }
        return message;
    }

    private Message requireOwnedUserMessage(Long debateId, Long userId, Long userMessageId) {
        Message message = messageMapper.selectById(userMessageId);
        if (message == null
                || !debateId.equals(message.getDebateId())
                || !userId.equals(message.getUserId())
                || !"user".equals(message.getRole())) {
            throw new ApiException(HttpStatus.NOT_FOUND, "MESSAGE_NOT_FOUND", "用户发言不存在");
        }
        return message;
    }

    private List<DebateMessage> buildContextHistory(List<Message> messages) {
        if (messages.size() <= HISTORY_SUMMARY_THRESHOLD) {
            return messages.stream()
                    .filter(message -> !"pending".equals(message.getStatus()))
                    .map(this::toDebateMessage)
                    .toList();
        }

        int recentStart = Math.max(1, messages.size() - RECENT_HISTORY_LIMIT);
        List<Message> middleMessages = messages.subList(1, recentStart);
        List<Message> recentMessages = messages.subList(recentStart, messages.size());

        List<DebateMessage> context = new java.util.ArrayList<>();
        context.add(toDebateMessage(messages.get(0)));
        context.add(new DebateMessage("system", summarizeHistory(middleMessages)));
        context.addAll(recentMessages.stream()
                .filter(message -> !"pending".equals(message.getStatus()))
                .map(this::toDebateMessage)
                .toList());
        return context;
    }

    private DebateMessage toDebateMessage(Message message) {
        return new DebateMessage(message.getRole(), message.getContent());
    }

    private String summarizeHistory(List<Message> messages) {
        List<String> userPoints = summarizeRole(messages, "user");
        List<String> aiPoints = summarizeRole(messages, "ai");

        return "历史摘要：这部分内容由较早的辩论消息压缩生成，用于保持上下文连续。"
                + "用户此前主要提出：" + joinSummary(userPoints)
                + "AI 此前主要回应：" + joinSummary(aiPoints)
                + "请继续围绕这些已出现的争点推进，不要把摘要当作新的用户发言。";
    }

    private List<String> summarizeRole(List<Message> messages, String role) {
        return messages.stream()
                .filter(message -> role.equals(message.getRole()))
                .filter(message -> !"pending".equals(message.getStatus()))
                .map(Message::getContent)
                .filter(content -> content != null && !content.isBlank())
                .map(content -> truncate(content.trim(), SUMMARY_SNIPPET_LENGTH))
                .limit(SUMMARY_ITEM_LIMIT)
                .toList();
    }

    private String joinSummary(List<String> points) {
        if (points.isEmpty()) {
            return "暂无；";
        }
        return String.join("；", points) + "；";
    }

    private String currentVote(Long debateId, Long userId) {
        Vote vote = voteMapper.selectOne(new LambdaQueryWrapper<Vote>()
                .eq(Vote::getDebateId, debateId)
                .eq(Vote::getVoterId, userId));
        return vote == null ? null : vote.getVotedFor();
    }

    private int nextRound(Debate debate) {
        return (debate.getCurrentRound() == null ? 0 : debate.getCurrentRound()) + 1;
    }

    private String resolveWinner(Debate debate) {
        int userVotes = debate.getUserVoteCount() == null ? 0 : debate.getUserVoteCount();
        int aiVotes = debate.getAiVoteCount() == null ? 0 : debate.getAiVoteCount();
        if (userVotes > aiVotes) {
            return "user";
        }
        if (aiVotes > userVotes) {
            return "ai";
        }
        return "draw";
    }

    private String normalizeStance(String stance) {
        if (stance == null || stance.isBlank()) {
            return "pro";
        }
        return switch (stance.trim()) {
            case "反方", "con" -> "con";
            default -> "pro";
        };
    }

    private String normalizeStyle(String style) {
        if (style == null || style.isBlank()) {
            return "mild";
        }
        return switch (style.trim()) {
            case "激烈", "intense" -> "intense";
            default -> "mild";
        };
    }

    private String normalizeVisibility(String visibility) {
        if (visibility == null || visibility.isBlank()) {
            return "public";
        }
        return switch (visibility.trim()) {
            case "私密", "private" -> "private";
            default -> "public";
        };
    }

    private int normalizeMaxRounds(Integer maxRounds) {
        if (maxRounds == null) {
            return DEFAULT_MAX_ROUNDS;
        }
        return Math.min(Math.max(maxRounds, MIN_MAX_ROUNDS), MAX_MAX_ROUNDS);
    }

    private String normalizeStatus(String status) {
        return switch (status.trim()) {
            case "进行中", "active" -> "active";
            case "结辩中", "ending" -> "ending";
            case "已结束", "ended" -> "ended";
            case "失败", "failed" -> "failed";
            default -> status.trim();
        };
    }

    private String truncate(String value, int maxLength) {
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength) + "...";
    }
}
