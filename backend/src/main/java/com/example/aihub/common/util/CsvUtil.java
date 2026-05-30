package com.example.aihub.common.util;

/**
 * CSV 输出工具:对字段做引号转义并中和公式注入,防止导出文件在 Excel/WPS 打开时
 * 触发公式执行(CSV Injection),以及字段含逗号/换行导致的列错位。
 */
public final class CsvUtil {
    private CsvUtil() {
    }

    /** 转义单个 CSV 字段:中和公式前缀、按 RFC 4180 处理引号/逗号/换行。 */
    public static String escape(Object value) {
        String s = value == null ? "" : String.valueOf(value);

        // 公式注入防护:以 = + - @ 制表符 回车 开头的字段前置单引号,使其被当作文本
        if (!s.isEmpty()) {
            char first = s.charAt(0);
            if (first == '=' || first == '+' || first == '-' || first == '@'
                    || first == '\t' || first == '\r') {
                s = "'" + s;
            }
        }

        // 含逗号/引号/换行时用双引号包裹,内部引号翻倍
        if (s.contains(",") || s.contains("\"") || s.contains("\n") || s.contains("\r")) {
            s = "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }
}
