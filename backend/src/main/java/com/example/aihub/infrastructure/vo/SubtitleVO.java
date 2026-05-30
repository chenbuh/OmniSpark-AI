package com.example.aihub.infrastructure.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class SubtitleVO {
    private Long id;
    private Long assetId;
    private Long projectId;
    private String language;
    private String srtContent;
    private Integer status;
    private String voiceUrl;
    private LocalDateTime createdAt;
}
