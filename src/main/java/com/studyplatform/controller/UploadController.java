package com.studyplatform.controller;

import com.studyplatform.config.UploadStorageProperties;
import com.studyplatform.dto.response.ImageUploadResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/uploads")
public class UploadController {
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            MediaType.IMAGE_JPEG_VALUE,
            MediaType.IMAGE_PNG_VALUE,
            MediaType.IMAGE_GIF_VALUE,
            "image/webp"
    );
    private static final Map<String, String> EXTENSIONS = Map.of(
            MediaType.IMAGE_JPEG_VALUE, ".jpg",
            MediaType.IMAGE_PNG_VALUE, ".png",
            MediaType.IMAGE_GIF_VALUE, ".gif",
            "image/webp", ".webp"
    );

    @PostMapping("/images")
    public ResponseEntity<ImageUploadResponse> uploadImage(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Image file is required.");
        }
        if (file.getSize() > UploadStorageProperties.MAX_IMAGE_BYTES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Image must be 10MB or smaller.");
        }

        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only jpg, png, gif, and webp images are allowed.");
        }

        try {
            Files.createDirectories(UploadStorageProperties.CHAT_UPLOAD_DIR);
            String storedName = UUID.randomUUID() + EXTENSIONS.get(contentType);
            java.nio.file.Path target = UploadStorageProperties.CHAT_UPLOAD_DIR.resolve(storedName).normalize();
            if (!target.startsWith(UploadStorageProperties.CHAT_UPLOAD_DIR)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid file path.");
            }
            file.transferTo(target);

            String originalName = file.getOriginalFilename();
            String displayName = originalName == null || originalName.isBlank() ? storedName : originalName;
            return ResponseEntity.ok(new ImageUploadResponse("/uploads/chat/" + storedName, displayName, file.getSize()));
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to save image.", e);
        }
    }
}
