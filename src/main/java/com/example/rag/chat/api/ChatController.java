package com.example.rag.chat.api;

import com.example.rag.chat.ChatService;
import com.example.rag.chat.ChatStreamEvent;
import com.example.rag.chat.api.dto.AskResponse;
import com.example.rag.chat.api.dto.ChatRequest;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 问答接口。
 *
 * <p>`/api/chat/stream` 用 SSE 逐步推送：先给引用来源，再逐段给答案，最后给统计信息。
 * 前端可以用 fetch 读取流，也可以通过网关转发给浏览器。
 */
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<ChatStreamEvent>> stream(@Valid @RequestBody ChatRequest request) {
        return chatService.stream(request.question(), request.sessionId(), request.topK(), request.mode())
                .map(event -> ServerSentEvent.builder(event).event(event.eventName()).build());
    }

    /** 一次性返回完整答案，方便测试与脚本调用 */
    @PostMapping
    public Mono<AskResponse> ask(@Valid @RequestBody ChatRequest request) {
        return chatService.ask(request.question(), request.sessionId(), request.topK(), request.mode())
                .map(AskResponse::from);
    }
}
