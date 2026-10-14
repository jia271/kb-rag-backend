package com.example.rag.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 分块策略参数。这是整个检索质量里最值得调的三个数字。
 */
@Data
@ConfigurationProperties(prefix = "app.chunking")
public class ChunkingProperties {

    /** 单个块的最大字符数 */
    private int maxChars = 800;

    /** 相邻块之间的重叠字符数，避免答案正好被切在边界上 */
    private int overlapChars = 120;

    /** 小于这个长度的块会尝试并入前一块，避免产生碎片 */
    private int minChars = 120;
}
