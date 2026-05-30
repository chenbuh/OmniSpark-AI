package com.example.aihub.infrastructure.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.aihub.common.exception.BusinessException;
import com.example.aihub.common.util.VoMapper;
import com.example.aihub.infrastructure.dto.CommunityPostDTO;
import com.example.aihub.infrastructure.entity.CommunityLike;
import com.example.aihub.infrastructure.entity.CommunityPost;
import com.example.aihub.infrastructure.mapper.CommunityLikeMapper;
import com.example.aihub.infrastructure.mapper.CommunityPostMapper;
import com.example.aihub.infrastructure.vo.CommunityPostVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CommunityService {
    private final CommunityPostMapper postMapper;
    private final CommunityLikeMapper likeMapper;

    public List<CommunityPostVO> list(String category, String search, Long currentUserId) {
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
        wrapper.orderByDesc(CommunityPost::getId);

        List<CommunityPost> posts = postMapper.selectList(wrapper);

        // 查询当前用户的点赞
        Set<Long> likedIds = Set.of();
        if (currentUserId != null) {
            likedIds = likeMapper.selectList(
                    new LambdaQueryWrapper<CommunityLike>().eq(CommunityLike::getUserId, currentUserId))
                    .stream().map(CommunityLike::getPostId).collect(Collectors.toSet());
        }

        Set<Long> finalLikedIds = likedIds;
        return posts.stream().map(p -> {
            CommunityPostVO vo = VoMapper.copy(p, CommunityPostVO.class);
            vo.setLiked(finalLikedIds.contains(p.getId()) ? 1 : 0);
            return vo;
        }).toList();
    }

    public CommunityPostVO get(Long id, Long currentUserId) {
        CommunityPost post = postMapper.selectById(id);
        if (post == null) throw new BusinessException("帖子不存在");
        CommunityPostVO vo = VoMapper.copy(post, CommunityPostVO.class);
        if (currentUserId != null) {
            Long count = likeMapper.selectCount(new LambdaQueryWrapper<CommunityLike>()
                    .eq(CommunityLike::getPostId, id).eq(CommunityLike::getUserId, currentUserId));
            vo.setLiked(count != null && count > 0 ? 1 : 0);
        }
        return vo;
    }

    @Transactional(rollbackFor = Exception.class)
    public CommunityPostVO create(CommunityPostDTO dto, Long userId, String username) {
        CommunityPost post = new CommunityPost();
        post.setUserId(userId);
        post.setUsername(username);
        post.setTitle(dto.getTitle());
        post.setPrompt(dto.getPrompt());
        post.setNegativePrompt(dto.getNegativePrompt());
        post.setModelName(dto.getModelName());
        post.setImageUrl(dto.getImageUrl());
        post.setCategory(dto.getCategory() != null ? dto.getCategory() : "uncategorized");
        post.setTags(dto.getTags());
        post.setLikesCount(0);
        post.setStatus(1);
        post.setCreatedAt(LocalDateTime.now());
        postMapper.insert(post);
        return VoMapper.copy(post, CommunityPostVO.class);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id, Long userId) {
        CommunityPost post = postMapper.selectById(id);
        if (post == null) throw new BusinessException("帖子不存在");
        if (!post.getUserId().equals(userId)) throw new BusinessException("只能删除自己的帖子");
        postMapper.deleteById(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public int toggleLike(Long postId, Long userId) {
        CommunityPost post = postMapper.selectById(postId);
        if (post == null) throw new BusinessException("帖子不存在");

        Long existing = likeMapper.selectCount(new LambdaQueryWrapper<CommunityLike>()
                .eq(CommunityLike::getPostId, postId).eq(CommunityLike::getUserId, userId));
        if (existing != null && existing > 0) {
            likeMapper.delete(new LambdaQueryWrapper<CommunityLike>()
                    .eq(CommunityLike::getPostId, postId).eq(CommunityLike::getUserId, userId));
            post.setLikesCount(Math.max(0, (post.getLikesCount() == null ? 0 : post.getLikesCount()) - 1));
            postMapper.updateById(post);
            return 0; // 取消点赞
        } else {
            CommunityLike like = new CommunityLike();
            like.setPostId(postId);
            like.setUserId(userId);
            like.setCreatedAt(LocalDateTime.now());
            likeMapper.insert(like);
            post.setLikesCount((post.getLikesCount() == null ? 0 : post.getLikesCount()) + 1);
            postMapper.updateById(post);
            return 1; // 已点赞
        }
    }

    public List<String> categories() {
        return postMapper.selectList(new LambdaQueryWrapper<CommunityPost>()
                        .eq(CommunityPost::getStatus, 1))
                .stream().map(CommunityPost::getCategory).filter(c -> c != null && !c.isBlank()).distinct().toList();
    }
}
