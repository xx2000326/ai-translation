package com.xx.aitranslation.service.ai;

import com.xx.aitranslation.enums.Language;
import com.xx.aitranslation.service.GlossaryService;
import com.xx.aitranslation.service.RagService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * 初翻译 LLM 服务：按手动选定的模型 code 渲染翻译 Prompt 并调用大模型，返回纯译文。
 * <p>
 * 术语规则与 RAG 上下文由上游（{@code TranslationPipeline}）按段构建后传入，本服务只负责模型调用，
 * 因此对外暴露的 {@link #translate} 是无状态的，便于按段或审校重翻复用。
 */
@Service
@RequiredArgsConstructor
public class TranslationLlmService {

    /** 角色缺省值（任务级未维护翻译角色时使用，作为给模型的提示词，非 UI 文案） */
    private static final String DEFAULT_ROLE = "通用译者";
    /** 风格缺省值 */
    private static final String DEFAULT_STYLE = "正式";
    /** 空内容占位符（术语 / RAG / 审校建议为空时填入，避免出现裸变量） */
    private static final String EMPTY_PLACEHOLDER = "（无）";

    @Value("classpath:prompts/file-translation-prompt.st")
    private Resource fileTranslationPrompt;

    private final ChatModelRouter chatModelRouter;
    private final GlossaryService glossaryService;
    private final RagService ragService;

    /**
     * 执行单段翻译。
     *
     * @param text          待翻译原文
     * @param role          翻译角色，可为空（空则用默认）
     * @param style         翻译风格，可为空（空则用默认）
     * @param sourceLang    源语言 code
     * @param targetLang    目标语言 code
     * @param glossaryRules 命中的术语规则文本，可为空
     * @param ragContext    RAG 历史参考文本，可为空
     * @param reviewAdvice  上一轮审校建议，可为空（初翻译传 null）
     * @param modelCode     使用的模型 code
     * @return 纯译文（已 trim）
     */
    public String translate(String text, String role, String style, String sourceLang, String targetLang,
                            String glossaryRules, String ragContext, String reviewAdvice, String modelCode) {
        ChatClient client = chatModelRouter.client(modelCode);

        Map<String, Object> params = new HashMap<>();
        params.put("role", ObjectUtils.isEmpty(role) ? DEFAULT_ROLE : role);
        params.put("style", ObjectUtils.isEmpty(style) ? DEFAULT_STYLE : style);
        params.put("sourceLang", Language.labelOf(sourceLang));
        params.put("targetLang", Language.labelOf(targetLang));
        params.put("glossary", ObjectUtils.isEmpty(glossaryRules) ? EMPTY_PLACEHOLDER : glossaryRules);
        params.put("ragContext", ObjectUtils.isEmpty(ragContext) ? EMPTY_PLACEHOLDER : ragContext);
        params.put("advice", ObjectUtils.isEmpty(reviewAdvice) ? EMPTY_PLACEHOLDER : reviewAdvice);
        params.put("inputText", text);

        Prompt prompt = PromptTemplate.builder()
                .resource(fileTranslationPrompt)
                .build()
                .create(params);

        String content = client.prompt(prompt).call().content();
        return ObjectUtils.isEmpty(content) ? "" : content.trim();
    }
}
