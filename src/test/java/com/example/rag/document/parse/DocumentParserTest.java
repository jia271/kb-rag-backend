package com.example.rag.document.parse;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentParserTest {

    private final PlainTextParser plainTextParser = new PlainTextParser();
    private final MarkdownParser markdownParser = new MarkdownParser();
    private final PdfParser pdfParser = new PdfParser();

    @Test
    void plainTextBecomesOneSegment() {
        ParsedDocument document = plainTextParser.parse("第一段。\n\n第二段。".getBytes(StandardCharsets.UTF_8), "a.txt");

        assertThat(document.format()).isEqualTo("text");
        assertThat(document.segments()).hasSize(1);
        assertThat(document.segments().get(0).text()).contains("第一段。");
    }

    @Test
    void markdownRecordsHeadingPath() {
        String markdown = """
                # 第一章

                第一章的正文。

                ## 第一节

                第一节的正文。

                # 第二章

                第二章的正文。
                """;

        ParsedDocument document = markdownParser.parse(markdown.getBytes(StandardCharsets.UTF_8), "a.md");

        assertThat(document.segments()).hasSize(3);
        assertThat(document.segments().get(0).heading()).isEqualTo("第一章");
        assertThat(document.segments().get(1).heading()).isEqualTo("第一章 > 第一节");
        // 同级标题会顶掉之前的层级
        assertThat(document.segments().get(2).heading()).isEqualTo("第二章");
    }

    @Test
    void pdfIsExtractedPageByPage() throws IOException {
        byte[] pdf = samplePdf(3);

        ParsedDocument document = pdfParser.parse(pdf, "sample.pdf");

        assertThat(document.format()).isEqualTo("pdf");
        assertThat(document.pageCount()).isEqualTo(3);
        assertThat(document.segments()).hasSize(3);
        assertThat(document.segments().get(0).text()).contains("Page 1");
        assertThat(document.segments().get(2).pageNumber()).isEqualTo(3);
    }

    @Test
    void parserRegistryPicksParserByExtension() {
        ParserRegistry registry = new ParserRegistry(List.of(plainTextParser, markdownParser, pdfParser));

        assertThat(registry.find("说明.md")).contains(markdownParser);
        assertThat(registry.find("D:\\docs\\手册.PDF")).contains(pdfParser);
        assertThat(registry.find("没有扩展名")).isEmpty();
        assertThat(registry.supportedExtensions()).containsExactly("csv", "log", "markdown", "md", "pdf", "text", "txt");
    }

    private byte[] samplePdf(int pages) throws IOException {
        try (PDDocument document = new PDDocument()) {
            for (int page = 1; page <= pages; page++) {
                PDPage content = new PDPage();
                document.addPage(content);
                try (PDPageContentStream stream = new PDPageContentStream(document, content)) {
                    stream.beginText();
                    stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                    stream.newLineAtOffset(50, 700);
                    stream.showText("Page " + page + " content about retrieval.");
                    stream.endText();
                }
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        }
    }
}
