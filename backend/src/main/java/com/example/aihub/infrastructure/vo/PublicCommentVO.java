package com.example.aihub.infrastructure.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class PublicCommentVO {
    private Long id;
    private Long parentId;
    private Long userId;
    private String username;
    private String nickname;
    private String avatar;
    private String replyToUsername;
    private String replyToNickname;
    private String content;
    private LocalDateTime createdAt;
    private List<PublicCommentVO> replies = new ArrayList<>();
}
