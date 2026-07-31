package com.studyplatform.dto.request;

import lombok.Data;

@Data
public class LobbyVendingRequest {
    private String type;
    private String eventId;
    private String sessionId;
    private String nickname;
    private String drink;
    private double x;
    private double y;
}
