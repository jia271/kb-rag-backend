package com.example.rag.document.parse;

import java.util.Set;

/**
 * 文档解析器：把一种格式的文件变成纯文本段落。
 * 后面要加 Word、HTML，只需要多一个实现类。
 */
public interface DocumentParser {

    /** 支持的文件扩展名，小写，不带点 */
    Set<String> extensions();

    /** 展示用的格式名 */
    String format();

    ParsedDocument parse(byte[] content, String fileName) throws Exception;
}
