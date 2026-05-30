package com.example.aihub.infrastructure.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RegisterDTO {
    @NotBlank
    private String username;

    @NotBlank
    private String passwordHash;

    @NotBlank
    private String nickname;
}
