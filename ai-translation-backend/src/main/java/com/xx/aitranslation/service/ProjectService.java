package com.xx.aitranslation.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xx.aitranslation.common.BizException;
import com.xx.aitranslation.dto.ProjectRequest;
import com.xx.aitranslation.entity.Project;
import com.xx.aitranslation.mapper.ProjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.util.List;

/**
 * 项目服务：项目的增删改查，承载后续文件翻译所需的客户与术语库配置。
 */
@Service
@RequiredArgsConstructor
public class ProjectService {

    private final ProjectMapper projectMapper;

    public List<Project> list(Long customerId) {
        LambdaQueryWrapper<Project> wrapper = new LambdaQueryWrapper<>();
        if (!ObjectUtils.isEmpty(customerId)) {
            wrapper.eq(Project::getCustomerId, customerId);
        }
        wrapper.orderByDesc(Project::getCreateTime);
        return projectMapper.selectList(wrapper);
    }

    public Project getById(Long id) {
        return projectMapper.selectById(id);
    }

    public Project create(ProjectRequest request) {
        Project project = new Project();
        applyRequest(project, request);
        projectMapper.insert(project);
        return project;
    }

    public Project update(Long id, ProjectRequest request) {
        Project project = projectMapper.selectById(id);
        if (ObjectUtils.isEmpty(project)) {
            throw new BizException("project.not.found");
        }
        applyRequest(project, request);
        projectMapper.updateById(project);
        return project;
    }

    public void delete(Long id) {
        if (ObjectUtils.isEmpty(projectMapper.selectById(id))) {
            throw new BizException("project.not.found");
        }
        projectMapper.deleteById(id);
    }

    private void applyRequest(Project project, ProjectRequest request) {
        project.setName(request.getName());
        project.setCustomerId(request.getCustomerId());
        project.setEnableGlossary(!ObjectUtils.isEmpty(request.getEnableGlossary()) && request.getEnableGlossary());
        project.setRole(request.getRole());
        project.setStyle(request.getStyle());
        project.setDescription(request.getDescription());
    }
}
