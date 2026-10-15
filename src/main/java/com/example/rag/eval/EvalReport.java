package com.example.rag.eval;

import java.util.List;

/**
 * 评测报告。指标只针对检索环节——检索是整条链路里最值得量化的部分，
 * 而且它不调用大模型，跑一次不花钱、可反复跑。
 */
public record EvalReport(
        int questions,
        int answerableQuestions,
        int topK,
        String index,
        List<ModeReport> modes
) {

    /**
     * @param hitRate          可回答问题中，期望片段出现在前 topK 的比例
     * @param mrr              平均倒数排名：命中越靠前分数越高，最高 1.0
     * @param averageLatencyMs 平均检索耗时
     * @param answerableTopScore   可回答问题的首条得分均值
     * @param unanswerableTopScore 不可回答问题的首条得分均值，
     *                             与上一个数字拉不开差距就说明该加相关性阈值
     */
    public record ModeReport(
            String mode,
            double hitRate,
            double mrr,
            double averageLatencyMs,
            double answerableTopScore,
            double unanswerableTopScore,
            List<QuestionResult> details
    ) {
    }

    /**
     * @param rank 期望片段在结果里的名次，0 表示前 topK 里没有
     */
    public record QuestionResult(
            String id,
            String question,
            boolean answerable,
            int rank,
            double topScore,
            String topSource
    ) {
    }
}
