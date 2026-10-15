package com.example.rag.chat;

import com.example.rag.config.ChatProperties;
import com.example.rag.retrieval.SearchHit;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RelevanceGateTest {

    @Test
    void disabledByDefault() {
        assertThat(new RelevanceGate(new ChatProperties()).filter(List.of(hit(0.1)))).hasSize(1);
    }

    @Test
    void keepsResultsAboveThreshold() {
        assertThat(gateWithThreshold(3.0).filter(List.of(hit(4.8), hit(2.0)))).hasSize(2);
    }

    @Test
    void dropsEverythingWhenBestScoreIsBelowThreshold() {
        assertThat(gateWithThreshold(3.0).filter(List.of(hit(1.2), hit(0.8)))).isEmpty();
    }

    @Test
    void ignoresHitsWithoutKeywordScore() {
        SearchHit vectorOnly = new SearchHit(9L, 1L, "a.md", 0, null, null, "内容",
                null, 1, null, 0.62, 0.62);

        assertThat(gateWithThreshold(3.0).filter(List.of(vectorOnly))).isEmpty();
    }

    @Test
    void emptyInputStaysEmpty() {
        assertThat(gateWithThreshold(3.0).filter(List.of())).isEmpty();
    }

    private RelevanceGate gateWithThreshold(double threshold) {
        ChatProperties properties = new ChatProperties();
        properties.setMinKeywordScore(threshold);
        return new RelevanceGate(properties);
    }

    private SearchHit hit(double keywordScore) {
        return new SearchHit(1L, 1L, "a.md", 0, null, null, "内容",
                1, 1, keywordScore, 0.5, 0.03);
    }
}
