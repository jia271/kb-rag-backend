package com.example.rag.llm;

/**
 * 流式响应里的一个片段。
 *
 * <p>一次流式调用里，绝大多数片段只带文本；最后一个片段只带用量统计
 * （需要在请求里显式打开 stream_options.include_usage）。用同一个类型承载两者，
 * 调用方就不用为了拿 token 数去旁路探测。
 *
 * @param delta            增量文本，可能为 null
 * @param promptTokens     输入 token 数，仅最后一个片段有
 * @param completionTokens 输出 token 数，仅最后一个片段有
 */
public record StreamChunk(String delta, Integer promptTokens, Integer completionTokens) {

    public static StreamChunk text(String delta) {
        return new StreamChunk(delta, null, null);
    }

    public static StreamChunk usage(int promptTokens, int completionTokens) {
        return new StreamChunk(null, promptTokens, completionTokens);
    }

    public boolean hasText() {
        return delta != null && !delta.isEmpty();
    }

    public boolean hasUsage() {
        return promptTokens != null || completionTokens != null;
    }
}
