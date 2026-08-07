package com.studyplatform.service;

import com.studyplatform.dto.request.LobbyDrawingRequest;
import com.studyplatform.dto.response.LobbyDrawingMessage;
import com.studyplatform.dto.response.LobbyDrawingPoint;
import com.studyplatform.dto.response.LobbyDrawingStroke;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class LobbyDrawingService {
    static final int MAX_STROKES = 600;
    static final int MAX_POINTS = 400;
    private static final Set<String> TOOLS = Set.of("PEN", "ERASER");
    private static final Pattern HEX_COLOR = Pattern.compile("#[0-9a-f]{6}");

    private final Map<String, LobbyDrawingStroke> strokes = new LinkedHashMap<>();

    public synchronized LobbyDrawingMessage apply(LobbyDrawingRequest request) {
        if (request == null) return null;
        String type = safe(request.getType(), 20).toUpperCase();
        String sessionId = safe(request.getSessionId(), 80);

        if ("ENTER".equals(type)) return LobbyDrawingMessage.snapshot(snapshot());
        if (sessionId.isBlank()) return null;
        if ("UNDO".equals(type)) {
            removeLastOwnedStroke(sessionId);
            return LobbyDrawingMessage.snapshot(snapshot());
        }
        if ("CLEAR_MINE".equals(type)) {
            strokes.values().removeIf(stroke -> sessionId.equals(stroke.getSessionId())
                    && "PEN".equals(stroke.getTool()));
            return LobbyDrawingMessage.snapshot(snapshot());
        }
        if (!"STROKE".equals(type)) return null;

        String strokeId = safe(request.getStrokeId(), 100);
        String tool = safe(request.getTool(), 12).toUpperCase();
        String color = safe(request.getColor(), 16).toLowerCase();
        if (strokeId.isBlank() || strokes.containsKey(strokeId) || !TOOLS.contains(tool)) return null;
        if ("PEN".equals(tool) && !HEX_COLOR.matcher(color).matches()) return null;
        if (request.getPoints() == null || request.getPoints().size() < 2 || request.getPoints().size() > MAX_POINTS) {
            return null;
        }

        List<LobbyDrawingPoint> points = new ArrayList<>(request.getPoints().size());
        for (com.studyplatform.dto.request.LobbyDrawingPoint point : request.getPoints()) {
            if (point == null || !Double.isFinite(point.getX()) || !Double.isFinite(point.getY())) return null;
            points.add(new LobbyDrawingPoint(clamp(point.getX(), 0, 1), clamp(point.getY(), 0, 1)));
        }

        LobbyDrawingStroke stroke = new LobbyDrawingStroke(
                strokeId,
                sessionId,
                defaultNickname(request.getNickname()),
                tool,
                "ERASER".equals(tool) ? "#000000" : color,
                clamp(request.getWidth(), 1, 28),
                points,
                System.currentTimeMillis()
        );
        strokes.put(strokeId, stroke);
        while (strokes.size() > MAX_STROKES) strokes.remove(strokes.keySet().iterator().next());
        return LobbyDrawingMessage.stroke(copy(stroke));
    }

    synchronized int size() {
        return strokes.size();
    }

    private void removeLastOwnedStroke(String sessionId) {
        String ownedId = null;
        for (LobbyDrawingStroke stroke : strokes.values()) {
            if (sessionId.equals(stroke.getSessionId())) ownedId = stroke.getStrokeId();
        }
        if (ownedId != null) strokes.remove(ownedId);
    }

    private List<LobbyDrawingStroke> snapshot() {
        List<LobbyDrawingStroke> result = new ArrayList<>();
        strokes.values().forEach(stroke -> result.add(copy(stroke)));
        return result;
    }

    private static LobbyDrawingStroke copy(LobbyDrawingStroke stroke) {
        List<LobbyDrawingPoint> points = stroke.getPoints().stream()
                .map(point -> new LobbyDrawingPoint(point.getX(), point.getY()))
                .toList();
        return new LobbyDrawingStroke(stroke.getStrokeId(), stroke.getSessionId(), stroke.getNickname(),
                stroke.getTool(), stroke.getColor(), stroke.getWidth(), points, stroke.getTimestamp());
    }

    private static String defaultNickname(String nickname) {
        String safeNickname = safe(nickname, 24);
        return safeNickname.isBlank() ? "anonymous" : safeNickname;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static String safe(String value, int maxLength) {
        if (value == null) return "";
        String trimmed = value.trim();
        return trimmed.substring(0, Math.min(maxLength, trimmed.length()));
    }
}
