package com.example.rag.usage;

import com.example.rag.config.LlmProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;
import static org.mockito.Mockito.mock;

class UsageRecorderTest {

    @Test
    void costIsCalculatedFromConfiguredPrices() {
        LlmProperties properties = new LlmProperties();
        LlmProperties.Provider provider = new LlmProperties.Provider();
        provider.setPricePer1kPrompt(0.002);
        provider.setPricePer1kCompletion(0.008);
        properties.getProviders().put("deepseek", provider);

        UsageRecorder recorder = new UsageRecorder(mock(LlmUsageRepository.class), properties);

        // 1000 输入 token * 0.002 + 500 输出 token * 0.008 / 1000
        assertThat(recorder.costOf("deepseek", 1000, 500)).isCloseTo(0.002 + 0.004, offset(1e-9));
    }

    @Test
    void unknownProviderCostsZero() {
        UsageRecorder recorder = new UsageRecorder(mock(LlmUsageRepository.class), new LlmProperties());

        assertThat(recorder.costOf("nobody", 1000, 1000)).isZero();
    }

    @Test
    void zeroPricesProduceZeroCost() {
        LlmProperties properties = new LlmProperties();
        properties.getProviders().put("local", new LlmProperties.Provider());

        UsageRecorder recorder = new UsageRecorder(mock(LlmUsageRepository.class), properties);

        assertThat(recorder.costOf("local", 5000, 3000)).isZero();
    }
}
