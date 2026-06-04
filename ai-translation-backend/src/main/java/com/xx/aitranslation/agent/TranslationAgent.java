package com.xx.aitranslation.agent;

import com.xx.aitranslation.enums.TranslationRole;
import com.xx.aitranslation.enums.TranslationStyle;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 翻译 Agent：负责管理 Prompt 模板、选择翻译角色/风格策略、注入术语库与 RAG 上下文，并调用 LLM。
 */
@Component
public class TranslationAgent {

    private final ChatClient translationChatClient;

    @Value("classpath:prompts/translation-prompt.st")
    private Resource translationPrompt;

    public TranslationAgent(@Qualifier("translationChatClient") ChatClient translationChatClient) {
        this.translationChatClient = translationChatClient;
    }

    /**
     * 执行翻译。
     *
     * @param inputText  待翻译文本
     * @param role       翻译角色
     * @param style      翻译风格
     * @param glossary   命中的术语规则文本，可为空
     * @param ragContext RAG 检索到的历史参考翻译，可为空
     * @return 译文
     */
    public String translate(String inputText, TranslationRole role, TranslationStyle style,
                            String glossary, String ragContext) {
        Map<String, Object> params = new HashMap<>();
        params.put("role", role.getDescription());
        params.put("style", style.getDescription());
        params.put("glossary", isBlank(glossary) ? "（无）" : glossary);
        params.put("ragContext", isBlank(ragContext) ? "（无）" : ragContext);
        params.put("inputText", inputText);

        PromptTemplate promptTemplate = PromptTemplate.builder()
                .resource(translationPrompt)
                .build();
        Prompt prompt = promptTemplate.create(params);

        return translationChatClient.prompt(prompt)
                .call()
                .content();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
