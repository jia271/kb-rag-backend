package com.example.rag.retrieval;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RrfFusionTest {

    @Test
    void itemFoundByBothPathsOutranksItemFoundByOne() {
        Map<String, Double> fused = RrfFusion.fuse(60, List.of(
                List.of("x", "y"),
                List.of("y", "z")));

        // y 在两路都被召回，即使都不是第一，融合后也应该排最前
        assertThat(fused.get("y")).isGreaterThan(fused.get("x"));
        assertThat(fused.get("x")).isGreaterThan(fused.get("z"));
    }

    @Test
    void scoreDependsOnRankNotOnRawScore() {
        Map<String, Double> fused = RrfFusion.fuse(60, List.of(List.of("first", "second")));

        assertThat(fused.get("first")).isCloseTo(1.0 / 61, org.assertj.core.data.Offset.offset(1e-9));
        assertThat(fused.get("second")).isCloseTo(1.0 / 62, org.assertj.core.data.Offset.offset(1e-9));
    }

    @Test
    void emptyListsProduceEmptyResult() {
        assertThat(RrfFusion.fuse(60, List.of(List.of(), List.of()))).isEmpty();
    }
}
