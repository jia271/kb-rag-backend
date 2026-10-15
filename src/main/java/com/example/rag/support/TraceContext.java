package com.example.rag.support;

import org.slf4j.MDC;

import java.util.UUID;

/**
 * 调用链追踪号。
 *
 * <p>响应式链路有个容易忽略的坑：MDC 是 ThreadLocal，而请求会在线程之间跳。
 * 所以追踪号要显式放进 Reactor 的 Context 里传递，进入工作线程时再塞回 MDC，
 * 否则日志里根本串不起一次请求（详见 BlockingCalls）。
 */
public final class TraceContext {

    public static final String HEADER = "X-Trace-Id";
    public static final String KEY = "traceId";
    public static final String UNKNOWN = "--------";

    private TraceContext() {
    }

    public static String generate() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    /** 外部传进来的追踪号要清洗，别让奇怪字符污染日志 */
    public static String normalize(String candidate) {
        if (candidate == null || candidate.isBlank()) {
            return generate();
        }
        String cleaned = candidate.trim().replaceAll("[^A-Za-z0-9._-]", "_");
        return cleaned.length() <= 64 ? cleaned : cleaned.substring(0, 64);
    }

    public static String current() {
        return MDC.get(KEY);
    }

    public static String currentOrUnknown() {
        String traceId = current();
        return traceId == null || traceId.isBlank() ? UNKNOWN : traceId;
    }
}
