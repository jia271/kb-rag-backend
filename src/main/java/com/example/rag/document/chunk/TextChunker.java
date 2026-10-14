package com.example.rag.document.chunk;

import com.example.rag.config.ChunkingProperties;
import com.example.rag.document.parse.ParsedDocument;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 分块器：把解析出来的段落切成适合检索的片段。
 *
 * <p>策略是"结构优先、长度兜底"：
 * <ol>
 *   <li>纯文本里的标题行（短、单独成段、没有句末标点）先并到下一段，
 *       否则会切出"只有六个字"的碎片，检索时毫无价值；</li>
 *   <li>先按空行切段落，段落边界永远不跨块——一个块里混两个主题，检索出来就是噪音；</li>
 *   <li>段落内按句号、问号、换行等句子边界累加，凑到目标长度就切一刀；</li>
 *   <li>相邻块之间保留一段重叠，防止答案正好被切在边界上；</li>
 *   <li>过短的尾块在放得下的前提下并回前一块；放不下就单独成块——
 *       宁可留一个小块，也不能丢内容。</li>
 * </ol>
 */
@Component
public class TextChunker {

    /** 空行分段 */
    private static final Pattern BLANK_LINE = Pattern.compile("\\n\\s*\\n+");

    /** 句子边界：在这些符号之后切，切点保留在句子末尾 */
    private static final Pattern SENTENCE_BOUNDARY = Pattern.compile("(?<=[。！？!?；;\\n])");

    private static final Pattern HORIZONTAL_SPACE = Pattern.compile("[ \\t\\x0B\\f\\u00A0\\u3000]+");

    private static final char[] SENTENCE_MARKS = {'。', '！', '？', '!', '?', '；', ';', '\n'};

    /** 超过这个长度就不太可能是标题行 */
    private static final int MAX_HEADING_CHARS = 40;

    private final ChunkingProperties properties;

    public TextChunker(ChunkingProperties properties) {
        this.properties = properties;
    }

    /**
     * @param content   块内容
     * @param pageNumber 来源页码，仅 PDF 有
     * @param heading   来源标题路径，仅 Markdown 有
     */
    public record TextChunk(String content, Integer pageNumber, String heading) {
    }

    public List<TextChunk> split(ParsedDocument document) {
        List<TextChunk> chunks = new ArrayList<>();
        for (ParsedDocument.Segment segment : document.segments()) {
            String text = normalize(segment.text());
            if (text.isBlank()) {
                continue;
            }
            for (String block : attachHeadingsToNextBlock(paragraphsOf(text))) {
                for (String piece : splitBlock(block)) {
                    chunks.add(new TextChunk(piece, segment.pageNumber(), segment.heading()));
                }
            }
        }
        return chunks;
    }

    private List<String> paragraphsOf(String text) {
        List<String> blocks = new ArrayList<>();
        for (String block : BLANK_LINE.split(text)) {
            String trimmed = block.strip();
            if (!trimmed.isEmpty()) {
                blocks.add(trimmed);
            }
        }
        return blocks;
    }

    /**
     * 把标题行并到它后面那一段。纯文本里的标题往往自己占一个段落，
     * 单独切出来只有几个字，既检索不到也说明不了问题；带上正文才有意义。
     */
    private List<String> attachHeadingsToNextBlock(List<String> blocks) {
        List<String> merged = new ArrayList<>(blocks.size());
        String pendingHeading = null;
        for (String block : blocks) {
            if (isHeadingLike(block)) {
                pendingHeading = pendingHeading == null ? block : pendingHeading + "\n" + block;
                continue;
            }
            merged.add(pendingHeading == null ? block : pendingHeading + "\n" + block);
            pendingHeading = null;
        }
        // 连续标题后面没有正文时保底留成一块，不能丢内容
        if (pendingHeading != null) {
            merged.add(pendingHeading);
        }
        return merged;
    }

    private boolean isHeadingLike(String block) {
        return block.length() <= MAX_HEADING_CHARS
                && !block.contains("\n")
                && !containsSentenceMark(block);
    }

    private boolean containsSentenceMark(String text) {
        for (int i = 0; i < text.length(); i++) {
            char current = text.charAt(i);
            for (char mark : SENTENCE_MARKS) {
                if (current == mark) {
                    return true;
                }
            }
        }
        return false;
    }

    private List<String> splitBlock(String block) {
        int maxChars = Math.max(1, properties.getMaxChars());
        int overlapChars = Math.max(0, properties.getOverlapChars());
        // 硬切长度要预留出重叠的空间，否则加了重叠就可能超过上限
        int hardLimit = Math.max(1, maxChars - overlapChars);

        List<String> chunks = new ArrayList<>();
        StringBuilder buffer = new StringBuilder();

        for (String sentence : SENTENCE_BOUNDARY.split(block)) {
            for (String piece : hardSplit(sentence, hardLimit)) {
                if (piece.isEmpty()) {
                    continue;
                }
                if (buffer.length() > 0 && buffer.length() + piece.length() > maxChars) {
                    String finished = buffer.toString().strip();
                    if (!finished.isEmpty()) {
                        chunks.add(finished);
                    }
                    buffer.setLength(0);
                    buffer.append(overlapTail(finished, overlapChars));
                }
                buffer.append(piece);
            }
        }
        String tail = buffer.toString().strip();
        if (!tail.isEmpty()) {
            chunks.add(tail);
        }
        return mergeShortTail(chunks);
    }

    /** 一整句都超长（比如没有标点的长行）时，只能按长度硬切 */
    private List<String> hardSplit(String text, int limit) {
        if (text.isBlank()) {
            return List.of();
        }
        // 只去掉行首空白，保留行尾的换行符：
        // 换行是句子边界，丢了它标题和正文会粘成一句读不通的话
        String trimmed = text.stripLeading();
        if (trimmed.length() <= limit) {
            return List.of(trimmed);
        }
        List<String> parts = new ArrayList<>();
        for (int start = 0; start < trimmed.length(); start += limit) {
            parts.add(trimmed.substring(start, Math.min(trimmed.length(), start + limit)));
        }
        return parts;
    }

    /** 取上一块末尾作为重叠内容，并尽量从句子边界开始，避免断句 */
    private String overlapTail(String chunk, int overlapChars) {
        if (overlapChars <= 0 || chunk.isEmpty()) {
            return "";
        }
        int overlap = Math.min(overlapChars, chunk.length());
        String tail = chunk.substring(chunk.length() - overlap);
        int boundary = lastSentenceBoundary(tail);
        if (boundary >= 0 && boundary < tail.length() - 1) {
            tail = tail.substring(boundary + 1);
        }
        return tail.strip();
    }

    private int lastSentenceBoundary(String text) {
        for (int i = text.length() - 1; i >= 0; i--) {
            char current = text.charAt(i);
            for (char mark : SENTENCE_MARKS) {
                if (current == mark) {
                    return i;
                }
            }
        }
        return -1;
    }

    private List<String> mergeShortTail(List<String> chunks) {
        if (chunks.size() < 2) {
            return chunks;
        }
        String last = chunks.get(chunks.size() - 1);
        if (last.length() >= properties.getMinChars()) {
            return chunks;
        }
        String previous = chunks.get(chunks.size() - 2);
        if (previous.length() + last.length() + 1 <= properties.getMaxChars()) {
            chunks.set(chunks.size() - 2, previous + "\n" + last);
            chunks.remove(chunks.size() - 1);
        }
        return chunks;
    }

    private String normalize(String text) {
        if (text == null) {
            return "";
        }
        String unified = text.replace("\r\n", "\n").replace('\r', '\n');
        return HORIZONTAL_SPACE.matcher(unified).replaceAll(" ").strip();
    }
}
