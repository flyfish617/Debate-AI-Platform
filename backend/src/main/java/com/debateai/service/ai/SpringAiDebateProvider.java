package com.debateai.service.ai;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.stream.Collectors;

/**
 * 基于Spring AI的辩论服务实现
 */
@Service
public class SpringAiDebateProvider implements AiProvider {

    private final ChatClient chatClient;
    private final boolean mockWhenApiKeyMissing;
    private final String apiKey;

    public SpringAiDebateProvider(
            ChatClient.Builder chatClientBuilder,
            @Value("${app.ai.mock-when-api-key-missing:true}") boolean mockWhenApiKeyMissing,
            @Value("${app.ai.real-api-key:}") String apiKey
    ) {
        this.chatClient = chatClientBuilder.build();
        this.mockWhenApiKeyMissing = mockWhenApiKeyMissing;
        this.apiKey = apiKey;
    }

    /**
     * 获取AI服务名称
     * @return 业务结果
     */
    @Override
    public String name() {
        return "spring-ai";
    }

    /**
     * 生成AI辩论回复
     * @param context
     * @return 业务结果
     */
    @Override
    public Flux<String> debate(DebateContext context) {
        if (shouldUseMock()) {
            return mockDebate(context);
        }

        return chatClient.prompt()
                .system(systemPrompt(context))
                .user(userPrompt(context))
                .stream()
                .content();
    }

    /**
     * 生成AI开篇立论
     * @param context
     * @return 业务结果
     */
    @Override
    public String openingStatement(DebateContext context) {
        if (shouldUseMock()) {
            return "围绕话题“" + context.topic() + "”，我将以" + stanceLabel(context.aiStance())
                    + "身份开始立论：先明确本方核心判断，再围绕事实、价值和可执行性展开论证。";
        }

        return chatClient.prompt()
                .system(systemPrompt(context))
                .user(openingPrompt(context))
                .call()
                .content();
    }

    /**
     * 生成AI结辩陈词
     * @param context
     * @return 业务结果
     */
    @Override
    public String closingStatement(DebateContext context) {
        if (shouldUseMock()) {
            return "围绕话题“" + context.topic() + "”，我的结辩是：" + stanceLabel(context.aiStance())
                    + "观点仍然更能解释问题的关键矛盾，也更具现实可操作性。";
        }

        return chatClient.prompt()
                .system(systemPrompt(context))
                .user("请为本场辩论生成 AI 的结辩陈词。必须使用中文称呼立场，例如正方或反方，不要输出 pro、con、mild、intense 等内部枚举。控制在 200-400 字。")
                .call()
                .content();
    }

    private boolean shouldUseMock() {
        return mockWhenApiKeyMissing && (apiKey == null || apiKey.isBlank());
    }

    private Flux<String> mockDebate(DebateContext context) {
        String text = "我理解你的观点，但我会从" + stanceLabel(context.aiStance())
                + "继续回应。第一，当前论点还需要更明确的证据支撑；第二，需要区分理想目标和现实约束；第三，如果把讨论落到执行层面，"
                + stanceLabel(context.aiStance()) + "方案在成本、风险和可持续性上更稳妥。";
        return Flux.fromArray(text.split(""))
                .delayElements(Duration.ofMillis(25));
    }

    private String systemPrompt(DebateContext context) {
        return """
                你是 DebateAI 平台中的 AI 辩手。
                本场辩论话题是：%s。
                你的立场是：%s。
                用户立场是：%s。
                本场辩论风格是：%s。
                必须坚持自己的立场，不允许中途改边。
                必须直接围绕本场辩论话题展开，不要写与话题无关的通用模板。
                回复应围绕论点、证据和逻辑，不做人身攻击。
                温和风格需要先承认对方合理处再反驳；激烈风格可以更直接，但不能攻击人格。
                全程使用中文称呼立场，例如正方或反方，不要输出 pro、con、mild、intense 等内部枚举。
                每次只输出一版完整回复，不要重复生成多个版本。
                每轮回复控制在 200-400 字。
                """.formatted(
                context.topic(),
                stanceLabel(context.aiStance()),
                stanceLabel(context.userStance()),
                styleLabel(context.style())
        );
    }

    private String userPrompt(DebateContext context) {
        String history = context.history().stream()
                .map(message -> roleLabel(message.role()) + ": " + message.content())
                .collect(Collectors.joining("\n"));

        return """
                话题：%s
                用户立场：%s
                AI 立场：%s
                风格：%s
                当前轮次：%d

                历史消息：
                %s

                请生成 AI 下一轮辩论回复。必须回应用户最近一轮观点，并且只输出一版完整回复。
                """.formatted(
                context.topic(),
                stanceLabel(context.userStance()),
                stanceLabel(context.aiStance()),
                styleLabel(context.style()),
                context.round(),
                history
        );
    }

    private String openingPrompt(DebateContext context) {
        return """
                请为本场辩论生成 AI 的开篇立论。
                话题：%s
                AI 立场：%s
                用户立场：%s
                风格：%s

                要求：
                1. 第一段就必须点明并围绕这个话题，不要写泛泛的辩论模板。
                2. 必须坚定站在 AI 立场，不要替用户立场论证。
                3. 必须使用中文称呼立场，例如正方或反方，不要输出 pro、con、mild、intense 等内部枚举。
                4. 只输出一版开篇立论，控制在 200-400 字。
                """.formatted(
                context.topic(),
                stanceLabel(context.aiStance()),
                stanceLabel(context.userStance()),
                styleLabel(context.style())
        );
    }

    private String stanceLabel(String stance) {
        return switch (stance) {
            case "pro", "正方" -> "正方";
            case "con", "反方" -> "反方";
            default -> stance == null || stance.isBlank() ? "未设置立场" : stance;
        };
    }

    private String styleLabel(String style) {
        return switch (style) {
            case "mild", "温和" -> "温和";
            case "intense", "激烈" -> "激烈";
            default -> style == null || style.isBlank() ? "温和" : style;
        };
    }

    private String roleLabel(String role) {
        return switch (role) {
            case "user" -> "用户";
            case "ai" -> "AI";
            case "system" -> "系统";
            default -> role == null || role.isBlank() ? "未知角色" : role;
        };
    }
}
