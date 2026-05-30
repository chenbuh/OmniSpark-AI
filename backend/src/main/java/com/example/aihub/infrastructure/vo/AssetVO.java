package com.example.aihub.infrastructure.vo;

import lombok.Data;

@Data
public class AssetVO {
    private Long id;
    private Long projectId;
    private Long taskId;
    private String assetType;
    private String fileName;
    private String fileUrl;
    private String thumbUrl;
    private String mimeType;
    private Long fileSize;
    private String prompt;
    private String modelName;
    private Integer favorite;
    private java.time.LocalDateTime createdAt;
}
