package com.xx.aitranslation.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 翻译响应体。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TranslateResponse {

    private String translatedText;

    private String role;

    private String style;
}
