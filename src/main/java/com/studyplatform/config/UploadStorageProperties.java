package com.studyplatform.config;

import java.nio.file.Path;
import java.time.Duration;

public final class UploadStorageProperties {
    public static final Path UPLOAD_DIR = Path.of("uploads").toAbsolutePath().normalize();
    public static final Path CHAT_UPLOAD_DIR = UPLOAD_DIR.resolve("chat").normalize();
    public static final long MAX_IMAGE_BYTES = 10L * 1024L * 1024L;
    public static final long MAX_CHAT_UPLOAD_BYTES = 200L * 1024L * 1024L;
    public static final Duration CHAT_IMAGE_TTL = Duration.ofHours(1);

    private UploadStorageProperties() {
    }
}
