package com.example.rag.llm;

import reactor.core.publisher.Mono;

/**
 * 模型客户端抽象。后面接第二家模型时，只需要多一个实现。
 */
public interface LlmClient {

    String providerName();

    /** 密钥等必要配置是否就绪 */
    boolean available();

    Mono<ChatResult> chat(String prompt);
}
