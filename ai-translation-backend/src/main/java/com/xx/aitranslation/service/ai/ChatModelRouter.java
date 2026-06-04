package com.xx.aitranslation.service.ai;

import com.xx.aitranslation.enums.ModelCode;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.ai.deepseek.DeepSeekChatOptions;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Component;

/**
 * 大模型路由：按模型 code 解析所属服务商，构建对应的 {@link ChatClient}。
 * <p>
 * DashScope 系（qwen-*）走 OpenAI 兼容模式的 {@link OpenAiChatModel}；
 * DeepSeek 系走 {@link DeepSeekChatModel}。
 */
@Component
@RequiredArgsConstructor
public class ChatModelRouter {

    private final OpenAiChatModel openAiChatModel;
    private final DeepSeekChatModel deepSeekChatModel;

    /**
     * 按模型 code 构建 ChatClient。
     *
     * @param modelCode 模型 code（如 qwen-plus / deepseek-chat），未识别时回退默认模型
     * @return 配置好默认 Options 的 ChatClient
     */
    public ChatClient client(String modelCode) {
        ModelCode model = ModelCode.fromCode(modelCode);
        String code = model.getCode();
        return switch (model.getProvider()) {
            case DEEPSEEK -> ChatClient.builder(deepSeekChatModel)
                    .defaultOptions(DeepSeekChatOptions.builder()
                            .model(code)
                            .temperature(0.2)
                            .build())
                    .build();
            case DASHSCOPE -> ChatClient.builder(openAiChatModel)
                    .defaultOptions(OpenAiChatOptions.builder()
                            .model(code)
                            .temperature(0.3)
                            .build())
                    .build();
        };
    }
}
