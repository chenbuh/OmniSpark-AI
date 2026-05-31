package com.example.aihub.infrastructure.vo;

import lombok.Data;

@Data
public class PromptTemplateVO {
    private Long id;
    private Long projectId;
    private Long userId;
    private String username;
    private String nickname;
    private String avatar;
    private String name;
    private String content;
    private String negativePrompt;
    private String modelName;
    private String tag;
    private Integer likesCount;
    private Integer commentsCount;
    private Integer liked;
    private Integer status;
    private java.time.LocalDateTime createdAt;
}
