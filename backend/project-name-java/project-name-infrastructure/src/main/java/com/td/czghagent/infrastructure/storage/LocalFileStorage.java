// GENERATED_BY_AI
// MODEL: gpt-5
// DATE: 2026-07-29
package com.td.czghagent.infrastructure.storage;

import com.td.czghagent.domain.exception.BusinessException;
import com.td.czghagent.domain.model.StoredFile;
import com.td.czghagent.domain.port.FileStorage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;

@Component
public class LocalFileStorage implements FileStorage {

    private final Path root;

    public LocalFileStorage(@Value("${app.storage.root}") String root) {
        this.root = Path.of(root).toAbsolutePath().normalize();
        createDirectories(this.root);
    }

    /**
     * <p><b>Preconditions:</b>namespace由业务服务生成，content已通过大小校验。</p>
     * <p><b>Side Effects:</b>在私有目录原子写入一个不可猜测对象。</p>
     * <p><b>Error Semantics:</b>磁盘写入失败返回STORAGE_WRITE_FAILED。</p>
     */
    @Override
    public StoredFile store(String namespace, String fileName, String mediaType, byte[] content) {
        String extension = extension(fileName);
        LocalDate today = LocalDate.now();
        String objectKey = namespace + "/" + today.getYear() + "/"
                + String.format(Locale.ROOT, "%02d", today.getMonthValue()) + "/"
                + UUID.randomUUID() + extension;
        Path target = resolve(objectKey);
        Path temporary = target.resolveSibling(target.getFileName() + ".tmp");
        createDirectories(target.getParent());
        try {
            Files.write(temporary, content);
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            return new StoredFile(objectKey, fileName, mediaType, content.length, sha256(content), content);
        } catch (IOException exception) {
            deleteQuietly(temporary);
            throw new BusinessException("STORAGE_WRITE_FAILED", "文件保存失败", 500);
        }
    }

    @Override
    public StoredFile read(String objectKey, String fileName, String mediaType) {
        Path target = resolve(objectKey);
        try {
            byte[] content = Files.readAllBytes(target);
            return new StoredFile(objectKey, fileName, mediaType, content.length, sha256(content), content);
        } catch (IOException exception) {
            throw new BusinessException("STORAGE_OBJECT_NOT_FOUND", "文件不存在或已损坏", 404);
        }
    }

    @Override
    public void delete(String objectKey) {
        deleteQuietly(resolve(objectKey));
    }

    private Path resolve(String objectKey) {
        Path resolved = root.resolve(objectKey).normalize();
        if (!resolved.startsWith(root)) {
            throw new BusinessException("STORAGE_KEY_INVALID", "文件对象键不合法", 400);
        }
        return resolved;
    }

    private void createDirectories(Path directory) {
        try {
            Files.createDirectories(directory);
        } catch (IOException exception) {
            throw new IllegalStateException("无法创建私有文件目录", exception);
        }
    }

    private String extension(String fileName) {
        int index = fileName.lastIndexOf('.');
        if (index < 0 || index == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(index).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9.]", "");
    }

    private String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JDK不支持SHA-256", exception);
        }
    }

    private void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // Cleanup failure is logged by storage monitoring in production.
        }
    }
}
