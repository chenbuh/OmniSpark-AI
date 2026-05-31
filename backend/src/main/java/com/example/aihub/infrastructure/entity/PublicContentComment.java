package com.example.aihub.infrastructure.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("public_content_comment")
public class PublicContentComment extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String resourceType;
    private Long resourceId;
    private Long parentId;
    private Long userId;
    private String username;
    private String nickname;
    private String avatar;
    private Long replyToUserId;
    private String replyToUsername;
    private String replyToNickname;
    private String content;
    private Integer status;
}
