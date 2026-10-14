package com.example.rag.chat.api.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * @param question  用户提问
 * @param sessionId 会话标识，不传则服务端新建一个；同一个 id 的多次提问会带上历史上下文
 * @param topK      检索多少个片段作为依据
 * @param mode      检索模式：keyword / vector / hybrid
 */
public record ChatRequest(
        @NotBlank(message = "提问不能为空") String question,
        String sessionId,
        Integer topK,
        String mode
) {
}
