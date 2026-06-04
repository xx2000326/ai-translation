package com.xx.aitranslation.controller;

import com.xx.aitranslation.common.Result;
import com.xx.aitranslation.dto.TranslationHistoryVO;
import com.xx.aitranslation.service.HistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 翻译历史相关接口。
 */
@RestController
@RequestMapping("/api/history")
@RequiredArgsConstructor
public class HistoryController {

    private final HistoryService historyService;

    @GetMapping
    public Result<List<TranslationHistoryVO>> list(@RequestParam(required = false) String customerName) {
        return Result.success(historyService.list(customerName));
    }
}
