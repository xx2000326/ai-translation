package com.xx.aitranslation.controller;

import com.xx.aitranslation.common.Result;
import com.xx.aitranslation.enums.Language;
import com.xx.aitranslation.enums.ModelCode;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 元数据接口：提供可选模型列表与语言列表，供前端下拉选择。
 */
@RestController
@RequestMapping("/api")
public class ModelController {

    @GetMapping("/models")
    public Result<List<String>> models() {
        return Result.success(ModelCode.codes());
    }

    @GetMapping("/languages")
    public Result<List<Language.LanguageOption>> languages() {
        return Result.success(Language.codes());
    }
}
