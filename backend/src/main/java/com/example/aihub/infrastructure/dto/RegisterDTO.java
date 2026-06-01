package com.example.aihub.infrastructure.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RegisterDTO {
    @NotBlank
    private String username;

    /** 解密后的明文密码载体（由 Controller 通过 encryptedPassword 解密回写）。 */
    private String password;

    /** 传输加密后的密码字段，前端实际提交的字段。 */
    private String encryptedPassword;

    /** 滑块验证码通过后获得的一次性票据，注册时核销。 */
    private String captchaTicket;

    @NotBlank
    private String nickname;
}
