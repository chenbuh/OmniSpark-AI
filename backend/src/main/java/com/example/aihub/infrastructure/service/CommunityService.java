package com.example.aihub.infrastructure.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.aihub.common.exception.BusinessException;
import com.example.aihub.common.security.UploadAccessSignatureService;
import com.example.aihub.common.result.PageResult;
import com.example.aihub.common.util.VoMapper;
import com.example.aihub.infrastructure.dto.CommunityPostDTO;
import com.example.aihub.infrastructure.entity.CommunityPost;
import com.example.aihub.infrastructure.entity.User;
import com.example.aihub.infrastructure.mapper.CommunityPostMapper;
import com.example.aihub.infrastructure.mapper.UserMapper;
import com.example.aihub.infrastructure.vo.CommunityPostVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class CommunityService {
    private final CommunityPostMapper postMapper;
    private final UserMapper userMapper;
    private final PublicContentInteractionService interactionService;
    private final UploadAccessSignatureService uploadAccessSignatureService;

    public List<CommunityPostVO> list(String category, String search, Long currentUserId) {
        return list(category, search, "newest", currentUserId);
    }

    public List<CommunityPostVO> list(String category, String search, String sort, Long currentUserId) {
        LambdaQueryWrapper<CommunityPost> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CommunityPost::getStatus, 1);
        if (category != null && !category.isBlank() && !"all".equals(category)) {
            wrapper.eq(CommunityPost::getCategory, category);
        }
        if (search != null && !search.isBlank()) {
            wrapper.and(w -> w.like(CommunityPost::getTitle, search)
                    .or().like(CommunityPost::getPrompt, search)
                    .or().like(CommunityPost::getTags, search));
        }
        applySort(wrapper, sort);
        List<CommunityPost> posts = postMapper.selectList(wrapper);
        Set<Long> likedIds = interactionService.findLikedResourceIds(
                PublicContentInteractionService.RESOURCE_COMMUNITY_POST,
                posts.stream().map(CommunityPost::getId).toList(),
                currentUserId
        );
        return posts.stream().map(post -> toVO(post, likedIds.contains(post.getId()))).toList();
    }

    public PageResult<CommunityPostVO> page(String category, String search, String sort, Long currentUserId, long page, long size) {
        LambdaQueryWrapper<CommunityPost> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CommunityPost::getStatus, 1);
        if (category != null && !category.isBlank() && !"all".equals(category)) {
            wrapper.eq(CommunityPost::getCategory, category);
        }
        if (search != null && !search.isBlank()) {
            wrapper.and(w -> w.like(CommunityPost::getTitle, search)
                    .or().like(CommunityPost::getPrompt, search)
                    .or().like(CommunityPost::getTags, search));
        }
        applySort(wrapper, sort);

        var result = postMapper.selectPage(
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(page, size), wrapper);
        Set<Long> likedIds = interactionService.findLikedResourceIds(
                PublicContentInteractionService.RESOURCE_COMMUNITY_POST,
                result.getRecords().stream().map(CommunityPost::getId).toList(),
                currentUserId
        );
        List<CommunityPostVO> records = result.getRecords().stream()
                .map(post -> toVO(post, likedIds.contains(post.getId())))
                .toList();
        return new PageResult<>(result.getTotal(), result.getPages(), records);
    }

    public CommunityPostVO get(Long id, Long currentUserId) {
        CommunityPost post = postMapper.selectById(id);
        if (post == null || post.getStatus() == null || post.getStatus() != 1) {
            throw new BusinessException("帖子不存在");
        }
        return toVO(post, interactionService.isLiked(
                PublicContentInteractionService.RESOURCE_COMMUNITY_POST,
                id,
                currentUserId
        ));
    }

    @Transactional(rollbackFor = Exception.class)
    public CommunityPostVO create(CommunityPostDTO dto, Long userId, String ignoredUsername) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("当前用户不存在");
        }
        CommunityPost post = new CommunityPost();
        post.setUserId(userId);
        post.setUsername(user.getUsername());
        post.setNickname(user.getNickname());
        post.setAvatar(user.getAvatar());
        post.setTitle(dto.getTitle());
        post.setPrompt(dto.getPrompt());
        post.setNegativePrompt(dto.getNegativePrompt());
        post.setModelName(dto.getModelName());
        post.setImageUrl(dto.getImageUrl());
        post.setCategory(dto.getCategory() != null ? dto.getCategory() : "uncategorized");
        post.setTags(dto.getTags());
        post.setLikesCount(0);
        post.setCommentsCount(0);
        post.setStatus(1);
        post.setCreatedAt(LocalDateTime.now());
        postMapper.insert(post);
        return toVO(post, false);
    }

    @Transactional(rollbackFor = Exception.class)
    public CommunityPostVO update(Long id, CommunityPostDTO dto, Long userId) {
        CommunityPost post = postMapper.selectById(id);
        if (post == null || post.getStatus() == null || post.getStatus() != 1) {
            throw new BusinessException("帖子不存在");
        }
        if (!post.getUserId().equals(userId)) {
            throw new BusinessException("只能编辑自己的帖子");
        }
        post.setTitle(dto.getTitle());
        post.setPrompt(dto.getPrompt());
        post.setNegativePrompt(dto.getNegativePrompt());
        post.setModelName(dto.getModelName());
        post.setImageUrl(dto.getImageUrl());
        post.setCategory(dto.getCategory() != null ? dto.getCategory() : "uncategorized");
        post.setTags(dto.getTags());
        postMapper.updateById(post);
        return toVO(post, false);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id, Long userId) {
        CommunityPost post = postMapper.selectById(id);
        if (post == null) {
            throw new BusinessException("帖子不存在");
        }
        if (!post.getUserId().equals(userId)) {
            throw new BusinessException("只能删除自己的帖子");
        }
        postMapper.deleteById(id);
    }

    public List<String> categories() {
        return postMapper.selectList(new LambdaQueryWrapper<CommunityPost>()
                        .eq(CommunityPost::getStatus, 1))
                .stream()
                .map(CommunityPost::getCategory)
                .filter(c -> c != null && !c.isBlank())
                .distinct()
                .toList();
    }

    private CommunityPostVO toVO(CommunityPost post, boolean liked) {
        CommunityPostVO vo = VoMapper.copy(post, CommunityPostVO.class);
        vo.setImageUrl(uploadAccessSignatureService.signUrl(vo.getImageUrl()));
        vo.setLiked(liked ? 1 : 0);
        return vo;
    }

    private void applySort(LambdaQueryWrapper<CommunityPost> wrapper, String sort) {
        if ("likes".equals(sort)) {
            wrapper.orderByDesc(CommunityPost::getLikesCount).orderByDesc(CommunityPost::getId);
            return;
        }
        if ("comments".equals(sort)) {
            wrapper.orderByDesc(CommunityPost::getCommentsCount).orderByDesc(CommunityPost::getId);
            return;
        }
        wrapper.orderByDesc(CommunityPost::getId);
    }
}
