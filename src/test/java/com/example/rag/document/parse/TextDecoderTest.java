package com.example.rag.document.parse;

import org.junit.jupiter.api.Test;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class TextDecoderTest {

    private static final Charset GB18030 = Charset.forName("GB18030");

    @Test
    void decodesPlainUtf8() {
        assertThat(TextDecoder.decode("中文内容 ABC".getBytes(StandardCharsets.UTF_8)))
                .isEqualTo("中文内容 ABC");
    }

    @Test
    void fallsBackToGb18030WhenBytesAreNotValidUtf8() {
        byte[] gb18030 = "中文内容".getBytes(GB18030);

        assertThat(TextDecoder.decode(gb18030)).isEqualTo("中文内容");
    }

    @Test
    void stripsUtf8Bom() {
        byte[] withBom = "带 BOM 的文本".getBytes(StandardCharsets.UTF_8);
        byte[] bytes = new byte[withBom.length + 3];
        bytes[0] = (byte) 0xEF;
        bytes[1] = (byte) 0xBB;
        bytes[2] = (byte) 0xBF;
        System.arraycopy(withBom, 0, bytes, 3, withBom.length);

        assertThat(TextDecoder.decode(bytes)).isEqualTo("带 BOM 的文本");
    }

    @Test
    void decodesUtf16WithBom() {
        byte[] utf16 = "UTF-16 文本".getBytes(StandardCharsets.UTF_16LE);
        byte[] bytes = new byte[utf16.length + 2];
        bytes[0] = (byte) 0xFF;
        bytes[1] = (byte) 0xFE;
        System.arraycopy(utf16, 0, bytes, 2, utf16.length);

        assertThat(TextDecoder.decode(bytes)).isEqualTo("UTF-16 文本");
    }

    @Test
    void emptyInputGivesEmptyString() {
        assertThat(TextDecoder.decode(new byte[0])).isEmpty();
        assertThat(TextDecoder.decode(null)).isEmpty();
    }
}
