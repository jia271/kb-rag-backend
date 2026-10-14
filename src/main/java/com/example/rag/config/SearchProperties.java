package com.example.rag.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "app.search")
public class SearchProperties {

    /** 索引根目录 */
    private String indexDir = "./data/index";

    /** 默认返回多少条结果 */
    private int topK = 5;

    /** RRF 融合参数，越大越平滑 */
    private int rrfK = 60;
}
