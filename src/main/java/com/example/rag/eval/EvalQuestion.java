package com.example.rag.eval;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * 一条评测样本。
 *
 * @param id         编号
 * @param question   提问
 * @param keywords   期望命中的片段里应当出现的关键词；切分为多个是为了容忍不同切法
 * @param answerable 知识库里是否真的有答案。不可回答的问题用来观察"检索是否也会硬凑出结果"
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record EvalQuestion(String id, String question, List<String> keywords, boolean answerable) {

    public EvalQuestion {
        keywords = keywords == null ? List.of() : List.copyOf(keywords);
    }
}
