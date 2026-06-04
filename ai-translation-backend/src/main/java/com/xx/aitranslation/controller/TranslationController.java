package com.xx.aitranslation.controller;

import com.xx.aitranslation.common.Result;
import com.xx.aitranslation.dto.TranslateRequest;
import com.xx.aitranslation.dto.TranslateResponse;
import com.xx.aitranslation.enums.TranslationRole;
import com.xx.aitranslation.enums.TranslationStyle;
import com.xx.aitranslation.service.TranslationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 翻译相关接口。
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class TranslationController {

    private final TranslationService translationService;

    @PostMapping("/translate")
    public Result<TranslateResponse> translate(@Valid @RequestBody TranslateRequest request) {
        return Result.success(translationService.translate(request));
    }

    @GetMapping("/roles")
    public Result<List<String>> roles() {
        return Result.success(TranslationRole.codes());
    }

    @GetMapping("/styles")
    public Result<List<String>> styles() {
        return Result.success(TranslationStyle.codes());
    }
}
