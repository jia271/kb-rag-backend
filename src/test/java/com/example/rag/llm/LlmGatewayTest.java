package com.example.rag.llm;

import com.example.rag.usage.UsageRecorder;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class LlmGatewayTest {

    private final UsageRecorder recorder = mock(UsageRecorder.class);
    private final LlmCallContext context = new LlmCallContext("trace-1", "session-1", "chat");

    @Test
    void chatFallsBackToNextProviderWhenFirstFails() {
        LlmGateway gateway = new LlmGateway(List.of(
                failing("broken", "连接超时"),
                working("backup")), recorder);

        ChatResult result = gateway.chat(List.of(ChatMessage.user("你好")), context).block();

        assertThat(result).isNotNull();
        assertThat(result.provider()).isEqualTo("backup");
        // 失败的也要记一笔：失败同样消耗时间和重试成本
        verify(recorder).record(eq(context), eq("broken"), any(), eq(0), eq(0),
                anyLong(), eq(false), any());
        verify(recorder).record(eq(context), eq("backup"), any(), anyInt(), anyInt(),
                anyLong(), eq(true), eq(null));
    }

    @Test
    void chatGivesClearErrorWhenAllProvidersFail() {
        LlmGateway gateway = new LlmGateway(List.of(
                failing("a", "挂了"),
                failing("b", "也挂了")), recorder);

        assertThatThrownBy(() -> gateway.chat(List.of(ChatMessage.user("你好")), context).block())
                .hasMessageContaining("所有模型提供方都调用失败");
    }

    @Test
    void streamFallsBackWhenNothingHasBeenEmittedYet() {
        LlmGateway gateway = new LlmGateway(List.of(
                new StubClient("broken", Mono.error(new IllegalStateException("挂了")),
                        Flux.error(new IllegalStateException("连接失败"))),
                new StubClient("backup", Mono.empty(),
                        Flux.just(StreamChunk.text("你"), StreamChunk.text("好"),
                                StreamChunk.usage(10, 2)))), recorder);

        List<String> texts = gateway.stream(List.of(ChatMessage.user("你好")), context)
                .filter(StreamChunk::hasText)
                .map(StreamChunk::delta)
                .collectList()
                .block();

        assertThat(texts).containsExactly("你", "好");
    }

    @Test
    void streamDoesNotSwitchProviderAfterPartialOutput() {
        LlmGateway gateway = new LlmGateway(List.of(
                // 先吐一个字再报错：这时不能换模型，否则用户会看到两段拼接的内容
                new StubClient("partial", Mono.empty(),
                        Flux.concat(Flux.just(StreamChunk.text("你")),
                                Flux.error(new IllegalStateException("中途断了")))),
                new StubClient("backup", Mono.empty(), Flux.just(StreamChunk.text("不该出现")))),
                recorder);

        assertThatThrownBy(() -> gateway.stream(List.of(ChatMessage.user("你好")), context)
                .collectList()
                .block())
                .hasMessageContaining("中途断了");

        verify(recorder).record(eq(context), eq("partial"), any(), eq(0), eq(0),
                anyLong(), eq(false), any());
    }

    @Test
    void streamRecordsTokensReturnedByProvider() {
        LlmGateway gateway = new LlmGateway(List.of(
                new StubClient("ok", Mono.empty(),
                        Flux.just(StreamChunk.text("答案"), StreamChunk.usage(120, 45)))), recorder);

        gateway.stream(List.of(ChatMessage.user("你好")), context).collectList().block();

        verify(recorder).record(eq(context), eq("ok"), any(), eq(120), eq(45),
                anyLong(), eq(true), eq(null));
    }

    private LlmClient failing(String name, String message) {
        return new StubClient(name, Mono.error(new IllegalStateException(message)),
                Flux.error(new IllegalStateException(message)));
    }

    private LlmClient working(String name) {
        return new StubClient(name,
                Mono.just(new ChatResult(name, name + "-model", "答案", 30, 12, 100)),
                Flux.just(StreamChunk.text("答案")));
    }

    private record StubClient(String name, Mono<ChatResult> chatResult, Flux<StreamChunk> streamResult)
            implements LlmClient {

        @Override
        public String providerName() {
            return name;
        }

        @Override
        public String modelName() {
            return name + "-model";
        }

        @Override
        public boolean available() {
            return true;
        }

        @Override
        public Mono<ChatResult> chat(List<ChatMessage> messages) {
            return chatResult;
        }

        @Override
        public Flux<StreamChunk> streamChat(List<ChatMessage> messages) {
            return streamResult;
        }
    }
}
