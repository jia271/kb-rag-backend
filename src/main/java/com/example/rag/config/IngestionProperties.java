package com.example.rag.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "app.ingestion")
public class IngestionProperties {

    /** 上传文件的落盘目录 */
    private String storageDir = "./data/uploads";
}
