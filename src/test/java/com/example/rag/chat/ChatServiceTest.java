package com.example.rag.chat;

import com.example.rag.config.ChatProperties;
import com.example.rag.config.LlmProperties;
import com.example.rag.llm.LlmGateway;
import com.example.rag.llm.LlmClientRegistry;
import com.example.rag.usage.UsageRecorder;
import com.example.rag.retrieval.HybridSearchService;
import com.example.rag.retrieval.SearchMode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 只验证"没有依据时不调用模型"这条关键分支。
 * 真正的生成链路靠端到端验证，这里不重复造模型替身。
 */
class ChatServiceTest {

    private final HybridSearchService search = mock(HybridSearchService.class);
    private final ChatProperties properties = new ChatProperties();
    private final ConversationStore conversations = new ConversationStore(properties);
    private final ChatService service = new ChatService(
            search,
            // 故意不给任何模型提供方：一旦代码走到调用模型那一步，测试就会失败
            new LlmGateway(new LlmClientRegistry(new LlmProperties(), new ObjectMapper()),
                    new LlmProperties(), mock(UsageRecorder.class)),
            new RagPromptBuilder(properties),
            new RelevanceGate(properties),
            conversations,
            properties);

    @Test
    void askReturnsFixedAnswerWithoutCallingModelWhenNothingRetrieved() throws Exception {
        when(search.search(anyString(), anyInt(), any())).thenReturn(List.of());

        AskResult result = service.ask("和文档无关的问题", null, null, "hybrid").block();

        assertThat(result).isNotNull();
        assertThat(result.answer()).isEqualTo(ChatService.NO_CONTEXT_ANSWER);
        assertThat(result.citations()).isEmpty();
        assertThat(result.sessionId()).isNotBlank();
    }

    @Test
    void streamEmitsCitationsThenCompletedWhenNothingRetrieved() throws Exception {
        when(search.search(anyString(), anyInt(), any())).thenReturn(List.of());

        List<ChatStreamEvent> events = service.stream("无关问题", "s1", 3, "vector").collectList().block();

        assertThat(events).hasSize(2);
        assertThat(events.get(0)).isInstanceOf(ChatStreamEvent.Citations.class);
        assertThat(events.get(1)).isInstanceOf(ChatStreamEvent.Completed.class);
        ChatStreamEvent.Completed completed = (ChatStreamEvent.Completed) events.get(1);
        assertThat(completed.sessionId()).isEqualTo("s1");
        assertThat(completed.answer()).isEqualTo(ChatService.NO_CONTEXT_ANSWER);
    }

    @Test
    void retrievalUsesRequestedTopKAndMode() throws Exception {
        when(search.search(anyString(), anyInt(), any())).thenReturn(List.of());

        service.ask("问题", null, 2, "keyword").block();

        verify(search).search("问题", 2, SearchMode.KEYWORD);
    }

    @Test
    void answerIsStoredInSessionHistory() throws Exception {
        when(search.search(anyString(), anyInt(), any())).thenReturn(List.of());

        service.ask("问题", "keep-me", null, "hybrid").block();

        assertThat(conversations.history("keep-me")).hasSize(2);
        assertThat(conversations.history("keep-me").get(0).content()).isEqualTo("问题");
        assertThat(conversations.history("keep-me").get(1).content())
                .isEqualTo(ChatService.NO_CONTEXT_ANSWER);
    }
}
