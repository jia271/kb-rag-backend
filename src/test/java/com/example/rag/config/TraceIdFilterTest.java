package com.example.rag.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * 追踪号必须真的出现在响应头里，否则调用方拿不到它去查日志。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@ActiveProfiles("test")
class TraceIdFilterTest {

    @Autowired
    private WebTestClient webTestClient;

    @Test
    void generatesTraceIdWhenCallerDoesNotProvideOne() {
        webTestClient.get().uri("/actuator/health")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().exists("X-Trace-Id");
    }

    @Test
    void reusesTraceIdFromCaller() {
        webTestClient.get().uri("/actuator/health")
                .header("X-Trace-Id", "my-trace-001")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals("X-Trace-Id", "my-trace-001");
    }

    @Test
    void sanitizesTraceIdFromCaller() {
        webTestClient.get().uri("/actuator/health")
                .header("X-Trace-Id", "bad id with spaces")
                .exchange()
                .expectHeader().valueEquals("X-Trace-Id", "bad_id_with_spaces");
    }
}
