package com.studyplatform.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class LobbyDrawingMessage {
    private String type;
    private String strokeId;
    private LobbyDrawingStroke stroke;
    private List<LobbyDrawingStroke> strokes;
    private boolean locked;

    public static LobbyDrawingMessage snapshot(List<LobbyDrawingStroke> strokes, boolean locked) {
        return new LobbyDrawingMessage("SNAPSHOT", null, null, strokes, locked);
    }

    public static LobbyDrawingMessage stroke(LobbyDrawingStroke stroke) {
        return new LobbyDrawingMessage("STROKE", stroke.getStrokeId(), stroke, null, false);
    }

    public static LobbyDrawingMessage lockState(boolean locked) {
        return new LobbyDrawingMessage("LOCK_STATE", null, null, null, locked);
    }
}
