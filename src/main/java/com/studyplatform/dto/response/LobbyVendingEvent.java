package com.studyplatform.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class LobbyVendingEvent {
    private String eventId;
    private String sessionId;
    private String nickname;
    private String drink;
    private double x;
    private double y;
    private long timestamp;
}
