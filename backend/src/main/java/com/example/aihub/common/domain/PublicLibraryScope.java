package com.example.aihub.common.domain;

/**
 * 公共模板库 / 风格卡库当前复用项目维度存储，统一落在保留槽位 `project_id = 0`。
 * 这是内部存储实现细节，不应继续向接口协议或页面语义扩散。
 */
public final class PublicLibraryScope {
    private static final long STORAGE_PROJECT_ID = 0L;

    private PublicLibraryScope() {
    }

    public static long storageProjectId() {
        return STORAGE_PROJECT_ID;
    }

    public static boolean matches(Long projectId) {
        return projectId != null && projectId == STORAGE_PROJECT_ID;
    }
}
