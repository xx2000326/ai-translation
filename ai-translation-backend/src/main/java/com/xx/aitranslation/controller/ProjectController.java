package com.xx.aitranslation.controller;

import com.xx.aitranslation.common.MessageUtils;
import com.xx.aitranslation.common.Result;
import com.xx.aitranslation.dto.ProjectRequest;
import com.xx.aitranslation.entity.Project;
import com.xx.aitranslation.entity.TranslationTask;
import com.xx.aitranslation.service.ProjectService;
import com.xx.aitranslation.service.TranslationTaskService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 项目管理接口（为后续文件翻译做准备）。
 */
@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;
    private final TranslationTaskService translationTaskService;

    @GetMapping
    public Result<List<Project>> list(@RequestParam(required = false) Long customerId) {
        return Result.success(projectService.list(customerId));
    }

    @GetMapping("/{id}")
    public Result<Project> get(@PathVariable Long id) {
        return Result.success(projectService.getById(id));
    }

    @PostMapping
    public Result<Project> create(@Valid @RequestBody ProjectRequest request) {
        return Result.success(MessageUtils.get("project.save.success"), projectService.create(request));
    }

    @PutMapping("/{id}")
    public Result<Project> update(@PathVariable Long id, @Valid @RequestBody ProjectRequest request) {
        return Result.success(MessageUtils.get("project.save.success"), projectService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        projectService.delete(id);
        return Result.success(MessageUtils.get("project.delete.success"), null);
    }

    @PostMapping("/{id}/start")
    public Result<TranslationTask> start(@PathVariable Long id) {
        return Result.success(translationTaskService.createFromProject(id));
    }
}
