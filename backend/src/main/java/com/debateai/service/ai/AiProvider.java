package com.debateai.service.ai;

import reactor.core.publisher.Flux;

/**
 * AI辩手能力提供接口
 */
public interface AiProvider {

    String name();

    Flux<String> debate(DebateContext context);

    String openingStatement(DebateContext context);

    String closingStatement(DebateContext context);
}
