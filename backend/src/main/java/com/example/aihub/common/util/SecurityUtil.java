package com.example.aihub.common.util;

import cn.dev33.satoken.stp.StpUtil;

public final class SecurityUtil {
    private SecurityUtil() {
    }

    public static Long loginUserId() {
        return Long.valueOf(String.valueOf(StpUtil.getLoginId()));
    }

    public static Long tryLoginUserId() {
        try {
            if (!StpUtil.isLogin()) {
                return null;
            }
            return StpUtil.getLoginIdAsLong();
        } catch (Exception ignored) {
            return null;
        }
    }
}
