package com.example.aihub.infrastructure.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class PublicCommentSaveDTO {
    private Long parentId;

    @NotBlank
    private String content;
}
