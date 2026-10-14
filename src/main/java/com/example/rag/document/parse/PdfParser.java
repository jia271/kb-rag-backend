package com.example.rag.document.parse;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * PDF：按页提取文本，每页保留页码。
 * 逐页提取（而不是一次提全文）才能知道每段文字来自第几页。
 */
@Component
public class PdfParser implements DocumentParser {

    @Override
    public Set<String> extensions() {
        return Set.of("pdf");
    }

    @Override
    public String format() {
        return "pdf";
    }

    @Override
    public ParsedDocument parse(byte[] content, String fileName) throws IOException {
        List<ParsedDocument.Segment> segments = new ArrayList<>();
        try (PDDocument document = Loader.loadPDF(content)) {
            int pageCount = document.getNumberOfPages();
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            for (int page = 1; page <= pageCount; page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                String text = stripper.getText(document);
                if (text != null && !text.isBlank()) {
                    segments.add(new ParsedDocument.Segment(text, page, null));
                }
            }
            if (segments.isEmpty()) {
                throw new IllegalStateException(
                        "该 PDF 提取不到任何文字，可能是扫描件（图片型 PDF），需要 OCR 才能处理");
            }
            return new ParsedDocument(segments, pageCount, format());
        }
    }
}
