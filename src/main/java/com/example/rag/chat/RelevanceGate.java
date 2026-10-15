package com.example.rag.chat;

import com.example.rag.config.ChatProperties;
import com.example.rag.retrieval.SearchHit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

/**
 * 相关性门控：检索结果明显不相关时，直接当作"没有找到依据"，不再把噪音喂给模型。
 *
 * <p>为什么用 BM25 分数而不是融合分数：RRF 只看名次，排第一的片段无关于相关性都会拿到同样的分，
 * 结构上就不可能用来判断相关性。这一点由评测数据印证——见 README 的评测结果。
 *
 * <p>默认关闭（阈值 0）：阈值依赖语料规模与用词分布，在 12 条自造题目上标出来的数字
 * 不足以直接当作线上默认值。理由同样写在 README 里。
 */
@Slf4j
@Component
public class RelevanceGate {

    private final ChatProperties properties;

    public RelevanceGate(ChatProperties properties) {
        this.properties = properties;
    }

    public List<SearchHit> filter(List<SearchHit> hits) {
        double threshold = properties.getMinKeywordScore();
        if (threshold <= 0 || hits.isEmpty()) {
            return hits;
        }
        double best = hits.stream()
                .map(SearchHit::keywordScore)
                .filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .max()
                .orElse(0.0);
        if (best < threshold) {
            log.info("最高关键词得分 {} 低于阈值 {}，按无相关内容处理", best, threshold);
            return List.of();
        }
        return hits;
    }
}
