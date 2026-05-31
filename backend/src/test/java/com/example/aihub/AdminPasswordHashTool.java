package com.example.aihub;

import com.example.aihub.common.util.PasswordUtil;

/**
 * 本地一次性工具：为指定管理员账号生成 BCrypt（cost=12）哈希，并打印可直接执行的 UPDATE SQL。
 *
 * 用法（在 IDEA 中）：
 *   1. 改下面两个常量：USERNAME（你的管理员用户名）、RAW_PASSWORD（想设置的新密码）。
 *   2. 右键 main 方法 -> Run。
 *   3. 复制控制台打印的 UPDATE 语句，到 MySQL 里执行。
 *
 * 安全提示：
 *   - 密码明文只存在于你本地，不要把 RAW_PASSWORD 提交到 git；用完把它改回占位值。
 *   - 哈希用的是项目现网同一套 PasswordUtil.encode()，与注册/改密完全一致，登录可直接通过。
 */
public class AdminPasswordHashTool {

    /** 改成你的管理员用户名。 */
    private static final String USERNAME = "admin";

    /** 改成你想设置的新密码（至少 8 位，避开常见弱密码、不要与账号相同）。 */
    private static final String RAW_PASSWORD = "CHANGE_ME_please";

    public static void main(String[] args) {
        // 先按现网同一套策略校验，避免生成一个登录时会被策略拒绝的密码
        String error = PasswordUtil.getPasswordValidationError(RAW_PASSWORD, USERNAME);
        if (error != null) {
            System.out.println("[拒绝] 密码不符合策略：" + error);
            return;
        }

        String hash = PasswordUtil.encode(RAW_PASSWORD);

        // 自检：确认生成的哈希能被 matches 校验通过
        boolean ok = PasswordUtil.matches(RAW_PASSWORD, hash);
        Integer cost = PasswordUtil.getBcryptCost(hash);

        System.out.println("============ 管理员密码哈希 ============");
        System.out.println("用户名 : " + USERNAME);
        System.out.println("哈希   : " + hash);
        System.out.println("cost   : " + cost + "   自检校验 : " + (ok ? "通过" : "失败"));
        System.out.println("--------------------------------------");
        System.out.println("把下面这条 SQL 拷到 MySQL 执行：");
        System.out.println();
        System.out.println("UPDATE `user` SET `password` = '" + hash + "' WHERE `username` = '" + USERNAME + "';");
        System.out.println();
        System.out.println("执行后用新密码登录即可。记得把本文件的 RAW_PASSWORD 改回占位值。");
        System.out.println("======================================");
    }
}
