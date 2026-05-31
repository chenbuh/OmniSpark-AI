package com.example.aihub.infrastructure.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.aihub.common.exception.BusinessException;
import com.example.aihub.common.util.SecurityUtil;
import com.example.aihub.common.util.VoMapper;
import com.example.aihub.infrastructure.dto.PromptTemplateSaveDTO;
import com.example.aihub.infrastructure.entity.PromptTemplate;
import com.example.aihub.infrastructure.entity.User;
import com.example.aihub.infrastructure.mapper.PromptTemplateMapper;
import com.example.aihub.infrastructure.mapper.UserMapper;
import com.example.aihub.infrastructure.vo.PromptTemplateVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class PromptTemplateService {
    private static final long PUBLIC_LIBRARY_PROJECT_ID = 0L;

    private final PromptTemplateMapper templateMapper;
    private final UserMapper userMapper;
    private final PublicContentInteractionService interactionService;

    public List<PromptTemplateVO> list(Long projectId, String sort, Long currentUserId) {
        LambdaQueryWrapper<PromptTemplate> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PromptTemplate::getStatus, 1);
        applySort(wrapper, sort);
        List<PromptTemplate> templates = templateMapper.selectList(wrapper);
        Set<Long> likedIds = interactionService.findLikedResourceIds(
                PublicContentInteractionService.RESOURCE_PROMPT_TEMPLATE,
                templates.stream().map(PromptTemplate::getId).toList(),
                currentUserId
        );
        return templates.stream().map(item -> toVO(item, likedIds.contains(item.getId()))).toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public PromptTemplateVO create(PromptTemplateSaveDTO dto) {
        User currentUser = requireCurrentUser();
        PromptTemplate template = new PromptTemplate();
        template.setProjectId(PUBLIC_LIBRARY_PROJECT_ID);
        template.setUserId(currentUser.getId());
        template.setUsername(currentUser.getUsername());
        template.setNickname(currentUser.getNickname());
        template.setAvatar(currentUser.getAvatar());
        template.setName(dto.getName());
        template.setContent(dto.getContent());
        template.setNegativePrompt(dto.getNegativePrompt());
        template.setModelName(dto.getModelName());
        template.setTag(dto.getTag());
        template.setLikesCount(0);
        template.setCommentsCount(0);
        template.setStatus(dto.getStatus() == null ? 1 : dto.getStatus());
        templateMapper.insert(template);
        return toVO(template, false);
    }

    @Transactional(rollbackFor = Exception.class)
    public PromptTemplateVO update(Long id, PromptTemplateSaveDTO dto) {
        PromptTemplate template = templateMapper.selectById(id);
        if (template == null) {
            throw new BusinessException("模板不存在");
        }
        assertOwner(template);
        template.setProjectId(PUBLIC_LIBRARY_PROJECT_ID);
        template.setName(dto.getName());
        template.setContent(dto.getContent());
        template.setNegativePrompt(dto.getNegativePrompt());
        template.setModelName(dto.getModelName());
        template.setTag(dto.getTag());
        template.setStatus(dto.getStatus() == null ? template.getStatus() : dto.getStatus());
        templateMapper.updateById(template);
        return toVO(template, false);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        PromptTemplate template = templateMapper.selectById(id);
        if (template == null) {
            throw new BusinessException("模板不存在");
        }
        assertOwner(template);
        templateMapper.deleteById(id);
    }

    private PromptTemplateVO toVO(PromptTemplate template, boolean liked) {
        PromptTemplateVO vo = VoMapper.copy(template, PromptTemplateVO.class);
        vo.setLiked(liked ? 1 : 0);
        return vo;
    }

    private void applySort(LambdaQueryWrapper<PromptTemplate> wrapper, String sort) {
        if ("likes".equals(sort)) {
            wrapper.orderByDesc(PromptTemplate::getLikesCount).orderByDesc(PromptTemplate::getId);
            return;
        }
        if ("comments".equals(sort)) {
            wrapper.orderByDesc(PromptTemplate::getCommentsCount).orderByDesc(PromptTemplate::getId);
            return;
        }
        wrapper.orderByDesc(PromptTemplate::getId);
    }

    private User requireCurrentUser() {
        User currentUser = userMapper.selectById(SecurityUtil.loginUserId());
        if (currentUser == null) {
            throw new BusinessException("当前用户不存在");
        }
        return currentUser;
    }

    private void assertOwner(PromptTemplate template) {
        Long currentUserId = SecurityUtil.loginUserId();
        if (template.getUserId() != null && !template.getUserId().equals(currentUserId)) {
            throw new BusinessException("只能编辑或删除自己发布的模板");
        }
    }
}
