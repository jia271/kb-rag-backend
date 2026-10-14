package com.example.rag.embedding;

import java.util.List;

/**
 * 向量化客户端。
 *
 * <p>刻意做成同步接口：向量化本质是一次请求-响应，没有流式场景，
 * 硬套响应式只会让调用方到处写订阅。真正需要异步的地方由调用方决定。
 */
public interface EmbeddingClient {

    String providerName();

    /** 是否具备可用条件（例如是否配了密钥） */
    boolean available();

    int dimensions();

    /** 批量向量化，返回顺序与入参一一对应 */
    List<float[]> embed(List<String> texts);

    default float[] embedOne(String text) {
        return embed(List.of(text)).get(0);
    }
}
