package com.example.rag.usage;

import com.example.rag.config.LlmProperties;
import com.example.rag.llm.LlmCallContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 模型调用的计量与成本统计。
 */
@Slf4j
@Service
public class UsageRecorder {

    private final LlmUsageRepository repository;
    private final LlmProperties llmProperties;

    public UsageRecorder(LlmUsageRepository repository, LlmProperties llmProperties) {
        this.repository = repository;
        this.llmProperties = llmProperties;
    }

    /**
     * 记录一次调用。计量绝不能影响主流程：
     * 写库失败只记日志，不能把已经生成的答案搞丢。
     */
    public void record(LlmCallContext context,
                       String provider,
                       String model,
                       int promptTokens,
                       int completionTokens,
                       long latencyMillis,
                       boolean success,
                       String failureReason) {
        try {
            LlmUsage usage = new LlmUsage();
            usage.setTraceId(context == null ? null : context.traceId());
            usage.setSessionId(context == null ? null : context.sessionId());
            usage.setScene(context == null ? "unknown" : context.scene());
            usage.setProvider(provider);
            usage.setModel(model);
            usage.setPromptTokens(promptTokens);
            usage.setCompletionTokens(completionTokens);
            usage.setLatencyMillis(latencyMillis);
            usage.setSuccess(success);
            usage.setFailureReason(truncate(failureReason));
            usage.setCost(costOf(provider, promptTokens, completionTokens));
            repository.save(usage);
        } catch (Exception error) {
            log.warn("写入用量记录失败，已忽略：{}", error.toString());
        }
    }

    public double costOf(String provider, int promptTokens, int completionTokens) {
        LlmProperties.Provider config = llmProperties.getProviders().get(provider);
        if (config == null) {
            return 0.0;
        }
        return promptTokens / 1000.0 * config.getPricePer1kPrompt()
                + completionTokens / 1000.0 * config.getPricePer1kCompletion();
    }

    public UsageSummary summary() {
        List<UsageByModel> byModel = repository.summarizeByModel();
        long calls = 0;
        long promptTokens = 0;
        long completionTokens = 0;
        long latencyMillis = 0;
        double cost = 0.0;
        for (UsageByModel row : byModel) {
            calls += row.calls() == null ? 0 : row.calls();
            promptTokens += row.promptTokens() == null ? 0 : row.promptTokens();
            completionTokens += row.completionTokens() == null ? 0 : row.completionTokens();
            latencyMillis += row.latencyMillis() == null ? 0 : row.latencyMillis();
            cost += row.cost() == null ? 0.0 : row.cost();
        }
        double averageLatency = calls == 0 ? 0.0 : (double) latencyMillis / calls;
        return new UsageSummary(calls, repository.countBySuccessTrue(), promptTokens,
                completionTokens, cost, averageLatency, byModel);
    }

    private String truncate(String message) {
        if (message == null) {
            return null;
        }
        return message.length() <= 500 ? message : message.substring(0, 500);
    }
}
