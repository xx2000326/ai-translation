package com.xx.aitranslation.graph.router;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.EdgeAction;

/**
 * 审校循环条件路由：根据本轮审校分数和轮次决定下一步。
 * <ul>
 *   <li>通过了（minScore ≥ 80 或 flagged = 0）→ "pass"，跳到风格统一/收尾</li>
 *   <li>未通过但轮次未满 → 从评分后来返回 "retranslate"，从重翻后来返回 "review_score"</li>
 *   <li>已达最大轮次 → "pass"</li>
 * </ul>
 */
public class ReviewLoopRouter implements EdgeAction {

    /** 审校及格分 */
    static final int PASS_SCORE = 80;
    /** 最大审校轮次 */
    static final int MAX_ROUND = 3;

    @Override
    public String apply(OverAllState state) throws Exception {
        int round = state.value("reviewRound", 0);
        int minScore = state.value("minScore", 100);
        int flagged = state.value("flagCount", 0);

        if (flagged == 0 || minScore >= PASS_SCORE || round >= MAX_ROUND) {
            return "pass";
        }

        boolean retranslateDone = state.value("retranslateDone", false);
        return retranslateDone ? "review_score" : "retranslate";
    }
}
