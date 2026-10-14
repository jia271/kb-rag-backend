package com.example.rag.chat;

import com.example.rag.retrieval.SearchHit;

import java.util.ArrayList;
import java.util.List;

/**
 * 引用来源。编号与提示词里资料前的 [1]、[2] 一一对应，
 * 这样模型在答案里标注的编号能直接被前端还原成出处。
 */
public record Citation(
        int index,
        long chunkId,
        long documentId,
        String fileName,
        Integer pageNumber,
        String heading,
        double score,
        String snippet
) {

    private static final int SNIPPET_CHARS = 160;

    public static List<Citation> from(List<SearchHit> hits) {
        List<Citation> citations = new ArrayList<>(hits.size());
        for (int position = 0; position < hits.size(); position++) {
            SearchHit hit = hits.get(position);
            citations.add(new Citation(
                    position + 1,
                    hit.chunkId(),
                    hit.documentId(),
                    hit.fileName(),
                    hit.pageNumber(),
                    hit.heading(),
                    hit.score(),
                    snippet(hit.content())));
        }
        return citations;
    }

    private static String snippet(String content) {
        String flat = content.replaceAll("\\s+", " ").strip();
        return flat.length() <= SNIPPET_CHARS ? flat : flat.substring(0, SNIPPET_CHARS) + "…";
    }
}
