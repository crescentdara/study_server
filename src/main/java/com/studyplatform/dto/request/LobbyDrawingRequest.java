package com.studyplatform.dto.request;

import lombok.Data;

import java.util.List;

@Data
public class LobbyDrawingRequest {
    private String type;
    private String strokeId;
    private String sessionId;
    private String nickname;
    private String tool;
    private String color;
    private double width;
    private List<LobbyDrawingPoint> points;
}
