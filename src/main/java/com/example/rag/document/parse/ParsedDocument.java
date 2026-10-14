package com.example.rag.document.parse;

import java.util.List;

/**
 * 解析结果：一段段带来源定位的文本。
 * 这里的 segment 只做粗切（一页、一节），细切交给 TextChunker。
 */
public record ParsedDocument(List<Segment> segments, int pageCount, String format) {

    /**
     * @param text       该段的文本
     * @param pageNumber 来源页码，仅 PDF 有
     * @param heading    来源标题路径，仅 Markdown 有
     */
    public record Segment(String text, Integer pageNumber, String heading) {
    }

    public int totalChars() {
        return segments.stream().mapToInt(segment -> segment.text().length()).sum();
    }
}
