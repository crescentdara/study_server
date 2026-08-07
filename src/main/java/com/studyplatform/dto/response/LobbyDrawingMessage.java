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

    public static LobbyDrawingMessage snapshot(List<LobbyDrawingStroke> strokes) {
        return new LobbyDrawingMessage("SNAPSHOT", null, null, strokes);
    }

    public static LobbyDrawingMessage stroke(LobbyDrawingStroke stroke) {
        return new LobbyDrawingMessage("STROKE", stroke.getStrokeId(), stroke, null);
    }
}
