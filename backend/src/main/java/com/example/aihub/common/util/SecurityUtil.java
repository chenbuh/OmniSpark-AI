package com.example.aihub.common.util;

import cn.dev33.satoken.stp.StpUtil;

public final class SecurityUtil {
    private SecurityUtil() {
    }

    public static Long loginUserId() {
        return Long.valueOf(String.valueOf(StpUtil.getLoginId()));
    }
}
