package com.studyplatform.service;

import com.studyplatform.config.UploadStorageProperties;
import jakarta.annotation.PostConstruct;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

@Service
public class UploadCleanupService {
    @PostConstruct
    public void cleanupOnStartup() {
        cleanupChatUploads();
    }

    @Scheduled(fixedDelay = 10 * 60 * 1000L)
    public void cleanupChatUploads() {
        try {
            Files.createDirectories(UploadStorageProperties.CHAT_UPLOAD_DIR);
            deleteExpiredFiles();
            trimToMaxSize();
        } catch (IOException ignored) {
            // Cleanup is best-effort for local test storage.
        }
    }

    private void deleteExpiredFiles() throws IOException {
        Instant cutoff = Instant.now().minus(UploadStorageProperties.CHAT_IMAGE_TTL);
        try (Stream<Path> paths = Files.list(UploadStorageProperties.CHAT_UPLOAD_DIR)) {
            for (Path path : paths.filter(Files::isRegularFile).toList()) {
                FileTime modifiedAt = Files.getLastModifiedTime(path);
                if (modifiedAt.toInstant().isBefore(cutoff)) {
                    Files.deleteIfExists(path);
                }
            }
        }
    }

    private void trimToMaxSize() throws IOException {
        List<Path> files;
        try (Stream<Path> paths = Files.list(UploadStorageProperties.CHAT_UPLOAD_DIR)) {
            files = paths.filter(Files::isRegularFile)
                    .sorted(Comparator.comparing(this::lastModifiedOrEpoch))
                    .toList();
        }

        long totalBytes = 0L;
        for (Path file : files) {
            totalBytes += Files.size(file);
        }

        for (Path file : files) {
            if (totalBytes <= UploadStorageProperties.MAX_CHAT_UPLOAD_BYTES) return;
            long size = Files.size(file);
            Files.deleteIfExists(file);
            totalBytes -= size;
        }
    }

    private Instant lastModifiedOrEpoch(Path path) {
        try {
            return Files.getLastModifiedTime(path).toInstant();
        } catch (IOException e) {
            return Instant.EPOCH;
        }
    }
}
