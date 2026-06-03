package com.example.aihub.common.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Component
public class UploadStorageResolver {
    private static final String DEFAULT_UPLOAD_DIR = "uploads";
    private static final String LOCAL_UPLOAD_PREFIX = "/uploads/";

    private final Path uploadRoot;

    public UploadStorageResolver(@Value("${file.upload-dir:" + DEFAULT_UPLOAD_DIR + "}") String configuredUploadDir) {
        this.uploadRoot = resolveUploadRoot(configuredUploadDir);
    }

    public Path getUploadRoot() {
        return uploadRoot;
    }

    public Path resolve(String first, String... more) {
        return uploadRoot.resolve(Paths.get(first, more)).normalize();
    }

    public Path resolveLocalUploadPath(String rawUrl) {
        String relativePath = extractRelativeUploadPath(rawUrl);
        if (relativePath == null || relativePath.isBlank()) {
            return null;
        }
        Path resolved = uploadRoot.resolve(relativePath).normalize();
        return resolved.startsWith(uploadRoot) ? resolved : null;
    }

    public String asResourceLocation() {
        String location = uploadRoot.toUri().toString();
        return location.endsWith("/") ? location : location + "/";
    }

    private Path resolveUploadRoot(String configuredUploadDir) {
        String normalizedDir = configuredUploadDir == null || configuredUploadDir.isBlank()
                ? DEFAULT_UPLOAD_DIR
                : configuredUploadDir.trim();
        Path configuredPath = Paths.get(normalizedDir);
        if (configuredPath.isAbsolute()) {
            return configuredPath.normalize();
        }
        Path launchDir = Paths.get("").toAbsolutePath().normalize();
        Path baseDir = detectProjectBaseDir(launchDir);
        return baseDir.resolve(configuredPath).normalize();
    }

    private Path detectProjectBaseDir(Path launchDir) {
        Path current = launchDir;
        Path backendRoot = null;
        while (current != null) {
            if (isWorkspaceRoot(current)) {
                return current;
            }
            if (backendRoot == null && isBackendRoot(current)) {
                backendRoot = current;
            }
            current = current.getParent();
        }
        return backendRoot != null ? backendRoot : launchDir;
    }

    private boolean isWorkspaceRoot(Path path) {
        return Files.exists(path.resolve("backend").resolve("pom.xml"))
                && Files.exists(path.resolve("package.json"));
    }

    private boolean isBackendRoot(Path path) {
        return Files.exists(path.resolve("pom.xml"))
                && Files.isDirectory(path.resolve("src").resolve("main"));
    }

    private String extractRelativeUploadPath(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            return null;
        }
        String normalized = rawUrl.trim();
        int queryIndex = normalized.indexOf('?');
        if (queryIndex >= 0) {
            normalized = normalized.substring(0, queryIndex);
        }
        if (normalized.startsWith("http://") || normalized.startsWith("https://")) {
            try {
                normalized = URI.create(normalized).getPath();
            } catch (Exception ex) {
                return null;
            }
        }
        if (normalized.startsWith(LOCAL_UPLOAD_PREFIX)) {
            return normalized.substring(LOCAL_UPLOAD_PREFIX.length());
        }
        if (normalized.startsWith(DEFAULT_UPLOAD_DIR + "/")) {
            return normalized.substring((DEFAULT_UPLOAD_DIR + "/").length());
        }
        return null;
    }
}
