package com.example.rag.eval.api;

import com.example.rag.eval.EvalReport;
import com.example.rag.eval.EvaluationService;
import com.example.rag.retrieval.SearchMode;
import com.example.rag.support.BlockingCalls;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.List;

/**
 * 评测接口。
 *
 * <p>注意：这是开发和运维用的接口，正式环境应当鉴权或直接关闭。
 * 当前项目没有做权限体系（第一版明确不做），所以先如实标注。
 */
@RestController
@RequestMapping("/api/eval")
public class EvalController {

    private final EvaluationService evaluationService;

    public EvalController(EvaluationService evaluationService) {
        this.evaluationService = evaluationService;
    }

    @PostMapping("/run")
    public Mono<EvalReport> run(@RequestParam(defaultValue = "keyword,vector,hybrid") String modes,
                                @RequestParam(defaultValue = "5") int topK) {
        List<SearchMode> parsed = Arrays.stream(modes.split(","))
                .map(SearchMode::from)
                .distinct()
                .toList();
        int limit = Math.max(1, Math.min(topK, 20));
        return BlockingCalls.call(() -> evaluationService.run(parsed, limit));
    }
}
