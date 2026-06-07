package com.example.aihub.infrastructure.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class PromptTemplateSaveDTO {
    @NotBlank
    private String name;

    @NotBlank
    private String content;

    private String negativePrompt;
    private String modelName;
    private String tag;
    private Integer status;
}
