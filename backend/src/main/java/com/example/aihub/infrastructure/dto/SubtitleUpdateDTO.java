package com.example.aihub.infrastructure.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class SubtitleUpdateDTO {
    @NotNull
    private Long id;

    @NotBlank
    private String srtContent;

    private String language;
}
