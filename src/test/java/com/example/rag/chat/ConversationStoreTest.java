package com.example.rag.chat;

import com.example.rag.config.ChatProperties;
import com.example.rag.llm.ChatMessage;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ConversationStoreTest {

    @Test
    void unknownSessionHasEmptyHistory() {
        assertThat(new ConversationStore(new ChatProperties()).history("missing")).isEmpty();
    }

    @Test
    void appendedTurnsKeepOrder() {
        ConversationStore store = new ConversationStore(new ChatProperties());

        store.append("s1", "第一个问题", "第一个回答");
        store.append("s1", "第二个问题", "第二个回答");

        List<ChatMessage> history = store.history("s1");
        assertThat(history).hasSize(4);
        assertThat(history.get(0).content()).isEqualTo("第一个问题");
        assertThat(history.get(3).content()).isEqualTo("第二个回答");
    }

    @Test
    void sessionsAreIsolated() {
        ConversationStore store = new ConversationStore(new ChatProperties());

        store.append("s1", "问题一", "回答一");

        assertThat(store.history("s2")).isEmpty();
    }

    @Test
    void oldestTurnIsDroppedWhenTurnLimitExceeded() {
        ChatProperties properties = new ChatProperties();
        properties.setMaxHistoryTurns(2);
        properties.setMaxHistoryChars(100_000);
        ConversationStore store = new ConversationStore(properties);

        store.append("s", "问题一", "回答一");
        store.append("s", "问题二", "回答二");
        store.append("s", "问题三", "回答三");

        List<ChatMessage> history = store.history("s");
        assertThat(history).hasSize(4);
        // 整轮丢弃，不会留下孤立的回答
        assertThat(history.get(0).content()).isEqualTo("问题二");
        assertThat(history.get(1).role()).isEqualTo(ChatMessage.ROLE_ASSISTANT);
    }

    @Test
    void oldestTurnIsDroppedWhenCharBudgetExceeded() {
        ChatProperties properties = new ChatProperties();
        properties.setMaxHistoryTurns(10);
        // 每轮 6 个字符，预算 12 个字符：恰好能留下两轮
        properties.setMaxHistoryChars(12);
        ConversationStore store = new ConversationStore(properties);

        store.append("s", "问题一", "回答一");
        store.append("s", "问题二", "回答二");
        store.append("s", "问题三", "回答三");

        List<ChatMessage> history = store.history("s");
        assertThat(history).hasSize(4);
        assertThat(history.get(0).content()).isEqualTo("问题二");
    }
}
