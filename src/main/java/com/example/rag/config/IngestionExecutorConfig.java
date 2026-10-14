package com.example.rag.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 文档解析是 IO + CPU 混合型任务，单独给它一个线程池，不和 Web 请求抢资源。
 *
 * <p>这里刻意用显式的 Executor 而不是 {@code @Async} 注解：
 * {@code @Async} 依赖 Spring 代理，同一个类里自己调自己会绕过代理、退化成同步执行，
 * 是个很常见的坑。显式提交任务后，真正干活的方法在另一个 Bean 上，
 * 事务代理也能正常生效。
 */
@Configuration
public class IngestionExecutorConfig {

    public static final String INGESTION_EXECUTOR = "ingestionExecutor";

    @Bean(INGESTION_EXECUTOR)
    public Executor ingestionExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("ingest-");
        // 队列满了就让提交任务的线程自己跑，保证任务不会静默丢失
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.initialize();
        return executor;
    }
}
