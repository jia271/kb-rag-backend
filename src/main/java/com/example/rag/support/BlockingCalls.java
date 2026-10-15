package com.example.rag.support;

import org.slf4j.MDC;
import reactor.core.publisher.Mono;
import reactor.core.publisher.MonoSink;
import reactor.core.scheduler.Schedulers;

import java.util.concurrent.Callable;

/**
 * WebFlux 的请求线程不能被阻塞，而 JPA 与文件 IO 都是阻塞式的。
 * 统一把这类调用挪到弹性线程池执行，避免堵住事件循环。
 */
public final class BlockingCalls {

    private BlockingCalls() {
    }

    public static <T> Mono<T> call(Callable<T> action) {
        return Mono.<T>deferContextual(contextView -> Mono.create((MonoSink<T> sink) -> {
            // 追踪号存在 Reactor Context 里，进工作线程时手动还原到 MDC，
            // 否则日志里看不出这些行属于同一次请求
            String traceId = contextView.getOrDefault(TraceContext.KEY, TraceContext.UNKNOWN);
            Schedulers.boundedElastic().schedule(() -> {
                MDC.put(TraceContext.KEY, traceId);
                try {
                    sink.success(action.call());
                } catch (Throwable error) {
                    sink.error(error);
                } finally {
                    MDC.remove(TraceContext.KEY);
                }
            });
        }));
    }

    public static Mono<Void> run(Runnable action) {
        return call(() -> {
            action.run();
            return null;
        }).then();
    }
}
