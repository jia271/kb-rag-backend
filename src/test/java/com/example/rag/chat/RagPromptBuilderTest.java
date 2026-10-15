package com.example.rag.chat;

import com.example.rag.config.ChatProperties;
import com.example.rag.llm.ChatMessage;
import com.example.rag.retrieval.SearchHit;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RagPromptBuilderTest {

    private final ChatProperties properties = new ChatProperties();
    private final RagPromptBuilder builder = new RagPromptBuilder(properties);

    @Test
    void sourcesAreNumberedAndLocated() {
        List<ChatMessage> messages = builder.build("报销怎么走？",
                List.of(hit("报销需要先提交申请。", "员工手册.md", "第三章 > 报销", 3)),
                List.of());

        assertThat(messages).hasSize(2);
        assertThat(messages.get(0).role()).isEqualTo(ChatMessage.ROLE_SYSTEM);
        assertThat(messages.get(0).content()).contains("只能依据下面提供的资料回答问题");

        String userMessage = messages.get(1).content();
        assertThat(userMessage).contains("[1] 来源：员工手册.md / 第三章 > 报销 / 第 3 页");
        assertThat(userMessage).contains("报销需要先提交申请。");
        assertThat(userMessage).endsWith("问题：报销怎么走？");
    }

    @Test
    void historySitsBetweenSystemPromptAndCurrentQuestion() {
        List<ChatMessage> messages = builder.build("那需要几天？",
                List.of(hit("内容", "a.md", null, null)),
                List.of(ChatMessage.user("报销怎么走？"), ChatMessage.assistant("先提交申请 [1]")));

        assertThat(messages).hasSize(4);
        assertThat(messages.get(1).content()).isEqualTo("报销怎么走？");
        assertThat(messages.get(2).content()).isEqualTo("先提交申请 [1]");
        assertThat(messages.get(3).content()).contains("问题：那需要几天？");
    }

    @Test
    void noHitsIsMarkedExplicitly() {
        String userMessage = builder.build("随便问问", List.of(), List.of()).get(1).content();

        assertThat(userMessage).startsWith("资料：（无）");
    }

    @Test
    void contextIsTrimmedToTheConfiguredBudget() {
        properties.setMaxContextChars(50);
        List<ChatMessage> messages = builder.build("问题",
                List.of(
                        hit("甲".repeat(40), "a.md", null, null),
                        hit("乙".repeat(40), "b.md", null, null)),
                List.of());

        String userMessage = messages.get(1).content();
        assertThat(userMessage).contains("[1] 来源：a.md");
        assertThat(userMessage).doesNotContain("[2]");
    }

    private SearchHit hit(String content, String fileName, String heading, Integer pageNumber) {
        return new SearchHit(1L, 2L, fileName, 0, pageNumber, heading, content, 1, 1, 4.0, 0.5, 0.03);
    }
}
