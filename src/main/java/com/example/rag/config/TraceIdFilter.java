package com.example.rag.config;

import com.example.rag.support.TraceContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * 给每个请求分配追踪号：优先用调用方传进来的 X-Trace-Id，没有就生成一个。
 *
 * <p>追踪号同时写进响应头（方便调用方拿着它去查日志）和 Reactor 的 Context
 * （方便下游在工作线程里还原）。
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter implements WebFilter {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String traceId = TraceContext.normalize(exchange.getRequest().getHeaders().getFirst(TraceContext.HEADER));
        exchange.getResponse().getHeaders().set(TraceContext.HEADER, traceId);
        log.debug("[{}] {} {}", traceId,
                exchange.getRequest().getMethod(), exchange.getRequest().getPath().value());
        return chain.filter(exchange)
                .contextWrite(context -> context.put(TraceContext.KEY, traceId));
    }
}
