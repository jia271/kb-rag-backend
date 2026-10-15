package com.example.rag.chat;

import java.util.List;

/**
 * 流式问答过程中的事件。顺序固定为：
 * citations（先给出依据，用户立刻看到出处）→ delta（逐段文本）→ completed（收尾统计）。
 */
public interface ChatStreamEvent {

    String eventName();

    record Citations(List<Citation> citations) implements ChatStreamEvent {
        @Override
        public String eventName() {
            return "citations";
        }
    }

    record Delta(String text) implements ChatStreamEvent {
        @Override
        public String eventName() {
            return "delta";
        }
    }

    record Completed(String answer, long firstTokenMillis, long tookMillis, String sessionId,
                     int promptTokens, int completionTokens) implements ChatStreamEvent {
        @Override
        public String eventName() {
            return "completed";
        }
    }

    record Error(String message) implements ChatStreamEvent {
        @Override
        public String eventName() {
            return "error";
        }
    }
}
