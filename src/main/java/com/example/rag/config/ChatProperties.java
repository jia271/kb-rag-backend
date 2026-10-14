package com.example.rag.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "app.chat")
public class ChatProperties {

    /** 每次问答检索多少个片段作为依据 */
    private int topK = 5;

    /** 多轮对话保留最近多少轮（一轮 = 提问 + 回答） */
    private int maxHistoryTurns = 6;

    /** 历史对话最多占用多少字符，超出后从最早的轮次开始丢 */
    private int maxHistoryChars = 4000;

    /** 塞进提示词的资料最多多少字符，防止上下文过长 */
    private int maxContextChars = 6000;

    /** 最多同时保留多少个会话，超出后淘汰最久未使用的 */
    private int maxSessions = 500;
}
