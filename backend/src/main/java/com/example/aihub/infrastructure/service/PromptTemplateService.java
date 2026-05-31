package com.example.aihub.infrastructure.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.aihub.common.exception.BusinessException;
import com.example.aihub.common.util.VoMapper;
import com.example.aihub.infrastructure.dto.PromptTemplateSaveDTO;
import com.example.aihub.infrastructure.entity.PromptTemplate;
import com.example.aihub.infrastructure.mapper.PromptTemplateMapper;
import com.example.aihub.infrastructure.vo.PromptTemplateVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PromptTemplateService {
    private static final long PUBLIC_LIBRARY_PROJECT_ID = 0L;

    private final PromptTemplateMapper templateMapper;

    public List<PromptTemplateVO> list(Long projectId) {
        LambdaQueryWrapper<PromptTemplate> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PromptTemplate::getStatus, 1);
        wrapper.orderByDesc(PromptTemplate::getId);
        return templateMapper.selectList(wrapper).stream().map(item -> VoMapper.copy(item, PromptTemplateVO.class)).toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public PromptTemplateVO create(PromptTemplateSaveDTO dto) {
        PromptTemplate template = new PromptTemplate();
        template.setProjectId(PUBLIC_LIBRARY_PROJECT_ID);
        template.setName(dto.getName());
        template.setContent(dto.getContent());
        template.setTag(dto.getTag());
        template.setStatus(dto.getStatus() == null ? 1 : dto.getStatus());
        templateMapper.insert(template);
        return VoMapper.copy(template, PromptTemplateVO.class);
    }

    @Transactional(rollbackFor = Exception.class)
    public PromptTemplateVO update(Long id, PromptTemplateSaveDTO dto) {
        PromptTemplate template = templateMapper.selectById(id);
        if (template == null) {
            throw new BusinessException("模板不存在");
        }
        template.setProjectId(PUBLIC_LIBRARY_PROJECT_ID);
        template.setName(dto.getName());
        template.setContent(dto.getContent());
        template.setTag(dto.getTag());
        template.setStatus(dto.getStatus() == null ? template.getStatus() : dto.getStatus());
        templateMapper.updateById(template);
        return VoMapper.copy(template, PromptTemplateVO.class);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        PromptTemplate template = templateMapper.selectById(id);
        if (template == null) {
            throw new BusinessException("模板不存在");
        }
        templateMapper.deleteById(id);
    }
}
