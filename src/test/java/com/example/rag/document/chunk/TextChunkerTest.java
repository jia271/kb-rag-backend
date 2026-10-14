package com.example.rag.document.chunk;

import com.example.rag.config.ChunkingProperties;
import com.example.rag.document.parse.ParsedDocument;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class TextChunkerTest {

    private static TextChunker chunker(int maxChars, int overlapChars, int minChars) {
        ChunkingProperties properties = new ChunkingProperties();
        properties.setMaxChars(maxChars);
        properties.setOverlapChars(overlapChars);
        properties.setMinChars(minChars);
        return new TextChunker(properties);
    }

    private static List<TextChunker.TextChunk> split(TextChunker chunker, String text) {
        return chunker.split(new ParsedDocument(
                List.of(new ParsedDocument.Segment(text, null, null)), 0, "text"));
    }

    @Test
    void everyChunkStaysWithinMaxChars() {
        TextChunker chunker = chunker(200, 40, 40);

        List<TextChunker.TextChunk> chunks = split(chunker, "这是一句用来测试分块的中文句子。".repeat(120));

        assertThat(chunks).isNotEmpty();
        assertThat(chunks).allSatisfy(chunk ->
                assertThat(chunk.content().length()).isLessThanOrEqualTo(200));
    }

    @Test
    void adjacentChunksShareOverlappingContext() {
        int overlapChars = 60;
        TextChunker chunker = chunker(200, overlapChars, 40);

        List<TextChunker.TextChunk> chunks = split(chunker, "这是第若干句话，用来把文本撑长一点。".repeat(80));

        assertThat(chunks.size()).isGreaterThan(1);
        String first = chunks.get(0).content();
        String second = chunks.get(1).content();
        String tailRegion = first.substring(Math.max(0, first.length() - overlapChars));

        boolean secondStartsWithTail = IntStream.range(0, tailRegion.length())
                .anyMatch(index -> second.startsWith(tailRegion.substring(index)));
        assertThat(secondStartsWithTail)
                .as("第二块开头应该来自第一块的尾部重叠区，否则跨块的问题会被切断")
                .isTrue();
    }

    @Test
    void paragraphBoundaryIsNeverCrossed() {
        TextChunker chunker = chunker(400, 50, 50);
        String paragraphA = "甲".repeat(150);
        String paragraphB = "乙".repeat(150);

        List<TextChunker.TextChunk> chunks = split(chunker, paragraphA + "\n\n" + paragraphB);

        assertThat(chunks).hasSize(2);
        assertThat(chunks.get(0).content()).doesNotContain("乙");
        assertThat(chunks.get(1).content()).doesNotContain("甲");
    }

    @Test
    void textWithoutPunctuationIsSplitByLength() {
        TextChunker chunker = chunker(200, 40, 40);

        List<TextChunker.TextChunk> chunks = split(chunker, "无".repeat(1000));

        assertThat(chunks.size()).isGreaterThanOrEqualTo(5);
        assertThat(chunks).allSatisfy(chunk ->
                assertThat(chunk.content().length()).isLessThanOrEqualTo(200));
    }

    @Test
    void chunkCarriesSourceLocator() {
        TextChunker chunker = chunker(200, 40, 40);
        ParsedDocument document = new ParsedDocument(
                List.of(new ParsedDocument.Segment("这一页的内容。", 3, "第一章 > 概述")), 5, "pdf");

        List<TextChunker.TextChunk> chunks = chunker.split(document);

        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).pageNumber()).isEqualTo(3);
        assertThat(chunks.get(0).heading()).isEqualTo("第一章 > 概述");
    }

    @Test
    void whitespaceIsNormalized() {
        TextChunker chunker = chunker(200, 40, 40);

        List<TextChunker.TextChunk> chunks = split(chunker, "前面    有多个　　空白");

        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).content()).isEqualTo("前面 有多个 空白");
    }

    @Test
    void shortTitleLineIsAttachedToTheFollowingParagraph() {
        TextChunker chunker = chunker(800, 120, 120);
        String text = "分块策略为什么重要\n\n切得太大，一个片段里会混着好几个主题。切得太小，上下文又不够用。";

        List<TextChunker.TextChunk> chunks = split(chunker, text);

        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).content()).startsWith("分块策略为什么重要");
        assertThat(chunks.get(0).content()).contains("切得太大");
        // 标题与正文之间必须保留换行，否则会被粘成一句读不通的话
        assertThat(chunks.get(0).content()).contains("分块策略为什么重要\n切得太大");
    }
}
