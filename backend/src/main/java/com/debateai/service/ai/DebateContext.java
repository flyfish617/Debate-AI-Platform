package com.debateai.service.ai;

import java.util.List;

/**
 * AI辩论上下文
 */
public record DebateContext(
        long debateId,
        String topic,
        String userStance,
        String aiStance,
        String style,
        int round,
        List<DebateMessage> history
) {
}
