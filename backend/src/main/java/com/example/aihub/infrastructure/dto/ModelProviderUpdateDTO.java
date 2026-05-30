package com.example.aihub.infrastructure.dto;

import lombok.Data;

@Data
public class ModelProviderUpdateDTO {
    private Long projectId;
    private String name;
    private String type;
    private String baseUrl;
    private String apiKey;
    private String modelName;
    private Boolean enabled;
    private Boolean isDefault;
    private String configJson;
}
