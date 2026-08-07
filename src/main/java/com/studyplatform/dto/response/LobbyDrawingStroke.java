package com.studyplatform.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class LobbyDrawingStroke {
    private String strokeId;
    private String sessionId;
    private String nickname;
    private String tool;
    private String color;
    private double width;
    private List<LobbyDrawingPoint> points;
    private long timestamp;
}
