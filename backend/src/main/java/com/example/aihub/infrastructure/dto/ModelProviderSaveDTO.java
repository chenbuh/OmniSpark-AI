package com.example.aihub.infrastructure.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ModelProviderSaveDTO {
    @NotNull
    private Long projectId;

    @NotBlank
    private String name;

    @NotBlank
    private String type;

    @NotBlank
    private String baseUrl;

    @NotBlank
    private String apiKey;

    @NotBlank
    private String modelName;

    private Boolean enabled;

    private Boolean isDefault;

    private String configJson;
}
