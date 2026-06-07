package com.xx.aitranslation.agent;

import lombok.Builder;
import lombok.Data;

/**
 * 翻译 Agent 上下文：承载一次翻译调用所需的全部参数。
 * <p>
 * 单句即时翻译与文档流水线共用此上下文，统一走 {@link TranslationAgent}。
 * 当 {@link #reviewAdvice} 非空时即为"反馈优化"模式（带审校建议重翻）；
 * 当 {@link #sourceLang} 或 {@link #targetLang} 为空时由模型自动识别语言方向。
 */
@Data
@Builder
public class AgentContext {

    /** 待翻译原文 */
    private String text;

    /** 源语言 code，可为空（空则自动识别） */
    private String sourceLang;

    /** 目标语言 code，可为空（空则自动识别） */
    private String targetLang;

    /** 翻译角色描述（注入 Prompt 的自然语言描述），可为空 */
    private String role;

    /** 翻译风格描述，可为空 */
    private String style;

    /** 命中的术语规则文本，可为空 */
    private String glossaryRules;

    /** RAG 历史参考文本，可为空 */
    private String ragContext;

    /** 上一轮审校建议，可为空（初翻译传 null） */
    private String reviewAdvice;

    /** 使用的模型 code，未识别时由路由回退默认模型 */
    private String modelCode;
}
