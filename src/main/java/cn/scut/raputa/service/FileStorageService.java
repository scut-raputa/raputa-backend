package cn.scut.raputa.service;

import cn.scut.raputa.exception.BizException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Service
@Slf4j
public class FileStorageService {

    @Value("${raputa.storage.root:${user.home}/raputa-storage}")
    private String storageRoot;

    public Path getStorageRootPath() {
        Path root = Paths.get(storageRoot).toAbsolutePath().normalize();
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new BizException(500, "无法创建存储根目录: " + root);
        }
        return root;
    }

    public Path ensureSessionDirectory(String sessionKey) {
        Path dir = getStorageRootPath().resolve("sessions").resolve(sessionKey).normalize();
        try {
            Files.createDirectories(dir);
            return dir;
        } catch (IOException e) {
            throw new BizException(500, "无法创建会话目录: " + dir);
        }
    }

    public Path resolveSessionDir(String sessionDir) {
        if (sessionDir == null || sessionDir.isBlank()) {
            return getStorageRootPath();
        }
        Path candidate = Paths.get(sessionDir.trim());
        if (!candidate.isAbsolute()) {
            candidate = getStorageRootPath().resolve(candidate);
        }
        return candidate.toAbsolutePath().normalize();
    }

    public String toRelativePath(Path absolutePath) {
        if (absolutePath == null) {
            return null;
        }

        Path normalized = absolutePath.toAbsolutePath().normalize();
        Path root = getStorageRootPath().toAbsolutePath().normalize();
        if (normalized.startsWith(root)) {
            return root.relativize(normalized).toString().replace('\\', '/');
        }
        return normalized.toString().replace('\\', '/');
    }

    public Path resolveStoredFile(String storageRootValue, String relativePath, String legacyAbsolutePath) {
        if (storageRootValue != null && !storageRootValue.isBlank()
                && relativePath != null && !relativePath.isBlank()) {
            Path base = Paths.get(storageRootValue);
            return base.resolve(relativePath).toAbsolutePath().normalize();
        }

        if (legacyAbsolutePath != null && !legacyAbsolutePath.isBlank()) {
            return Paths.get(legacyAbsolutePath).toAbsolutePath().normalize();
        }

        return null;
    }
}
