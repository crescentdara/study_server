package com.studyplatform.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ImageUploadResponse {
    private String imageUrl;
    private String fileName;
    private long fileSize;
}
