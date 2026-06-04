package com.xx.aitranslation.controller;

import com.xx.aitranslation.common.MessageUtils;
import com.xx.aitranslation.common.Result;
import com.xx.aitranslation.dto.GlossaryRequest;
import com.xx.aitranslation.dto.GlossaryVO;
import com.xx.aitranslation.entity.Glossary;
import com.xx.aitranslation.service.GlossaryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 术语库相关接口。
 */
@RestController
@RequestMapping("/api/glossary")
@RequiredArgsConstructor
public class GlossaryController {

    private final GlossaryService glossaryService;

    @GetMapping
    public Result<List<GlossaryVO>> query(@RequestParam(required = false) String customerName,
                                          @RequestParam(required = false) String term) {
        return Result.success(glossaryService.query(customerName, term));
    }

    @PostMapping
    public Result<Glossary> add(@Valid @RequestBody GlossaryRequest request) {
        Glossary glossary = glossaryService.add(request.getCustomerId(), request.getTerm(),
                request.getTranslation(), request.getCategory());
        return Result.success(MessageUtils.get("glossary.add.success"), glossary);
    }
}
