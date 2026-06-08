package com.xx.aitranslation.controller;

import com.xx.aitranslation.common.BizException;
import com.xx.aitranslation.common.Result;
import com.xx.aitranslation.dto.ChunkPreviewResponse;
import com.xx.aitranslation.service.chunk.ChunkEngine;
import com.xx.aitranslation.service.chunk.config.ChunkConfig;
import com.xx.aitranslation.service.chunk.config.ChunkStrategyType;
import lombok.RequiredArgsConstructor;
import org.springframework.util.ObjectUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * 文档拆分预览接口：供前端上传文件、选择拆分方式并查看拆分结果（仅预览，不落库）。
 */
@RestController
@RequestMapping("/api/chunk")
@RequiredArgsConstructor
public class ChunkController {

    private final ChunkEngine chunkEngine;

    /**
     * 可选拆分策略列表（供前端下拉选择）。
     */
    @GetMapping("/strategies")
    public Result<List<String>> strategies() {
        return Result.success(Arrays.stream(ChunkStrategyType.values()).map(Enum::name).toList());
    }

    /**
     * 拆分预览。
     *
     * @param file        待拆分文件
     * @param strategy    拆分策略（为空 / AUTO 时由引擎按文件类型自动选择）
     * @param chunkSize   固定/段落/句子策略单块字符数
     * @param overlap     固定策略重叠字符数
     * @param parentSize  层级策略父块字符数
     * @param childSize   层级策略子块字符数
     */
    @PostMapping("/preview")
    public Result<ChunkPreviewResponse> preview(@RequestParam("file") MultipartFile file,
                                                @RequestParam(value = "strategy", required = false) String strategy,
                                                @RequestParam(value = "chunkSize", defaultValue = "1000") int chunkSize,
                                                @RequestParam(value = "overlap", defaultValue = "100") int overlap,
                                                @RequestParam(value = "parentSize", defaultValue = "5000") int parentSize,
                                                @RequestParam(value = "childSize", defaultValue = "1000") int childSize) {
        if (ObjectUtils.isEmpty(file) || file.isEmpty()) {
            throw new BizException("file.empty");
        }
        ChunkConfig config = ChunkConfig.builder()
                .strategy(ChunkStrategyType.parse(strategy))
                .chunkSize(chunkSize)
                .overlap(overlap)
                .parentSize(parentSize)
                .childSize(childSize)
                .build();
        try {
            ChunkPreviewResponse response = ChunkPreviewResponse.from(
                    chunkEngine.chunk(file.getInputStream(), file.getOriginalFilename(), config));
            return Result.success(response);
        } catch (IOException e) {
            throw new BizException("chunk.read.failed");
        }
    }
}
