package com.example.rag.document.parse;

import org.commonmark.node.Heading;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.text.TextContentRenderer;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Markdown：按标题层级切段，并给每段记下"来源标题路径"。
 * 这样回答问题时可以说清楚答案出自哪一节，而不是只给个文件名。
 */
@Component
public class MarkdownParser implements DocumentParser {

    private static final String HEADING_SEPARATOR = " > ";

    private final Parser parser = Parser.builder().build();
    private final TextContentRenderer renderer = TextContentRenderer.builder().build();

    @Override
    public Set<String> extensions() {
        return Set.of("md", "markdown");
    }

    @Override
    public String format() {
        return "markdown";
    }

    @Override
    public ParsedDocument parse(byte[] content, String fileName) {
        String markdown = TextDecoder.decode(content);
        Node root = parser.parse(markdown);

        List<ParsedDocument.Segment> segments = new ArrayList<>();
        List<HeadingNode> openHeadings = new ArrayList<>();

        for (Node node = root.getFirstChild(); node != null; node = node.getNext()) {
            if (node instanceof Heading heading) {
                trackHeading(openHeadings, heading);
                continue;
            }
            String text = renderer.render(node);
            if (!text.isBlank()) {
                segments.add(new ParsedDocument.Segment(text, null, currentPath(openHeadings)));
            }
        }
        return new ParsedDocument(segments, 0, format());
    }

    /** 维护标题栈：同级或更高级的标题会顶掉旧的标题 */
    private void trackHeading(List<HeadingNode> openHeadings, Heading heading) {
        while (!openHeadings.isEmpty()
                && openHeadings.get(openHeadings.size() - 1).level() >= heading.getLevel()) {
            openHeadings.remove(openHeadings.size() - 1);
        }
        openHeadings.add(new HeadingNode(heading.getLevel(), renderer.render(heading).strip()));
    }

    private String currentPath(List<HeadingNode> openHeadings) {
        if (openHeadings.isEmpty()) {
            return null;
        }
        return String.join(HEADING_SEPARATOR, openHeadings.stream().map(HeadingNode::title).toList());
    }

    private record HeadingNode(int level, String title) {
    }
}
