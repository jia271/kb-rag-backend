package com.example.rag.eval;

import com.example.rag.retrieval.ChunkIndexService;
import com.example.rag.retrieval.HybridSearchService;
import com.example.rag.retrieval.SearchHit;
import com.example.rag.retrieval.SearchMode;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * 用固定题目集衡量检索效果。
 *
 * <p>评测集小、样本少，结论只能说明趋势，不能当基准；但它足以回答
 * "要不要加某个机制"这类是非题——这正是它存在的意义。
 */
@Slf4j
@Service
public class EvaluationService {

    private static final String QUESTIONS_RESOURCE = "eval/questions.json";

    private final HybridSearchService searchService;
    private final ChunkIndexService index;
    private final ObjectMapper objectMapper;

    public EvaluationService(HybridSearchService searchService, ChunkIndexService index, ObjectMapper objectMapper) {
        this.searchService = searchService;
        this.index = index;
        this.objectMapper = objectMapper;
    }

    public List<EvalQuestion> questions() throws IOException {
        try (InputStream input = new ClassPathResource(QUESTIONS_RESOURCE).getInputStream()) {
            return objectMapper.readValue(input, new TypeReference<>() {
            });
        }
    }

    public EvalReport run(List<SearchMode> modes, int topK) throws IOException {
        List<EvalQuestion> questions = questions();
        List<EvalReport.ModeReport> reports = new ArrayList<>();
        for (SearchMode mode : modes) {
            reports.add(evaluate(questions, mode, topK));
        }
        long answerable = questions.stream().filter(EvalQuestion::answerable).count();
        return new EvalReport(questions.size(), (int) answerable, topK, index.describe(), reports);
    }

    private EvalReport.ModeReport evaluate(List<EvalQuestion> questions, SearchMode mode, int topK)
            throws IOException {
        List<EvalReport.QuestionResult> details = new ArrayList<>();
        List<Double> answerableScores = new ArrayList<>();
        List<Double> unanswerableScores = new ArrayList<>();
        int answerableCount = 0;
        int hitCount = 0;
        double reciprocalRankSum = 0;
        long latencySum = 0;

        for (EvalQuestion question : questions) {
            long start = System.currentTimeMillis();
            List<SearchHit> hits = searchService.search(question.question(), topK, mode);
            latencySum += System.currentTimeMillis() - start;

            double topScore = hits.isEmpty() ? 0.0 : hits.get(0).score();
            int rank = question.answerable() ? firstRelevantRank(hits, question.keywords()) : 0;
            if (question.answerable()) {
                answerableCount++;
                answerableScores.add(topScore);
                if (rank > 0) {
                    hitCount++;
                    reciprocalRankSum += 1.0 / rank;
                }
            } else {
                unanswerableScores.add(topScore);
            }
            details.add(new EvalReport.QuestionResult(
                    question.id(), question.question(), question.answerable(), rank, topScore,
                    hits.isEmpty() ? null : hits.get(0).fileName()));
        }

        double hitRate = answerableCount == 0 ? 0 : (double) hitCount / answerableCount;
        double mrr = answerableCount == 0 ? 0 : reciprocalRankSum / answerableCount;
        return new EvalReport.ModeReport(
                mode.name().toLowerCase(),
                round(hitRate),
                round(mrr),
                round((double) latencySum / Math.max(1, questions.size())),
                round(mean(answerableScores)),
                round(mean(unanswerableScores)),
                details);
    }

    /** 片段里出现任一期望关键词就算命中；名次从 1 开始，0 表示没找到 */
    private int firstRelevantRank(List<SearchHit> hits, List<String> keywords) {
        if (keywords.isEmpty()) {
            return 0;
        }
        for (int position = 0; position < hits.size(); position++) {
            String content = hits.get(position).content();
            for (String keyword : keywords) {
                if (content.contains(keyword)) {
                    return position + 1;
                }
            }
        }
        return 0;
    }

    private double mean(List<Double> values) {
        return values.isEmpty() ? 0 : values.stream().mapToDouble(Double::doubleValue).average().orElse(0);
    }

    private double round(double value) {
        return Math.round(value * 10_000) / 10_000.0;
    }
}
