package com.studyplatform.dto.request;

import lombok.Data;

@Data
public class ChatWarningRequest {
    private String moderatorNickname;
    private String targetNickname;
    private String color;
}
