package com.example.aihub.infrastructure.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("community_post")
public class CommunityPost {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String username;
    private String nickname;
    private String avatar;
    private String title;
    private String prompt;
    private String negativePrompt;
    private String modelName;
    private String imageUrl;
    private String category;
    private String tags;
    private Integer likesCount;
    private Integer commentsCount;
    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
