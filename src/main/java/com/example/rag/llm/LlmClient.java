package com.example.rag.llm;

import reactor.core.publisher.Mono;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * 模型客户端抽象。后面接第二家模型时，只需要多一个实现。
 */
public interface LlmClient {

    String providerName();

    /** 密钥等必要配置是否就绪 */
    boolean available();

    Mono<ChatResult> chat(List<ChatMessage> messages);

    /** 流式对话：逐段返回增量文本，最后一段没有内容时代表结束 */
    Flux<String> streamChat(List<ChatMessage> messages);

    default Mono<ChatResult> chat(String prompt) {
        return chat(List.of(ChatMessage.user(prompt)));
    }
}
