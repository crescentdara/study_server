package com.studyplatform.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class UploadCleanupServiceTest {
    private static final Path CHAT_UPLOAD_DIR = Path.of("uploads", "chat").toAbsolutePath().normalize();
    private Path oldFile;
    private Path freshFile;

    @AfterEach
    void cleanup() throws Exception {
        if (oldFile != null) Files.deleteIfExists(oldFile);
        if (freshFile != null) Files.deleteIfExists(freshFile);
    }

    @Test
    void cleanupDeletesFilesOlderThanOneHour() throws Exception {
        Files.createDirectories(CHAT_UPLOAD_DIR);
        oldFile = CHAT_UPLOAD_DIR.resolve("old-" + UUID.randomUUID() + ".png");
        freshFile = CHAT_UPLOAD_DIR.resolve("fresh-" + UUID.randomUUID() + ".png");
        Files.write(oldFile, new byte[]{1});
        Files.write(freshFile, new byte[]{1});
        Files.setLastModifiedTime(oldFile, FileTime.from(Instant.now().minusSeconds(3700)));

        Object service = Class.forName("com.studyplatform.service.UploadCleanupService")
                .getConstructor()
                .newInstance();
        service.getClass().getMethod("cleanupChatUploads").invoke(service);

        assertThat(Files.exists(oldFile)).isFalse();
        assertThat(Files.exists(freshFile)).isTrue();
    }
}
