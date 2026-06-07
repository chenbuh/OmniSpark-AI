package com.example.aihub.infrastructure.vo;

import lombok.Data;

@Data
public class LoginVO {
    private String token;
    private UserVO userInfo;
    private Boolean requiresTotp;
    private Boolean requiresTotpSetup;
    private String loginTicket;
    private String setupTicket;
    private String totpSecret;
    private String totpOtpauthUrl;
    private String totpIssuer;
}
