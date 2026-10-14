package com.example.rag.document.parse;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * 按文件扩展名挑选解析器。新增格式时不用改这里。
 */
@Component
public class ParserRegistry {

    private final Map<String, DocumentParser> parsersByExtension = new LinkedHashMap<>();

    public ParserRegistry(List<DocumentParser> parsers) {
        for (DocumentParser parser : parsers) {
            for (String extension : parser.extensions()) {
                parsersByExtension.put(extension.toLowerCase(), parser);
            }
        }
    }

    public Optional<DocumentParser> find(String fileName) {
        String extension = extensionOf(fileName);
        return extension == null ? Optional.empty() : Optional.ofNullable(parsersByExtension.get(extension));
    }

    public Set<String> supportedExtensions() {
        return new TreeSet<>(parsersByExtension.keySet());
    }

    public static String extensionOf(String fileName) {
        if (fileName == null) {
            return null;
        }
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return null;
        }
        return fileName.substring(dot + 1).toLowerCase();
    }
}
