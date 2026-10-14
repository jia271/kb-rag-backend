package com.example.rag.embedding;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LocalHashingEmbeddingClientTest {

    private final LocalHashingEmbeddingClient client = new LocalHashingEmbeddingClient("local", 256);

    @Test
    void producesRequestedDimensionAndUnitLength() {
        float[] vector = client.embedOne("混合检索同时使用关键词和向量两种方式");

        assertThat(vector).hasSize(256);
        assertThat(norm(vector)).isCloseTo(1.0, org.assertj.core.data.Offset.offset(1e-4));
    }

    @Test
    void identicalTextProducesIdenticalVector() {
        float[] first = client.embedOne("向量检索理解语义");
        float[] second = client.embedOne("向量检索理解语义");

        assertThat(cosine(first, second)).isCloseTo(1.0, org.assertj.core.data.Offset.offset(1e-5));
    }

    @Test
    void overlappingTextIsCloserThanUnrelatedText() {
        float[] base = client.embedOne("混合检索同时使用关键词和向量两种方式，取长补短");
        float[] related = client.embedOne("混合检索使用关键词和向量两种方式");
        float[] unrelated = client.embedOne("公司的报销流程需要先提交申请再由主管审批");

        assertThat(cosine(base, related)).isGreaterThan(cosine(base, unrelated));
    }

    @Test
    void embedKeepsInputOrder() {
        List<float[]> vectors = client.embed(List.of("第一段", "第二段完全不同"));

        assertThat(vectors).hasSize(2);
        assertThat(vectors.get(0)).isEqualTo(client.embedOne("第一段"));
    }

    @Test
    void emptyTextIsHandled() {
        assertThat(norm(client.embedOne(""))).isEqualTo(0.0);
    }

    private double norm(float[] vector) {
        double sum = 0;
        for (float value : vector) {
            sum += (double) value * value;
        }
        return Math.sqrt(sum);
    }

    private double cosine(float[] left, float[] right) {
        double dot = 0;
        for (int i = 0; i < left.length; i++) {
            dot += (double) left[i] * right[i];
        }
        double norm = norm(left) * norm(right);
        return norm == 0 ? 0 : dot / norm;
    }
}
