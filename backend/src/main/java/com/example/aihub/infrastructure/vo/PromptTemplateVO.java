package com.example.aihub.infrastructure.vo;

import lombok.Data;

@Data
public class PromptTemplateVO {
    private Long id;
    private Long projectId;
    private String name;
    private String content;
    private String tag;
    private Integer status;
    private java.time.LocalDateTime createdAt;
}
