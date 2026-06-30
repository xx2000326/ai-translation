package com.xx.aitranslation.graph;

import com.alibaba.cloud.ai.graph.KeyStrategy;
import com.alibaba.cloud.ai.graph.KeyStrategyFactory;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.state.strategy.ReplaceStrategy;
import com.xx.aitranslation.graph.router.ReviewLoopRouter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

import static com.alibaba.cloud.ai.graph.StateGraph.END;
import static com.alibaba.cloud.ai.graph.StateGraph.START;
import static com.alibaba.cloud.ai.graph.action.AsyncEdgeAction.edge_async;
import static com.alibaba.cloud.ai.graph.action.AsyncNodeAction.node_async;

@Configuration
@RequiredArgsConstructor
public class TranslationGraphConfig {

    private final GraphNodes graphNodes;

    @Bean
    public KeyStrategyFactory translationKeyStrategyFactory() {
        return () -> {
            Map<String, KeyStrategy> map = new HashMap<>();
            map.put("taskId",           new ReplaceStrategy());
            map.put("customerId",       new ReplaceStrategy());
            map.put("sourceLang",       new ReplaceStrategy());
            map.put("targetLang",       new ReplaceStrategy());
            map.put("translateModel",   new ReplaceStrategy());
            map.put("reviewModel",      new ReplaceStrategy());
            map.put("requirement",      new ReplaceStrategy());
            map.put("roleDesc",         new ReplaceStrategy());
            map.put("styleDesc",        new ReplaceStrategy());
            map.put("ragRole",          new ReplaceStrategy());
            map.put("ragStyle",         new ReplaceStrategy());
            map.put("tempGlossary",     new ReplaceStrategy());
            map.put("enableGlossary",   new ReplaceStrategy());
            map.put("enableHistory",    new ReplaceStrategy());
            map.put("enableReview",     new ReplaceStrategy());
            map.put("enableSummary",    new ReplaceStrategy());
            map.put("reviewRound",      new ReplaceStrategy());
            map.put("minScore",         new ReplaceStrategy());
            map.put("flagCount",        new ReplaceStrategy());
            map.put("retranslateDone",  new ReplaceStrategy());
            map.put("contextById",      new ReplaceStrategy());
            map.put("styleGuide",       new ReplaceStrategy());
            map.put("parseDone",        new ReplaceStrategy());
            map.put("parseFailed",      new ReplaceStrategy());
            map.put("parseError",       new ReplaceStrategy());
            map.put("translateDone",    new ReplaceStrategy());
            map.put("summaryDone",      new ReplaceStrategy());
            map.put("done",             new ReplaceStrategy());
            map.put("flowStopped",      new ReplaceStrategy());
            return map;
        };
    }

    @Bean
    public com.alibaba.cloud.ai.graph.CompiledGraph translationGraph(
            KeyStrategyFactory translationKeyStrategyFactory) throws Exception {

        return new com.alibaba.cloud.ai.graph.StateGraph("translation-pipeline", translationKeyStrategyFactory)
                .addNode("parse",           node_async(graphNodes::parseNode))
                .addNode("translate",       node_async(graphNodes::translateNode))
                .addNode("review_score",    node_async(graphNodes::reviewScoreNode))
                .addNode("retranslate",     node_async(graphNodes::retranslateNode))
                .addNode("summary_guide",   node_async(graphNodes::summaryGuideNode))
                .addNode("summary_unify",   node_async(graphNodes::summaryUnifyNode))
                .addNode("finalize",        node_async(graphNodes::finalizeNode))
                .addNode("summary_router", node_async(state -> Map.of()))

                .addEdge(START, "parse")
                .addEdge("parse", "translate")

                .addConditionalEdges("translate",
                        edge_async(state -> state.value("enableReview", false) ? "review_score" : "summary_router"),
                        Map.of("review_score", "review_score", "summary_router", "summary_router"))

                .addConditionalEdges("review_score",
                        edge_async(new ReviewLoopRouter()),
                        Map.of("retranslate", "retranslate", "pass", "summary_router"))

                .addConditionalEdges("retranslate",
                        edge_async(new ReviewLoopRouter()),
                        Map.of("review_score", "review_score", "pass", "summary_router"))

                .addConditionalEdges("summary_router",
                        edge_async(state -> state.value("enableSummary", false) ? "summary_guide" : "finalize"),
                        Map.of("summary_guide", "summary_guide", "finalize", "finalize"))

                .addEdge("summary_guide", "summary_unify")
                .addEdge("summary_unify", "finalize")
                .addEdge("finalize", END)
                .compile();
    }
}
