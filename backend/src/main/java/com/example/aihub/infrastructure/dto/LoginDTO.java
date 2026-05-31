package com.example.aihub.infrastructure.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LoginDTO {
    @NotBlank
    private String username;

    private String password;

    private String encryptedPassword;
}
