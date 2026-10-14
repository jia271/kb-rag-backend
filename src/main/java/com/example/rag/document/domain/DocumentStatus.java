package com.example.rag.document.domain;

public enum DocumentStatus {

    /** 已接收，等待解析 */
    PENDING,

    /** 正在解析与切分 */
    PARSING,

    /** 解析完成，可以进入检索 */
    READY,

    /** 解析失败，原因记录在 failureReason */
    FAILED
}
