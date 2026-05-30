package com.example.aihub.infrastructure.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CommunityPostDTO {
    @NotBlank
    private String title;

    @NotBlank
    private String prompt;

    private String negativePrompt;
    private String modelName;
    private String imageUrl;
    private String category;
    private String tags;
}
