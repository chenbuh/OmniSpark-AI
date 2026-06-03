package com.example.aihub.common.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Component
public class UploadStorageResolver {
    private static final String DEFAULT_UPLOAD_DIR = "uploads";
    private static final String LOCAL_UPLOAD_PREFIX = "/uploads/";

    private final Path uploadRoot;
    private final List<Path> localUploadRoots;

    public UploadStorageResolver(@Value("${file.upload-dir:" + DEFAULT_UPLOAD_DIR + "}") String configuredUploadDir) {
        Path launchDir = Paths.get("").toAbsolutePath().normalize();
        this.uploadRoot = resolveUploadRoot(configuredUploadDir, launchDir);
        this.localUploadRoots = List.copyOf(buildLocalUploadRoots(launchDir));
    }

    public Path getUploadRoot() {
        return uploadRoot;
    }

    public Path resolve(String first, String... more) {
        return uploadRoot.resolve(Paths.get(first, more)).normalize();
    }

    public Path resolveLocalUploadPath(String rawUrl) {
        List<Path> candidates = resolveLocalUploadPaths(rawUrl);
        for (Path candidate : candidates) {
            if (Files.exists(candidate)) {
                return candidate;
            }
        }
        return candidates.isEmpty() ? null : candidates.get(0);
    }

    public List<Path> resolveLocalUploadPaths(String rawUrl) {
        String relativePath = extractRelativeUploadPath(rawUrl);
        if (relativePath == null || relativePath.isBlank()) {
            return List.of();
        }
        List<Path> resolvedPaths = new ArrayList<>();
        for (Path localUploadRoot : localUploadRoots) {
            Path resolved = localUploadRoot.resolve(relativePath).normalize();
            if (resolved.startsWith(localUploadRoot)) {
                resolvedPaths.add(resolved);
            }
        }
        return resolvedPaths;
    }

    public String asResourceLocation() {
        String location = uploadRoot.toUri().toString();
        return location.endsWith("/") ? location : location + "/";
    }

    private Path resolveUploadRoot(String configuredUploadDir, Path launchDir) {
        String normalizedDir = configuredUploadDir == null || configuredUploadDir.isBlank()
                ? DEFAULT_UPLOAD_DIR
                : configuredUploadDir.trim();
        Path configuredPath = Paths.get(normalizedDir);
        if (configuredPath.isAbsolute()) {
            return configuredPath.normalize();
        }
        Path baseDir = detectProjectBaseDir(launchDir);
        return baseDir.resolve(configuredPath).normalize();
    }

    private List<Path> buildLocalUploadRoots(Path launchDir) {
        LinkedHashSet<Path> roots = new LinkedHashSet<>();
        roots.add(uploadRoot);

        Path workspaceRoot = findWorkspaceRoot(launchDir);
        if (workspaceRoot != null) {
            roots.add(workspaceRoot.resolve(DEFAULT_UPLOAD_DIR).normalize());
        }

        Path backendRoot = findBackendRoot(launchDir);
        if (backendRoot == null && workspaceRoot != null) {
            Path workspaceBackend = workspaceRoot.resolve("backend").normalize();
            if (isBackendRoot(workspaceBackend)) {
                backendRoot = workspaceBackend;
            }
        }
        if (backendRoot != null) {
            roots.add(backendRoot.resolve(DEFAULT_UPLOAD_DIR).normalize());
        }
        return new ArrayList<>(roots);
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

    private Path findWorkspaceRoot(Path launchDir) {
        Path current = launchDir;
        while (current != null) {
            if (isWorkspaceRoot(current)) {
                return current;
            }
            current = current.getParent();
        }
        return null;
    }

    private Path findBackendRoot(Path launchDir) {
        Path current = launchDir;
        while (current != null) {
            if (isBackendRoot(current)) {
                return current;
            }
            current = current.getParent();
        }
        return null;
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
