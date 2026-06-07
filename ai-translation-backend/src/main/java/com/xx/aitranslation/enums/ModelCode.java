package com.xx.aitranslation.enums;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * 可选大模型枚举：维护模型 code 与其所属服务商，供模型路由（{@code ChatModelRouter}）与前端模型下拉使用。
 */
public enum ModelCode {

    QWEN_PLUS("qwen-plus", Provider.DASHSCOPE),
    QWEN_MAX("qwen-max", Provider.DASHSCOPE),
    QWEN_TURBO("qwen-turbo", Provider.DASHSCOPE),
    DEEPSEEK_V4_FLASH("deepseek-v4-flash", Provider.DEEPSEEK),
    DEEPSEEK_V4_PRO("deepseek-v4-pro", Provider.DEEPSEEK);

    /**
     * 模型服务商：决定路由到哪个 ChatModel。
     */
    public enum Provider {
        /** 阿里云百炼（DashScope，OpenAI 兼容模式） */
        DASHSCOPE,
        /** DeepSeek 官方 */
        DEEPSEEK
    }

    private final String code;
    private final Provider provider;

    ModelCode(String code, Provider provider) {
        this.code = code;
        this.provider = provider;
    }

    public String getCode() {
        return code;
    }

    public Provider getProvider() {
        return provider;
    }

    public static List<String> codes() {
        return Arrays.stream(values()).map(ModelCode::getCode).toList();
    }

    /**
     * 按模型 code 查找枚举，未匹配时回退到默认模型 {@link #QWEN_PLUS}。
     */
    public static ModelCode fromCode(String code) {
        if (Objects.isNull(code)) {
            return QWEN_PLUS;
        }
        return Arrays.stream(values())
                .filter(m -> m.code.equalsIgnoreCase(code))
                .findFirst()
                .orElse(QWEN_PLUS);
    }
}
