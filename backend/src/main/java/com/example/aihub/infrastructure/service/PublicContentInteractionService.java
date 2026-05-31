package com.example.aihub.infrastructure.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.aihub.common.exception.BusinessException;
import com.example.aihub.common.util.VoMapper;
import com.example.aihub.infrastructure.dto.PublicCommentSaveDTO;
import com.example.aihub.infrastructure.entity.CommunityPost;
import com.example.aihub.infrastructure.entity.PromptTemplate;
import com.example.aihub.infrastructure.entity.PublicContentComment;
import com.example.aihub.infrastructure.entity.PublicContentLike;
import com.example.aihub.infrastructure.entity.StyleCard;
import com.example.aihub.infrastructure.entity.User;
import com.example.aihub.infrastructure.mapper.CommunityPostMapper;
import com.example.aihub.infrastructure.mapper.PromptTemplateMapper;
import com.example.aihub.infrastructure.mapper.PublicContentCommentMapper;
import com.example.aihub.infrastructure.mapper.PublicContentLikeMapper;
import com.example.aihub.infrastructure.mapper.StyleCardMapper;
import com.example.aihub.infrastructure.mapper.UserMapper;
import com.example.aihub.infrastructure.vo.PublicCommentVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PublicContentInteractionService {
    public static final String RESOURCE_PROMPT_TEMPLATE = "prompt_template";
    public static final String RESOURCE_STYLE_CARD = "style_card";
    public static final String RESOURCE_COMMUNITY_POST = "community_post";

    private final PublicContentLikeMapper likeMapper;
    private final PublicContentCommentMapper commentMapper;
    private final PromptTemplateMapper promptTemplateMapper;
    private final StyleCardMapper styleCardMapper;
    private final CommunityPostMapper communityPostMapper;
    private final UserMapper userMapper;

    public Set<Long> findLikedResourceIds(String resourceType, List<Long> resourceIds, Long userId) {
        if (userId == null || resourceIds == null || resourceIds.isEmpty()) {
            return Set.of();
        }
        return likeMapper.selectList(new LambdaQueryWrapper<PublicContentLike>()
                        .eq(PublicContentLike::getResourceType, resourceType)
                        .eq(PublicContentLike::getUserId, userId)
                        .in(PublicContentLike::getResourceId, resourceIds))
                .stream()
                .map(PublicContentLike::getResourceId)
                .collect(Collectors.toSet());
    }

    public boolean isLiked(String resourceType, Long resourceId, Long userId) {
        if (userId == null) {
            return false;
        }
        Long count = likeMapper.selectCount(new LambdaQueryWrapper<PublicContentLike>()
                .eq(PublicContentLike::getResourceType, resourceType)
                .eq(PublicContentLike::getResourceId, resourceId)
                .eq(PublicContentLike::getUserId, userId));
        return count != null && count > 0;
    }

    @Transactional(rollbackFor = Exception.class)
    public int toggleLike(String resourceType, Long resourceId, Long userId) {
        ensureResourceExists(resourceType, resourceId);
        Long count = likeMapper.selectCount(new LambdaQueryWrapper<PublicContentLike>()
                .eq(PublicContentLike::getResourceType, resourceType)
                .eq(PublicContentLike::getResourceId, resourceId)
                .eq(PublicContentLike::getUserId, userId));
        if (count != null && count > 0) {
            likeMapper.delete(new LambdaQueryWrapper<PublicContentLike>()
                    .eq(PublicContentLike::getResourceType, resourceType)
                    .eq(PublicContentLike::getResourceId, resourceId)
                    .eq(PublicContentLike::getUserId, userId));
            adjustLikeCount(resourceType, resourceId, -1);
            return 0;
        }
        PublicContentLike like = new PublicContentLike();
        like.setResourceType(resourceType);
        like.setResourceId(resourceId);
        like.setUserId(userId);
        likeMapper.insert(like);
        adjustLikeCount(resourceType, resourceId, 1);
        return 1;
    }

    public List<PublicCommentVO> listComments(String resourceType, Long resourceId) {
        ensureResourceExists(resourceType, resourceId);
        List<PublicContentComment> comments = commentMapper.selectList(new LambdaQueryWrapper<PublicContentComment>()
                .eq(PublicContentComment::getResourceType, resourceType)
                .eq(PublicContentComment::getResourceId, resourceId)
                .eq(PublicContentComment::getStatus, 1)
                .orderByAsc(PublicContentComment::getCreatedAt)
                .orderByAsc(PublicContentComment::getId));
        Map<Long, PublicCommentVO> byId = new LinkedHashMap<>();
        List<PublicCommentVO> roots = new ArrayList<>();
        for (PublicContentComment comment : comments) {
            PublicCommentVO vo = VoMapper.copy(comment, PublicCommentVO.class);
            vo.setReplies(new ArrayList<>());
            byId.put(vo.getId(), vo);
            if (comment.getParentId() == null || comment.getParentId() == 0L) {
                roots.add(vo);
                continue;
            }
            PublicCommentVO parent = byId.get(comment.getParentId());
            if (parent != null) {
                parent.getReplies().add(vo);
            } else {
                roots.add(vo);
            }
        }
        return roots;
    }

    @Transactional(rollbackFor = Exception.class)
    public PublicCommentVO createComment(String resourceType, Long resourceId, Long userId, PublicCommentSaveDTO dto) {
        ensureResourceExists(resourceType, resourceId);
        User user = requireUser(userId);

        PublicContentComment parent = null;
        if (dto.getParentId() != null) {
            parent = commentMapper.selectById(dto.getParentId());
            if (parent == null
                    || !resourceType.equals(parent.getResourceType())
                    || !resourceId.equals(parent.getResourceId())
                    || parent.getStatus() == null
                    || parent.getStatus() != 1) {
                throw new BusinessException("回复目标不存在");
            }
        }

        PublicContentComment comment = new PublicContentComment();
        comment.setResourceType(resourceType);
        comment.setResourceId(resourceId);
        comment.setParentId(parent == null ? null : (parent.getParentId() == null ? parent.getId() : parent.getParentId()));
        comment.setUserId(user.getId());
        comment.setUsername(user.getUsername());
        comment.setNickname(user.getNickname());
        comment.setAvatar(user.getAvatar());
        if (parent != null) {
            comment.setReplyToUserId(parent.getUserId());
            comment.setReplyToUsername(parent.getUsername());
            comment.setReplyToNickname(parent.getNickname());
        }
        comment.setContent(dto.getContent().trim());
        comment.setStatus(1);
        commentMapper.insert(comment);
        adjustCommentCount(resourceType, resourceId, 1);
        PublicCommentVO vo = VoMapper.copy(comment, PublicCommentVO.class);
        vo.setReplies(new ArrayList<>());
        return vo;
    }

    @Transactional(rollbackFor = Exception.class)
    public int deleteComment(String resourceType, Long resourceId, Long commentId, Long userId) {
        ensureResourceExists(resourceType, resourceId);
        PublicContentComment comment = commentMapper.selectById(commentId);
        if (comment == null
                || comment.getStatus() == null
                || comment.getStatus() != 1
                || !resourceType.equals(comment.getResourceType())
                || !resourceId.equals(comment.getResourceId())) {
            throw new BusinessException("评论不存在");
        }
        if (!comment.getUserId().equals(userId)) {
            throw new BusinessException("只能删除自己的评论");
        }

        LambdaQueryWrapper<PublicContentComment> deleteWrapper = new LambdaQueryWrapper<PublicContentComment>()
                .eq(PublicContentComment::getResourceType, resourceType)
                .eq(PublicContentComment::getResourceId, resourceId);
        if (comment.getParentId() == null || comment.getParentId() == 0L) {
            deleteWrapper.and(wrapper -> wrapper.eq(PublicContentComment::getId, commentId)
                    .or()
                    .eq(PublicContentComment::getParentId, commentId));
        } else {
            deleteWrapper.eq(PublicContentComment::getId, commentId);
        }
        int deleted = commentMapper.delete(deleteWrapper);
        if (deleted > 0) {
            adjustCommentCount(resourceType, resourceId, -deleted);
        }
        return deleted;
    }

    private User requireUser(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        return user;
    }

    private void ensureResourceExists(String resourceType, Long resourceId) {
        switch (resourceType) {
            case RESOURCE_PROMPT_TEMPLATE -> {
                PromptTemplate template = promptTemplateMapper.selectById(resourceId);
                if (template == null || template.getStatus() == null || template.getStatus() != 1) {
                    throw new BusinessException("模板不存在");
                }
            }
            case RESOURCE_STYLE_CARD -> {
                StyleCard card = styleCardMapper.selectById(resourceId);
                if (card == null || card.getStatus() == null || card.getStatus() != 1) {
                    throw new BusinessException("卡片不存在");
                }
            }
            case RESOURCE_COMMUNITY_POST -> {
                CommunityPost post = communityPostMapper.selectById(resourceId);
                if (post == null || post.getStatus() == null || post.getStatus() != 1) {
                    throw new BusinessException("帖子不存在");
                }
            }
            default -> throw new BusinessException("不支持的公共内容类型");
        }
    }

    private void adjustLikeCount(String resourceType, Long resourceId, int delta) {
        switch (resourceType) {
            case RESOURCE_PROMPT_TEMPLATE -> {
                PromptTemplate template = promptTemplateMapper.selectById(resourceId);
                if (template != null) {
                    template.setLikesCount(Math.max(0, safeCount(template.getLikesCount()) + delta));
                    promptTemplateMapper.updateById(template);
                }
            }
            case RESOURCE_STYLE_CARD -> {
                StyleCard card = styleCardMapper.selectById(resourceId);
                if (card != null) {
                    card.setLikesCount(Math.max(0, safeCount(card.getLikesCount()) + delta));
                    styleCardMapper.updateById(card);
                }
            }
            case RESOURCE_COMMUNITY_POST -> {
                CommunityPost post = communityPostMapper.selectById(resourceId);
                if (post != null) {
                    post.setLikesCount(Math.max(0, safeCount(post.getLikesCount()) + delta));
                    communityPostMapper.updateById(post);
                }
            }
            default -> throw new BusinessException("不支持的公共内容类型");
        }
    }

    private void adjustCommentCount(String resourceType, Long resourceId, int delta) {
        switch (resourceType) {
            case RESOURCE_PROMPT_TEMPLATE -> {
                PromptTemplate template = promptTemplateMapper.selectById(resourceId);
                if (template != null) {
                    template.setCommentsCount(Math.max(0, safeCount(template.getCommentsCount()) + delta));
                    promptTemplateMapper.updateById(template);
                }
            }
            case RESOURCE_STYLE_CARD -> {
                StyleCard card = styleCardMapper.selectById(resourceId);
                if (card != null) {
                    card.setCommentsCount(Math.max(0, safeCount(card.getCommentsCount()) + delta));
                    styleCardMapper.updateById(card);
                }
            }
            case RESOURCE_COMMUNITY_POST -> {
                CommunityPost post = communityPostMapper.selectById(resourceId);
                if (post != null) {
                    post.setCommentsCount(Math.max(0, safeCount(post.getCommentsCount()) + delta));
                    communityPostMapper.updateById(post);
                }
            }
            default -> throw new BusinessException("不支持的公共内容类型");
        }
    }

    private int safeCount(Integer value) {
        return value == null ? 0 : value;
    }
}
