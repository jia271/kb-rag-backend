package com.example.rag.document.parse;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * 纯文本：自身没有结构，整份交给分块器按段落处理。
 */
@Component
public class PlainTextParser implements DocumentParser {

    @Override
    public Set<String> extensions() {
        return Set.of("txt", "text", "log", "csv");
    }

    @Override
    public String format() {
        return "text";
    }

    @Override
    public ParsedDocument parse(byte[] content, String fileName) {
        String text = TextDecoder.decode(content);
        List<ParsedDocument.Segment> segments = text.isBlank()
                ? List.of()
                : List.of(new ParsedDocument.Segment(text, null, null));
        return new ParsedDocument(segments, 0, format());
    }
}
