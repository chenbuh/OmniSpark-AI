package com.example.aihub.infrastructure.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CommunityPostVO {
    private Long id;
    private Long userId;
    private String username;
    private String title;
    private String prompt;
    private String negativePrompt;
    private String modelName;
    private String imageUrl;
    private String category;
    private String tags;
    private Integer likesCount;
    private Integer liked; // 当前用户是否已点赞
    private Integer status;
    private LocalDateTime createdAt;
}
