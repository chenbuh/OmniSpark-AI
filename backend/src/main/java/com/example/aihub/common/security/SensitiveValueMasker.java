package com.example.aihub.common.security;

public final class SensitiveValueMasker {
    public static final String MASK_SEGMENT = "****";
    public static final String FULL_MASK = "********";

    private SensitiveValueMasker() {}

    public static String maskFully(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return FULL_MASK;
    }

    public static String maskKeepingEdges(String value, int prefixLength, int suffixLength) {
        if (value == null || value.isBlank()) {
            return value;
        }
        String trimmed = value.trim();
        if (trimmed.length() <= prefixLength + suffixLength) {
            return MASK_SEGMENT;
        }
        return trimmed.substring(0, prefixLength) + MASK_SEGMENT + trimmed.substring(trimmed.length() - suffixLength);
    }

    public static boolean looksMasked(String value) {
        return value != null && value.contains(MASK_SEGMENT);
    }
}
