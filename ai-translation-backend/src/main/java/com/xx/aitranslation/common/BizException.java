package com.xx.aitranslation.common;

import lombok.Getter;

/**
 * 业务异常，message 使用国际化 code，统一由全局异常处理器翻译。
 */
@Getter
public class BizException extends RuntimeException {

    private final Object[] args;

    public BizException(String messageCode, Object... args) {
        super(messageCode);
        this.args = args;
    }
}
