package com.example.aihub.infrastructure.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.aihub.common.domain.PublicLibraryScope;
import com.example.aihub.common.exception.BusinessException;
import com.example.aihub.common.result.PageResult;
import com.example.aihub.common.security.UploadAccessSignatureService;
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
    private final StyleCardMapper cardMapper;
    private final UserMapper userMapper;
    private final PublicContentInteractionService interactionService;
    private final UploadAccessSignatureService uploadAccessSignatureService;

    public PageResult<StyleCardVO> page(String type, String search, String sort,
                                        Long currentUserId, long page, long size) {
        LambdaQueryWrapper<StyleCard> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(StyleCard::getProjectId, PublicLibraryScope.storageProjectId());
        wrapper.eq(StyleCard::getStatus, 1);
        if (type != null && !type.isBlank()) {
            wrapper.eq(StyleCard::getType, type);
        }
        if (search != null && !search.isBlank()) {
            wrapper.and(w -> w.like(StyleCard::getName, search)
                    .or().like(StyleCard::getContent, search)
                    .or().like(StyleCard::getTag, search));
        }
        applySort(wrapper, sort);

        var result = cardMapper.selectPage(new Page<>(page, size), wrapper);
        List<StyleCard> cards = result.getRecords();
        Set<Long> likedIds = interactionService.findLikedResourceIds(
                PublicContentInteractionService.RESOURCE_STYLE_CARD,
                cards.stream().map(StyleCard::getId).toList(),
                currentUserId
        );
        List<StyleCardVO> records = cards.stream()
                .map(card -> toVO(card, likedIds.contains(card.getId())))
                .toList();
        return new PageResult<>(result.getTotal(), result.getPages(), records);
    }

    public StyleCardVO get(Long id, Long currentUserId) {
        StyleCard card = cardMapper.selectById(id);
        if (card == null
                || !PublicLibraryScope.matches(card.getProjectId())
                || card.getStatus() == null
                || card.getStatus() != 1) {
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
        if (card == null || !PublicLibraryScope.matches(card.getProjectId())) {
            throw new BusinessException("卡片不存在");
        }
        assertOwner(card);
        applyDto(card, dto);
        cardMapper.updateById(card);
        return toVO(card, false);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        StyleCard card = cardMapper.selectById(id);
        if (card == null || !PublicLibraryScope.matches(card.getProjectId())) {
            throw new BusinessException("卡片不存在");
        }
        assertOwner(card);
        cardMapper.deleteById(id);
    }

    public List<String> tags() {
        return cardMapper.selectList(new LambdaQueryWrapper<StyleCard>()
                        .eq(StyleCard::getProjectId, PublicLibraryScope.storageProjectId())
                        .eq(StyleCard::getStatus, 1))
                .stream()
                .map(StyleCard::getTag)
                .filter(tag -> tag != null && !tag.isBlank())
                .map(String::trim)
                .distinct()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    private void applyDto(StyleCard card, StyleCardSaveDTO dto) {
        card.setProjectId(PublicLibraryScope.storageProjectId());
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
        vo.setPreviewUrl(uploadAccessSignatureService.signUrl(vo.getPreviewUrl()));
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
