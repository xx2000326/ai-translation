package com.xx.aitranslation.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * SpringAI 配置：构建翻译用的 {@link ChatClient}。
 * <p>
 * 系统提示词通过 {@code TranslationAgent} 在每次调用时按 role/style/术语/RAG 动态拼装，
 * 因此这里只构建一个通用的 ChatClient。
 */
@Configuration
public class SpringAIConfig {

    @Bean("translationChatClient")
    public ChatClient translationChatClient(OpenAiChatModel openAiChatModel) {
        return ChatClient.builder(openAiChatModel)
                .defaultAdvisors(new SimpleLoggerAdvisor())
                .build();
    }
}
