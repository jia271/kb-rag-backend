package com.example.rag.usage;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface LlmUsageRepository extends JpaRepository<LlmUsage, Long> {

    /**
     * 聚合放在数据库里做，不把明细全查出来再算——量大了那样会直接把内存吃满。
     */
    @Query("""
            select new com.example.rag.usage.UsageByModel(
                u.provider, u.model,
                sum(u.promptTokens), sum(u.completionTokens), count(u),
                sum(u.cost), sum(u.latencyMillis))
            from LlmUsage u
            group by u.provider, u.model
            order by sum(u.promptTokens) + sum(u.completionTokens) desc
            """)
    List<UsageByModel> summarizeByModel();

    long countBySuccessTrue();
}
