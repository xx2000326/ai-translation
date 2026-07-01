package com.xx.aitranslation.service.pipeline;

import com.xx.aitranslation.common.BizException;
import com.xx.aitranslation.entity.Project;
import com.xx.aitranslation.entity.TaskGlossary;
import com.xx.aitranslation.entity.TranslationTask;
import com.xx.aitranslation.enums.TaskStatus;
import com.xx.aitranslation.enums.TranslationRole;
import com.xx.aitranslation.enums.TranslationStyle;
import com.xx.aitranslation.mapper.ProjectMapper;
import com.xx.aitranslation.service.DocumentParseService;
import com.xx.aitranslation.service.TranslationTaskService;
import com.xx.aitranslation.service.chunk.ChunkEngine;
import com.xx.aitranslation.service.chunk.ChunkParseAdapter;
import com.xx.aitranslation.service.chunk.config.ChunkConfig;
import com.xx.aitranslation.service.chunk.config.ChunkStrategyType;
import com.xx.aitranslation.service.chunk.model.ChunkResult;
import com.xx.aitranslation.service.chunk.strategy.TitleChunkStrategy;
import com.xx.aitranslation.service.parse.ParsedDocument;
import com.xx.aitranslation.service.storage.FileStorageService;
import com.xx.aitranslation.support.TaskExtraDataSupport;
import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.KeyStrategyFactory;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 翻译流水线编排器：负责异步解析和 Graph 驱动的翻译流程。
 * <p>
 * 解析阶段通过 {@code parseAsync} 完成文档拆分与落库；
 * 翻译阶段通过 Spring AI Alibaba Graph（{@code TranslationGraphConfig}）节点图执行
 * 初译 → AI 审校循环 → 风格统一 → 人工审校的完整流程。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TranslationPipeline {

    private final TranslationTaskService translationTaskService;
    private final DocumentParseService documentParseService;
    private final FileStorageService fileStorageService;
    private final ChunkEngine chunkEngine;
    private final ChunkParseAdapter chunkParseAdapter;
    private final ProjectMapper projectMapper;
    private final TaskExtraDataSupport taskExtraDataSupport;
    private final CompiledGraph translationGraph;
    private final KeyStrategyFactory translationKeyStrategyFactory;

    /**
     * 异步解析：下载源文件 → 文档拆分引擎拆分 → 映射为翻译单元落库 → 状态置 PARSED。
     */
    @Async("taskExecutor")
    public void parseAsync(Long taskId) {
        translationTaskService.startParseStep(taskId);
        try {
            TranslationTask task = translationTaskService.getById(taskId);
            ChunkConfig config = buildChunkConfig(task);
            ChunkResult result;
            try (InputStream in = fileStorageService.download(task.getSourceFileKey())) {
                result = chunkEngine.chunk(in, task.getSourceFileName(), config);
            }
            ParsedDocument parsed = chunkParseAdapter.toParsedDocument(result);
            documentParseService.saveParsedDocument(taskId, task, parsed);
            translationTaskService.completeParseStep(taskId);
            translationTaskService.transit(taskId, TaskStatus.PARSING, TaskStatus.PARSED);
        } catch (BizException e) {
            log.error("文档解析失败, taskId={}, code={}", taskId, e.getMessage());
            translationTaskService.fail(taskId, e.getMessage());
        } catch (Exception e) {
            log.error("文档解析失败, taskId={}", taskId, e);
            translationTaskService.fail(taskId, e.getMessage());
        }
    }

    /**
     * 由任务配置构建拆分引擎配置；为空字段交由全局默认值兜底。
     */
    private ChunkConfig buildChunkConfig(TranslationTask task) {
        ChunkStrategyType strategyType = ChunkStrategyType.parse(task.getChunkStrategy());
        ChunkConfig.ChunkConfigBuilder builder = ChunkConfig.builder()
                .strategy(strategyType)
                .headingSplitLevel(taskExtraDataSupport.resolveHeadingLevel(task.getExtraData()));
        if (!ObjectUtils.isEmpty(task.getChunkSize())) {
            builder.chunkSize(task.getChunkSize());
        } else if (strategyType == ChunkStrategyType.TITLE
                || strategyType == ChunkStrategyType.MARKDOWN
                || strategyType == null) {
            builder.chunkSize(TitleChunkStrategy.DEFAULT_SECTION_MAX_SIZE);
        }
        if (!ObjectUtils.isEmpty(task.getChunkOverlap())) {
            builder.overlap(task.getChunkOverlap());
        }
        if (!ObjectUtils.isEmpty(task.getChunkParentSize())) {
            builder.parentSize(task.getChunkParentSize());
        }
        if (!ObjectUtils.isEmpty(task.getChunkChildSize())) {
            builder.childSize(task.getChunkChildSize());
        }
        return builder.build();
    }

    /**
     * Graph 版异步翻译编排：使用 Spring AI Alibaba Graph 节点图执行翻译流程。
     */
    @Async("taskExecutor")
    public void translateWithGraphAsync(Long taskId) {
        try {
            TranslationTask task = translationTaskService.getById(taskId);
            Map<String, Object> inputs = buildGraphInputs(task);

            RunnableConfig config = RunnableConfig.builder()
                    .threadId("task-" + taskId)
                    .build();

            translationGraph.invoke(inputs, config);
        } catch (Exception e) {
            log.error("Graph 翻译失败, taskId={}", taskId, e);
            translationTaskService.fail(taskId, e.getMessage());
        }
    }

    /**
     * 从 TranslationTask 构建 Graph 输入状态。
     */
    private Map<String, Object> buildGraphInputs(TranslationTask task) {
        Project project = task.getProjectId() == null ? null : projectMapper.selectById(task.getProjectId());

        Map<String, Object> inputs = new HashMap<>();
        inputs.put("taskId", task.getId());
        inputs.put("customerId", task.getCustomerId());
        inputs.put("sourceLang", task.getSourceLang());
        inputs.put("targetLang", task.getTargetLang());
        inputs.put("translateModel", task.getTranslateModel());
        inputs.put("reviewModel", task.getReviewModel());
        inputs.put("requirement", task.getRequirement());
        inputs.put("enableGlossary", isTrue(task.getEnableGlossary()));
        inputs.put("enableHistory", isTrue(task.getEnableHistory()));
        inputs.put("enableReview", isTrue(task.getEnableReview()));
        inputs.put("enableSummary", isTrue(task.getEnableSummary()));

        String roleCode = project == null ? null : project.getRole();
        String styleCode = project == null ? null : project.getStyle();
        TranslationRole role = TranslationRole.fromCode(roleCode);
        TranslationStyle style = TranslationStyle.fromCode(styleCode);
        inputs.put("roleDesc", role == null ? null : role.getDescription());
        inputs.put("styleDesc", style == null ? null : style.getDescription());
        inputs.put("ragRole", ObjectUtils.isEmpty(roleCode) ? "" : roleCode);
        inputs.put("ragStyle", ObjectUtils.isEmpty(styleCode) ? "" : styleCode);

        inputs.put("tempGlossary", isTrue(task.getEnableGlossary()) ? buildTempGlossary(task.getId()) : "");

        return inputs;
    }

    private boolean isTrue(Boolean value) {
        return !ObjectUtils.isEmpty(value) && value;
    }

    private String buildTempGlossary(Long taskId) {
        List<TaskGlossary> list = translationTaskService.listGlossary(taskId);
        if (ObjectUtils.isEmpty(list)) {
            return "";
        }
        return list.stream()
                .filter(g -> !ObjectUtils.isEmpty(g.getTerm()))
                .map(g -> g.getTerm() + " -> " + g.getTranslation())
                .collect(Collectors.joining("\n"));
    }
}
