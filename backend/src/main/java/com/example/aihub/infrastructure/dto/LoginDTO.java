package com.example.aihub.infrastructure.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LoginDTO {
    @NotBlank
    private String username;

    private String password;

    private String encryptedPassword;

    /** 滑块验证码通过后获得的一次性票据，登录时核销。 */
    private String captchaTicket;

    private String deviceId;
}
