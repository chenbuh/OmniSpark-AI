package com.example.aihub.common.util;

public final class PagingUtil {
    public static final long DEFAULT_PAGE = 1L;
    public static final long DEFAULT_PAGE_SIZE = 20L;
    public static final long MAX_PAGE_SIZE = 100L;
    public static final int DEFAULT_LIMIT = 20;
    public static final int MAX_LIMIT = 100;

    private PagingUtil() {
    }

    public static long normalizePage(long page) {
        return page < 1 ? DEFAULT_PAGE : page;
    }

    public static long clampPageSize(long pageSize) {
        return clampPageSize(pageSize, DEFAULT_PAGE_SIZE, MAX_PAGE_SIZE);
    }

    public static long clampPageSize(long pageSize, long defaultPageSize) {
        return clampPageSize(pageSize, defaultPageSize, MAX_PAGE_SIZE);
    }

    public static long clampPageSize(long pageSize, long defaultPageSize, long maxPageSize) {
        long safeDefault = defaultPageSize < 1 ? DEFAULT_PAGE_SIZE : defaultPageSize;
        long safeMax = maxPageSize < 1 ? MAX_PAGE_SIZE : maxPageSize;
        if (pageSize < 1) {
            return Math.min(safeDefault, safeMax);
        }
        return Math.min(pageSize, safeMax);
    }

    public static int clampLimit(int limit) {
        return clampLimit(limit, DEFAULT_LIMIT, MAX_LIMIT);
    }

    public static int clampLimit(int limit, int defaultLimit) {
        return clampLimit(limit, defaultLimit, MAX_LIMIT);
    }

    public static int clampLimit(int limit, int defaultLimit, int maxLimit) {
        int safeDefault = defaultLimit < 1 ? DEFAULT_LIMIT : defaultLimit;
        int safeMax = maxLimit < 1 ? MAX_LIMIT : maxLimit;
        if (limit < 1) {
            return Math.min(safeDefault, safeMax);
        }
        return Math.min(limit, safeMax);
    }
}
