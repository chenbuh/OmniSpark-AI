package com.example.aihub.common.meta;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

@Service
public class BuildMetadataService {

    @Value("${app.version:}")
    private String configuredVersion;

    @Value("${app.build-time:}")
    private String configuredBuildTime;

    @Value("${app.build-commit:}")
    private String configuredBuildCommit;

    @Value("${app.build-branch:}")
    private String configuredBuildBranch;

    public String currentVersionForDisplay() {
        return firstNonBlank(currentVersionOrBlank(), "开发环境待确认");
    }

    public String currentVersionOrBlank() {
        return firstNonBlank(sanitize(configuredVersion), readBackendPomVersion(), "");
    }

    public String buildTimeForDisplay() {
        return firstNonBlank(buildTimeOrBlank(), "开发环境未注入");
    }

    public String buildTimeOrBlank() {
        return sanitize(configuredBuildTime);
    }

    public String currentCommitSha() {
        String configured = sanitize(configuredBuildCommit);
        if (!configured.isBlank()) {
            return configured;
        }
        String localCommit = readLocalGitCommitSha();
        return localCommit == null ? "" : localCommit;
    }

    public String currentBranch() {
        String configured = sanitize(configuredBuildBranch);
        if (!configured.isBlank()) {
            return configured;
        }
        String localBranch = readLocalGitBranch();
        return localBranch == null ? "" : localBranch;
    }

    private String sanitize(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String trimmed = value.trim();
        if ((trimmed.startsWith("@") && trimmed.endsWith("@")) || "unknown".equalsIgnoreCase(trimmed)) {
            return "";
        }
        return trimmed;
    }

    private String firstNonBlank(String... candidates) {
        for (String candidate : candidates) {
            if (candidate != null && !candidate.isBlank()) {
                return candidate;
            }
        }
        return "";
    }

    private String readBackendPomVersion() {
        Path pomPath = resolvePomPath();
        if (pomPath == null) {
            return "";
        }
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(false);
            NodeList children = factory.newDocumentBuilder()
                    .parse(pomPath.toFile())
                    .getDocumentElement()
                    .getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                Node child = children.item(i);
                if (child instanceof Element element && "version".equals(element.getTagName())) {
                    String version = sanitize(element.getTextContent());
                    if (!version.isBlank()) {
                        return version;
                    }
                }
            }
        } catch (Exception ignored) {
            return "";
        }
        return "";
    }

    private Path resolvePomPath() {
        Path current = Paths.get("").toAbsolutePath().normalize();
        while (current != null) {
            Path pom = current.resolve("pom.xml");
            if (Files.isRegularFile(pom)) {
                return pom;
            }
            Path backendPom = current.resolve("backend").resolve("pom.xml");
            if (Files.isRegularFile(backendPom)) {
                return backendPom;
            }
            current = current.getParent();
        }
        return null;
    }

    private String readLocalGitCommitSha() {
        try {
            Path gitDir = resolveGitDir();
            if (gitDir == null) {
                return null;
            }
            String headContent = readTrimmed(gitDir.resolve("HEAD"));
            if (headContent == null || headContent.isBlank()) {
                return null;
            }
            if (!headContent.startsWith("ref:")) {
                return headContent;
            }
            String ref = headContent.substring(4).trim();
            String sha = readTrimmed(gitDir.resolve(ref));
            if (sha != null && !sha.isBlank()) {
                return sha;
            }
            return readPackedRef(gitDir, ref);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String readLocalGitBranch() {
        try {
            Path gitDir = resolveGitDir();
            if (gitDir == null) {
                return null;
            }
            String headContent = readTrimmed(gitDir.resolve("HEAD"));
            if (headContent == null || !headContent.startsWith("ref:")) {
                return null;
            }
            String ref = headContent.substring(4).trim();
            int lastSlash = ref.lastIndexOf('/');
            return lastSlash >= 0 ? ref.substring(lastSlash + 1) : ref;
        } catch (Exception ignored) {
            return null;
        }
    }

    private Path resolveGitDir() {
        Path current = Paths.get("").toAbsolutePath().normalize();
        while (current != null) {
            Path gitPath = current.resolve(".git");
            if (Files.isDirectory(gitPath)) {
                return gitPath;
            }
            if (Files.isRegularFile(gitPath)) {
                String pointer = readTrimmed(gitPath);
                if (pointer != null && pointer.startsWith("gitdir:")) {
                    String relativePath = pointer.substring("gitdir:".length()).trim();
                    return current.resolve(relativePath).normalize();
                }
            }
            current = current.getParent();
        }
        return null;
    }

    private String readPackedRef(Path gitDir, String ref) {
        Path packedRefsPath = gitDir.resolve("packed-refs");
        if (!Files.exists(packedRefsPath)) {
            return null;
        }
        try {
            List<String> lines = Files.readAllLines(packedRefsPath);
            for (String line : lines) {
                String trimmed = line.trim();
                if (trimmed.isBlank() || trimmed.startsWith("#") || trimmed.startsWith("^")) {
                    continue;
                }
                String[] parts = trimmed.split("\\s+", 2);
                if (parts.length == 2 && ref.equals(parts[1].trim())) {
                    return parts[0].trim();
                }
            }
        } catch (Exception ignored) {
            return null;
        }
        return null;
    }

    private String readTrimmed(Path path) {
        try {
            if (path == null || !Files.exists(path)) {
                return null;
            }
            return Files.readString(path).trim();
        } catch (Exception ignored) {
            return null;
        }
    }
}
