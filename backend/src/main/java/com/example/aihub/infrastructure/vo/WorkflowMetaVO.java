package com.example.aihub.infrastructure.vo;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class WorkflowMetaVO {
    private String scope;
    private String message;
    private List<Map<String, String>> stepTypes;
    private List<Map<String, String>> imageSizes;
    private List<Map<String, String>> videoDurations;
    private List<Map<String, String>> subtitleLanguages;
    private Defaults defaults;

    @Data
    public static class Defaults {
        private String imageSize;
        private String videoDuration;
        private String subtitleLanguage;
    }
}
