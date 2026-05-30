package com.example.aihub.infrastructure.vo;

import lombok.Data;

@Data
public class ModelProviderVO {
    private Long id;
    private Long projectId;
    private String name;
    private String type;
    private String baseUrl;
    private String apiKey;
    private String modelName;
    private Integer enabled;
    private Integer isDefault;
    private String configJson;
    private java.time.LocalDateTime createdAt;
}
