package com.example.rag.usage.api;

import com.example.rag.support.BlockingCalls;
import com.example.rag.usage.UsageRecorder;
import com.example.rag.usage.UsageSummary;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * 用量与成本查询。
 */
@RestController
@RequestMapping("/api/usage")
public class UsageController {

    private final UsageRecorder usageRecorder;

    public UsageController(UsageRecorder usageRecorder) {
        this.usageRecorder = usageRecorder;
    }

    @GetMapping("/summary")
    public Mono<UsageSummary> summary() {
        return BlockingCalls.call(usageRecorder::summary);
    }
}
