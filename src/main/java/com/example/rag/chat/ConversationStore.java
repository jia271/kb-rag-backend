package com.example.rag.chat;

import com.example.rag.config.ChatProperties;
import com.example.rag.llm.ChatMessage;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 会话上下文，存在单机内存里。
 *
 * <p>为什么要裁剪：大模型的上下文长度有限，而且历史越长单次调用越贵。
 * 这里按"轮次"和"字符数"双重上限裁剪，从最早的一轮开始丢。
 *
 * <p>已知取舍：进程重启就清空，多实例部署也共享不了。
 * 要做得更正式，应该把会话落到 Redis 或数据库里。
 */
@Component
public class ConversationStore {

    private final ChatProperties properties;
    private final Map<String, Deque<ChatMessage>> sessions;

    public ConversationStore(ChatProperties properties) {
        this.properties = properties;
        this.sessions = Collections.synchronizedMap(new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Deque<ChatMessage>> eldest) {
                return size() > properties.getMaxSessions();
            }
        });
    }

    public List<ChatMessage> history(String sessionId) {
        Deque<ChatMessage> turns = sessions.get(sessionId);
        if (turns == null) {
            return List.of();
        }
        synchronized (turns) {
            return new ArrayList<>(turns);
        }
    }

    public void append(String sessionId, String question, String answer) {
        Deque<ChatMessage> turns = sessions.computeIfAbsent(sessionId, key -> new ArrayDeque<>());
        synchronized (turns) {
            turns.addLast(ChatMessage.user(question));
            turns.addLast(ChatMessage.assistant(answer));
            trim(turns);
        }
    }

    public int sessionCount() {
        return sessions.size();
    }

    /** 轮次和字符数任一超标就丢最早的一轮，整轮丢，不拆散问答对 */
    private void trim(Deque<ChatMessage> turns) {
        int maxMessages = Math.max(2, properties.getMaxHistoryTurns() * 2);
        while (turns.size() > 2
                && (turns.size() > maxMessages || charsOf(turns) > properties.getMaxHistoryChars())) {
            turns.removeFirst();
            turns.removeFirst();
        }
    }

    private int charsOf(Deque<ChatMessage> turns) {
        int total = 0;
        for (ChatMessage message : turns) {
            total += message.content().length();
        }
        return total;
    }
}
