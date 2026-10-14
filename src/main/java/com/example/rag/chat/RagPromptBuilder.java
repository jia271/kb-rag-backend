package com.example.rag.chat;

import com.example.rag.config.ChatProperties;
import com.example.rag.llm.ChatMessage;
import com.example.rag.retrieval.SearchHit;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 组装提示词。这是 RAG 里最影响答案质量的一步，规则必须写死写清楚：
 * 只依据资料作答、没有依据就明说、引用要标编号。
 */
@Component
public class RagPromptBuilder {

    private static final String SYSTEM_PROMPT = """
            你是一个企业知识库助手，只能依据下面提供的资料回答问题。

            必须遵守的规则：
            1. 只使用资料中的信息作答，不要用你自己的知识补充或推测；
            2. 资料中没有相关内容时，直接回答"知识库中没有找到相关内容"，不要编造；
            3. 引用资料时用方括号标注编号，例如 [1]、[2]，编号对应资料前的序号；
            4. 用简体中文回答，条理清晰，不要复述全部资料。
            """;

    private final ChatProperties properties;

    public RagPromptBuilder(ChatProperties properties) {
        this.properties = properties;
    }

    public List<ChatMessage> build(String question, List<SearchHit> hits, List<ChatMessage> history) {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(ChatMessage.system(SYSTEM_PROMPT));
        messages.addAll(history);
        messages.add(ChatMessage.user(userPrompt(question, hits)));
        return messages;
    }

    String userPrompt(String question, List<SearchHit> hits) {
        StringBuilder prompt = new StringBuilder();
        List<SearchHit> usable = withinBudget(hits);

        if (usable.isEmpty()) {
            prompt.append("资料：（无）\n\n");
        } else {
            prompt.append("资料：\n");
            for (int position = 0; position < usable.size(); position++) {
                SearchHit hit = usable.get(position);
                prompt.append('[').append(position + 1).append("] 来源：")
                        .append(locate(hit)).append('\n')
                        .append(hit.content()).append("\n\n");
            }
        }
        prompt.append("问题：").append(question);
        return prompt.toString();
    }

    /** 按字符预算截断资料，宁可少给几条，也不要把上下文撑爆 */
    private List<SearchHit> withinBudget(List<SearchHit> hits) {
        List<SearchHit> usable = new ArrayList<>();
        int budget = Math.max(0, properties.getMaxContextChars());
        int used = 0;
        for (SearchHit hit : hits) {
            int length = hit.content().length();
            if (!usable.isEmpty() && used + length > budget) {
                break;
            }
            usable.add(hit);
            used += length;
        }
        return usable;
    }

    private String locate(SearchHit hit) {
        StringBuilder location = new StringBuilder(hit.fileName());
        if (hit.heading() != null && !hit.heading().isBlank()) {
            location.append(" / ").append(hit.heading());
        }
        if (hit.pageNumber() != null) {
            location.append(" / 第 ").append(hit.pageNumber()).append(" 页");
        }
        return location.toString();
    }
}
