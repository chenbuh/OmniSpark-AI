package com.example.aihub.infrastructure.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.aihub.common.exception.BusinessException;
import com.example.aihub.common.util.VoMapper;
import com.example.aihub.infrastructure.dto.StyleCardSaveDTO;
import com.example.aihub.infrastructure.entity.StyleCard;
import com.example.aihub.infrastructure.mapper.StyleCardMapper;
import com.example.aihub.infrastructure.vo.StyleCardVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class StyleCardService {
    private static final long PUBLIC_LIBRARY_PROJECT_ID = 0L;

    private final StyleCardMapper cardMapper;

    public List<StyleCardVO> list(Long projectId, String type) {
        LambdaQueryWrapper<StyleCard> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(StyleCard::getStatus, 1);
        if (type != null && !type.isBlank()) {
            wrapper.eq(StyleCard::getType, type);
        }
        wrapper.orderByDesc(StyleCard::getId);
        return cardMapper.selectList(wrapper).stream().map(this::toVO).toList();
    }

    public StyleCardVO get(Long id) {
        StyleCard card = cardMapper.selectById(id);
        if (card == null) {
            throw new BusinessException("卡片不存在");
        }
        return toVO(card);
    }

    @Transactional(rollbackFor = Exception.class)
    public StyleCardVO create(StyleCardSaveDTO dto) {
        StyleCard card = new StyleCard();
        applyDto(card, dto);
        card.setProjectId(PUBLIC_LIBRARY_PROJECT_ID);
        card.setStatus(dto.getStatus() == null ? 1 : dto.getStatus());
        cardMapper.insert(card);
        return toVO(card);
    }

    @Transactional(rollbackFor = Exception.class)
    public StyleCardVO update(Long id, StyleCardSaveDTO dto) {
        StyleCard card = cardMapper.selectById(id);
        if (card == null) {
            throw new BusinessException("卡片不存在");
        }
        applyDto(card, dto);
        card.setProjectId(PUBLIC_LIBRARY_PROJECT_ID);
        cardMapper.updateById(card);
        return toVO(card);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        StyleCard card = cardMapper.selectById(id);
        if (card == null) {
            throw new BusinessException("卡片不存在");
        }
        cardMapper.deleteById(id);
    }

    private void applyDto(StyleCard card, StyleCardSaveDTO dto) {
        card.setProjectId(dto.getProjectId());
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

    private StyleCardVO toVO(StyleCard card) {
        return VoMapper.copy(card, StyleCardVO.class);
    }
}
