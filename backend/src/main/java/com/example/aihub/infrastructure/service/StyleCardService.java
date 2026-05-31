package com.example.aihub.infrastructure.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.aihub.common.exception.BusinessException;
import com.example.aihub.common.util.SecurityUtil;
import com.example.aihub.common.util.VoMapper;
import com.example.aihub.infrastructure.dto.StyleCardSaveDTO;
import com.example.aihub.infrastructure.entity.StyleCard;
import com.example.aihub.infrastructure.entity.User;
import com.example.aihub.infrastructure.mapper.StyleCardMapper;
import com.example.aihub.infrastructure.mapper.UserMapper;
import com.example.aihub.infrastructure.vo.StyleCardVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class StyleCardService {
    private static final long PUBLIC_LIBRARY_PROJECT_ID = 0L;

    private final StyleCardMapper cardMapper;
    private final UserMapper userMapper;
    private final PublicContentInteractionService interactionService;

    public List<StyleCardVO> list(Long projectId, String type, String sort, Long currentUserId) {
        LambdaQueryWrapper<StyleCard> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(StyleCard::getStatus, 1);
        if (type != null && !type.isBlank()) {
            wrapper.eq(StyleCard::getType, type);
        }
        applySort(wrapper, sort);
        List<StyleCard> cards = cardMapper.selectList(wrapper);
        Set<Long> likedIds = interactionService.findLikedResourceIds(
                PublicContentInteractionService.RESOURCE_STYLE_CARD,
                cards.stream().map(StyleCard::getId).toList(),
                currentUserId
        );
        return cards.stream().map(card -> toVO(card, likedIds.contains(card.getId()))).toList();
    }

    public StyleCardVO get(Long id, Long currentUserId) {
        StyleCard card = cardMapper.selectById(id);
        if (card == null || card.getStatus() == null || card.getStatus() != 1) {
            throw new BusinessException("卡片不存在");
        }
        return toVO(card, interactionService.isLiked(
                PublicContentInteractionService.RESOURCE_STYLE_CARD,
                id,
                currentUserId
        ));
    }

    @Transactional(rollbackFor = Exception.class)
    public StyleCardVO create(StyleCardSaveDTO dto) {
        User currentUser = requireCurrentUser();
        StyleCard card = new StyleCard();
        applyDto(card, dto);
        card.setProjectId(PUBLIC_LIBRARY_PROJECT_ID);
        card.setUserId(currentUser.getId());
        card.setUsername(currentUser.getUsername());
        card.setNickname(currentUser.getNickname());
        card.setAvatar(currentUser.getAvatar());
        card.setLikesCount(0);
        card.setCommentsCount(0);
        card.setStatus(dto.getStatus() == null ? 1 : dto.getStatus());
        cardMapper.insert(card);
        return toVO(card, false);
    }

    @Transactional(rollbackFor = Exception.class)
    public StyleCardVO update(Long id, StyleCardSaveDTO dto) {
        StyleCard card = cardMapper.selectById(id);
        if (card == null) {
            throw new BusinessException("卡片不存在");
        }
        assertOwner(card);
        applyDto(card, dto);
        card.setProjectId(PUBLIC_LIBRARY_PROJECT_ID);
        cardMapper.updateById(card);
        return toVO(card, false);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        StyleCard card = cardMapper.selectById(id);
        if (card == null) {
            throw new BusinessException("卡片不存在");
        }
        assertOwner(card);
        cardMapper.deleteById(id);
    }

    private void applyDto(StyleCard card, StyleCardSaveDTO dto) {
        card.setProjectId(PUBLIC_LIBRARY_PROJECT_ID);
        card.setName(dto.getName());
        card.setType(dto.getType());
        card.setContent(dto.getContent());
        card.setNegativePrompt(dto.getNegativePrompt());
        card.setModelName(dto.getModelName());
        card.setProviderId(dto.getProviderId());
        card.setRefAssetId(dto.getRefAssetId());
        card.setCfg(dto.getCfg());
        card.setSteps(dto.getSteps());
        card.setSize(dto.getSize());
        card.setParamsJson(dto.getParamsJson());
        card.setPreviewUrl(dto.getPreviewUrl());
        card.setTag(dto.getTag());
        if (dto.getStatus() != null) {
            card.setStatus(dto.getStatus());
        }
    }

    private StyleCardVO toVO(StyleCard card, boolean liked) {
        StyleCardVO vo = VoMapper.copy(card, StyleCardVO.class);
        vo.setLiked(liked ? 1 : 0);
        return vo;
    }

    private void applySort(LambdaQueryWrapper<StyleCard> wrapper, String sort) {
        if ("likes".equals(sort)) {
            wrapper.orderByDesc(StyleCard::getLikesCount).orderByDesc(StyleCard::getId);
            return;
        }
        if ("comments".equals(sort)) {
            wrapper.orderByDesc(StyleCard::getCommentsCount).orderByDesc(StyleCard::getId);
            return;
        }
        wrapper.orderByDesc(StyleCard::getId);
    }

    private User requireCurrentUser() {
        User currentUser = userMapper.selectById(SecurityUtil.loginUserId());
        if (currentUser == null) {
            throw new BusinessException("当前用户不存在");
        }
        return currentUser;
    }

    private void assertOwner(StyleCard card) {
        Long currentUserId = SecurityUtil.loginUserId();
        if (card.getUserId() != null && !card.getUserId().equals(currentUserId)) {
            throw new BusinessException("只能编辑或删除自己发布的卡片");
        }
    }
}
