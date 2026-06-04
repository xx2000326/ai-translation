package com.xx.aitranslation.controller;

import com.xx.aitranslation.common.BizException;
import com.xx.aitranslation.common.Result;
import com.xx.aitranslation.dto.SegmentFinalRequest;
import com.xx.aitranslation.dto.SegmentUpdateRequest;
import com.xx.aitranslation.dto.StartTranslateRequest;
import com.xx.aitranslation.dto.TaskConfigRequest;
import com.xx.aitranslation.dto.TaskGlossaryRequest;
import com.xx.aitranslation.entity.TaskGlossary;
import com.xx.aitranslation.entity.TranslationSegment;
import com.xx.aitranslation.entity.TranslationTask;
import com.xx.aitranslation.enums.ExportFormat;
import com.xx.aitranslation.enums.FileType;
import com.xx.aitranslation.enums.TaskStatus;
import com.xx.aitranslation.service.TranslationTaskService;
import com.xx.aitranslation.service.export.DocumentExporter;
import com.xx.aitranslation.service.export.DocumentExporterFactory;
import com.xx.aitranslation.service.pipeline.TranslationPipeline;
import com.xx.aitranslation.service.storage.FileStorageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.util.ObjectUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 翻译任务接口：任务列表 / 详情、配置保存、源文件上传与任务级临时术语管理。
 */
@RestController
@RequestMapping("/api/tasks")
@RequiredArgsConstructor
public class TranslationTaskController {

    private final TranslationTaskService translationTaskService;
    private final FileStorageService fileStorageService;
    private final TranslationPipeline translationPipeline;
    private final DocumentExporterFactory documentExporterFactory;

    @GetMapping
    public Result<List<TranslationTask>> list(@RequestParam(required = false) Long projectId) {
        if (ObjectUtils.isEmpty(projectId)) {
            return Result.success(Collections.emptyList());
        }
        return Result.success(translationTaskService.listByProject(projectId));
    }

    @GetMapping("/{id}")
    public Result<TranslationTask> get(@PathVariable Long id) {
        return Result.success(translationTaskService.getById(id));
    }

    @PostMapping("/{id}/config")
    public Result<TranslationTask> config(@PathVariable Long id, @RequestBody TaskConfigRequest request) {
        return Result.success(translationTaskService.saveConfig(id, request));
    }

    @PostMapping("/{id}/file")
    public Result<Map<String, String>> uploadFile(@PathVariable Long id,
                                                   @RequestParam("file") MultipartFile file) {
        if (ObjectUtils.isEmpty(file) || file.isEmpty()) {
            throw new BizException("file.empty");
        }
        String fileName = file.getOriginalFilename();
        FileType fileType = FileType.fromFileName(fileName);
        try {
            String key = fileStorageService.upload(file.getInputStream(), fileName, file.getContentType());
            translationTaskService.saveFile(id, fileName, key, fileType.name());
            Map<String, String> data = new LinkedHashMap<>();
            data.put("fileKey", key);
            data.put("fileName", fileName);
            data.put("fileType", fileType.name());
            return Result.success(data);
        } catch (IOException e) {
            throw new BizException("file.upload.failed");
        }
    }

    @PostMapping("/{id}/parse")
    public Result<Map<String, String>> parse(@PathVariable Long id) {
        // 仅允许在已上传 / 已解析 / 失败重试时触发解析，避免进行中状态被重复触发
        TranslationTask task = translationTaskService.transitFromAny(id, TaskStatus.PARSING,
                TaskStatus.FILE_UPLOADED, TaskStatus.PARSED, TaskStatus.FAILED);
        translationTaskService.clearSegments(id);
        translationPipeline.parseAsync(id);
        Map<String, String> data = new LinkedHashMap<>();
        data.put("status", task.getStatus());
        return Result.success(data);
    }

    @PostMapping("/{id}/translate")
    public Result<Map<String, String>> translate(@PathVariable Long id, @RequestBody StartTranslateRequest request) {
        translationTaskService.saveTranslateConfig(id, request.getModel(),
                request.getEnableReview(), request.getReviewModel());
        // 同步置位 TRANSLATING 并做状态前置校验：仅允许在解析完成/已翻译/审校完成/人工审校/失败时触发，
        // 进行中（PARSING/TRANSLATING/REVIEWING）重复点击会被拦截，避免并发重复翻译
        TranslationTask task = translationTaskService.transitFromAny(id, TaskStatus.TRANSLATING,
                TaskStatus.PARSED, TaskStatus.TRANSLATED, TaskStatus.REVIEW_DONE,
                TaskStatus.MANUAL_REVIEW, TaskStatus.FAILED);
        translationPipeline.translateAsync(id);
        Map<String, String> data = new LinkedHashMap<>();
        data.put("status", task.getStatus());
        return Result.success(data);
    }

    @GetMapping("/{id}/segments")
    public Result<List<TranslationSegment>> listSegments(@PathVariable Long id) {
        return Result.success(translationTaskService.listSegments(id));
    }

    @PutMapping("/{id}/segments")
    public Result<Void> updateSegments(@PathVariable Long id, @RequestBody List<SegmentUpdateRequest> requests) {
        if (ObjectUtils.isEmpty(requests)) {
            return Result.success(null);
        }
        for (SegmentUpdateRequest request : requests) {
            if (ObjectUtils.isEmpty(request.getId())) {
                continue;
            }
            TranslationSegment segment = new TranslationSegment();
            segment.setId(request.getId());
            segment.setOriginalText(request.getOriginalText());
            translationTaskService.updateSegment(segment);
        }
        return Result.success(null);
    }

    @PutMapping("/{taskId}/segments/{segmentId}/final")
    public Result<Void> saveFinal(@PathVariable Long taskId, @PathVariable Long segmentId,
                                  @RequestBody SegmentFinalRequest request) {
        translationTaskService.saveFinal(segmentId, request.getFinalText());
        return Result.success(null);
    }

    @PostMapping("/{taskId}/complete")
    public Result<Map<String, String>> complete(@PathVariable Long taskId) {
        translationTaskService.complete(taskId);
        Map<String, String> data = new LinkedHashMap<>();
        data.put("status", TaskStatus.COMPLETED.name());
        return Result.success(data);
    }

    @GetMapping("/{id}/export")
    public ResponseEntity<byte[]> export(@PathVariable Long id, @RequestParam String format) {
        ExportFormat exportFormat = parseFormat(format);
        TranslationTask task = translationTaskService.getById(id);
        List<TranslationSegment> segments = translationTaskService.listSegments(id);
        DocumentExporter exporter = documentExporterFactory.get(exportFormat);
        byte[] bytes = exporter.export(segments);
        // 仅允许在人工审校/已完成/已导出时导出
        translationTaskService.transitFromAny(id, TaskStatus.EXPORTED,
                TaskStatus.MANUAL_REVIEW, TaskStatus.COMPLETED, TaskStatus.EXPORTED);

        String fileName = buildFileName(task.getSourceFileName(), exporter.fileExtension());
        String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"" + encoded + "\"; filename*=UTF-8''" + encoded);
        headers.add(HttpHeaders.CONTENT_TYPE, exporter.contentType());
        return ResponseEntity.ok().headers(headers).body(bytes);
    }

    private ExportFormat parseFormat(String format) {
        if (ObjectUtils.isEmpty(format)) {
            throw new BizException("export.format.unsupported");
        }
        try {
            return ExportFormat.valueOf(format.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BizException("export.format.unsupported");
        }
    }

    private String buildFileName(String sourceFileName, String ext) {
        String base = "export";
        if (!ObjectUtils.isEmpty(sourceFileName)) {
            int dot = sourceFileName.lastIndexOf('.');
            base = dot > 0 ? sourceFileName.substring(0, dot) : sourceFileName;
        }
        return base + "_translated." + ext;
    }

    @PostMapping("/{id}/glossary")
    public Result<TaskGlossary> addGlossary(@PathVariable Long id, @Valid @RequestBody TaskGlossaryRequest request) {
        return Result.success(translationTaskService.addGlossary(id, request.getTerm(), request.getTranslation()));
    }

    @GetMapping("/{id}/glossary")
    public Result<List<TaskGlossary>> listGlossary(@PathVariable Long id) {
        return Result.success(translationTaskService.listGlossary(id));
    }

    @DeleteMapping("/{id}/glossary/{gid}")
    public Result<Void> deleteGlossary(@PathVariable Long id, @PathVariable Long gid) {
        translationTaskService.deleteGlossary(gid);
        return Result.success(null);
    }
}
