package com.xx.aitranslation.agent;

import com.xx.aitranslation.enums.Language;
import com.xx.aitranslation.service.ai.ChatModelRouter;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * 翻译 Agent（V1 模块三 / 模块六）：负责管理翻译 Prompt 模板、按模型 code 路由大模型，
 * 并注入角色 / 风格 / 术语 / RAG / 审校建议等上下文后调用 LLM 返回纯译文。
 * <p>
 * 单句即时翻译与文档流水线统一走本 Agent；带 {@code reviewAdvice} 时即为反馈优化（重翻）模式。
 */
@Component
@RequiredArgsConstructor
public class TranslationAgent {

    /** 角色缺省值（作为给模型的提示词，非 UI 文案） */
    private static final String DEFAULT_ROLE = "通用译者";
    /** 风格缺省值 */
    private static final String DEFAULT_STYLE = "正式";
    /** 空内容占位符（术语 / RAG / 审校建议为空时填入，避免出现裸变量） */
    private static final String EMPTY_PLACEHOLDER = "（无）";

    @Value("classpath:prompts/translation-prompt.st")
    private Resource translationPrompt;

    private final ChatModelRouter chatModelRouter;

    /**
     * 执行翻译。
     *
     * @param ctx 翻译上下文
     * @return 纯译文（已 trim），调用异常时由上游处理
     */
    public String translate(AgentContext ctx) {
        ChatClient client = chatModelRouter.client(ctx.getModelCode());

        Map<String, Object> params = new HashMap<>();
        params.put("role", ObjectUtils.isEmpty(ctx.getRole()) ? DEFAULT_ROLE : ctx.getRole());
        params.put("style", ObjectUtils.isEmpty(ctx.getStyle()) ? DEFAULT_STYLE : ctx.getStyle());
        params.put("glossary", ObjectUtils.isEmpty(ctx.getGlossaryRules()) ? EMPTY_PLACEHOLDER : ctx.getGlossaryRules());
        params.put("ragContext", ObjectUtils.isEmpty(ctx.getRagContext()) ? EMPTY_PLACEHOLDER : ctx.getRagContext());
        params.put("advice", ObjectUtils.isEmpty(ctx.getReviewAdvice()) ? EMPTY_PLACEHOLDER : ctx.getReviewAdvice());
        params.put("contextPrefix", ObjectUtils.isEmpty(ctx.getContextPrefix()) ? EMPTY_PLACEHOLDER : ctx.getContextPrefix());
        params.put("languageInstruction", buildLanguageInstruction(ctx.getSourceLang(), ctx.getTargetLang()));
        params.put("inputText", ctx.getText());

        Prompt prompt = PromptTemplate.builder()
                .resource(translationPrompt)
                .build()
                .create(params);

        String content = client.prompt(prompt).call().content();
        return ObjectUtils.isEmpty(content) ? "" : content.trim();
    }

    /**
     * 构建语言方向指令：显式指定源 / 目标语种时给出明确方向，否则交给模型自动识别。
     */
    private String buildLanguageInstruction(String sourceLang, String targetLang) {
        if (ObjectUtils.isEmpty(sourceLang) || ObjectUtils.isEmpty(targetLang)) {
            return "自动识别源语言：若输入为中文则翻译为英文，否则翻译为中文。";
        }
        return "将" + Language.labelOf(sourceLang) + "翻译为" + Language.labelOf(targetLang) + "。";
    }
}
