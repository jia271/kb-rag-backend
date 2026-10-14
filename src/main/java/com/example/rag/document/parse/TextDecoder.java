package com.example.rag.document.parse;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

/**
 * 文本编码判定。
 *
 * <p>中文文档的现实是：同一批文件里可能混着 UTF-8、GBK、带 BOM 的 UTF-8，
 * 甚至 UTF-16。编码判错的话，进到系统里的就是乱码，后面再怎么优化检索都没用，
 * 所以这一步必须显式判断，不能依赖平台默认值。
 */
public final class TextDecoder {

    private static final Charset GB18030 = Charset.forName("GB18030");

    private TextDecoder() {
    }

    public static String decode(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return "";
        }
        if (startsWith(bytes, (byte) 0xEF, (byte) 0xBB, (byte) 0xBF)) {
            return new String(bytes, 3, bytes.length - 3, StandardCharsets.UTF_8);
        }
        if (startsWith(bytes, (byte) 0xFF, (byte) 0xFE)) {
            return new String(bytes, 2, bytes.length - 2, StandardCharsets.UTF_16LE);
        }
        if (startsWith(bytes, (byte) 0xFE, (byte) 0xFF)) {
            return new String(bytes, 2, bytes.length - 2, StandardCharsets.UTF_16BE);
        }
        // 没有 BOM：先按 UTF-8 严格校验，校验不过再按 GB18030 解
        return isStrictUtf8(bytes)
                ? new String(bytes, StandardCharsets.UTF_8)
                : new String(bytes, GB18030);
    }

    private static boolean isStrictUtf8(byte[] bytes) {
        try {
            StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes));
            return true;
        } catch (CharacterCodingException e) {
            return false;
        }
    }

    private static boolean startsWith(byte[] bytes, byte... prefix) {
        if (bytes.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (bytes[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }
}
