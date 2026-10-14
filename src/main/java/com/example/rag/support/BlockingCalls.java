package com.example.rag.support;

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
        return Mono.create((MonoSink<T> sink) -> Schedulers.boundedElastic().schedule(() -> {
            try {
                sink.success(action.call());
            } catch (Throwable error) {
                sink.error(error);
            }
        }));
    }

    public static Mono<Void> run(Runnable action) {
        return call(() -> {
            action.run();
            return null;
        }).then();
    }
}
