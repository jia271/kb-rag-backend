package com.example.rag.llm;

import com.example.rag.support.TraceContext;

/**
 * 一次模型调用的上下文，用于计量和排错。
 *
 * @param traceId   调用链追踪号
 * @param sessionId 会话标识，可为空
 * @param scene     调用场景：chat / stream
 */
public record LlmCallContext(String traceId, String sessionId, String scene) {

    /** 从当前线程的 MDC 里取追踪号，取不到就用占位符 */
    public static LlmCallContext of(String sessionId, String scene) {
        return new LlmCallContext(TraceContext.currentOrUnknown(), sessionId, scene);
    }
}
