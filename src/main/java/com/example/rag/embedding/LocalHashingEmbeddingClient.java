package com.example.rag.embedding;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 本地兜底向量化：把字符 n-gram 哈希到固定维度再做归一化。
 *
 * <p>它没有语义理解能力，只是把"字面重叠多的文本"映射到相近的向量上。
 * 存在的意义是让完整链路（索引、向量检索、混合排序）在没有任何外部密钥时也能跑通和演示。
 * 真实效果必须换成带语义的向量模型。
 */
public class LocalHashingEmbeddingClient implements EmbeddingClient {

    private static final int MAX_GRAM = 3;
    private static final float[] GRAM_WEIGHTS = {1.0f, 2.0f, 3.0f};

    private final String providerName;
    private final int dimensions;

    public LocalHashingEmbeddingClient(String providerName, int dimensions) {
        this.providerName = providerName;
        this.dimensions = Math.max(16, dimensions);
    }

    @Override
    public String providerName() {
        return providerName;
    }

    @Override
    public boolean available() {
        return true;
    }

    @Override
    public int dimensions() {
        return dimensions;
    }

    @Override
    public List<float[]> embed(List<String> texts) {
        List<float[]> vectors = new ArrayList<>(texts.size());
        for (String text : texts) {
            vectors.add(hash(text));
        }
        return vectors;
    }

    private float[] hash(String text) {
        float[] vector = new float[dimensions];
        String normalized = text == null ? "" : text.toLowerCase(Locale.ROOT);
        for (int gramSize = 1; gramSize <= MAX_GRAM; gramSize++) {
            float weight = GRAM_WEIGHTS[gramSize - 1];
            for (int start = 0; start + gramSize <= normalized.length(); start++) {
                int slot = Math.floorMod(normalized.substring(start, start + gramSize).hashCode(), dimensions);
                vector[slot] += weight;
            }
        }
        return l2Normalize(vector);
    }

    private float[] l2Normalize(float[] vector) {
        double sum = 0;
        for (float value : vector) {
            sum += (double) value * value;
        }
        double norm = Math.sqrt(sum);
        if (norm == 0) {
            return vector;
        }
        for (int i = 0; i < vector.length; i++) {
            vector[i] = (float) (vector[i] / norm);
        }
        return vector;
    }
}
