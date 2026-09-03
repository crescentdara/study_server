package com.studyplatform.service;

import com.studyplatform.dto.request.LobbyDrawingPoint;
import com.studyplatform.dto.request.LobbyDrawingRequest;
import com.studyplatform.dto.response.LobbyDrawingMessage;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LobbyDrawingServiceTest {
    @Test
    void sharesValidatedStrokeAndSnapshot() {
        LobbyDrawingService service = new LobbyDrawingService();
        LobbyDrawingRequest stroke = stroke("stroke-1", "session-1");
        stroke.setWidth(99);
        stroke.getPoints().get(0).setX(-1);

        LobbyDrawingMessage added = service.apply(stroke);
        assertNotNull(added);
        assertEquals("STROKE", added.getType());
        assertEquals(28.0, added.getStroke().getWidth());
        assertEquals(0.0, added.getStroke().getPoints().get(0).getX());

        LobbyDrawingRequest enter = new LobbyDrawingRequest();
        enter.setType("ENTER");
        assertEquals(1, service.apply(enter).getStrokes().size());
    }

    @Test
    void undoAndClearOnlyAffectOwnedStrokes() {
        LobbyDrawingService service = new LobbyDrawingService();
        assertNotNull(service.apply(stroke("a-1", "a")));
        assertNotNull(service.apply(stroke("b-1", "b")));
        assertNotNull(service.apply(stroke("a-2", "a")));

        LobbyDrawingRequest undo = action("UNDO", "a");
        assertEquals(2, service.apply(undo).getStrokes().size());

        LobbyDrawingRequest clear = action("CLEAR_MINE", "a");
        LobbyDrawingMessage remaining = service.apply(clear);
        assertEquals(1, remaining.getStrokes().size());
        assertEquals("b", remaining.getStrokes().get(0).getSessionId());
    }

    @Test
    void rejectsInvalidStrokeData() {
        LobbyDrawingService service = new LobbyDrawingService();
        LobbyDrawingRequest invalidColor = stroke("stroke-1", "s1");
        invalidColor.setColor("javascript:red");
        assertNull(service.apply(invalidColor));

        LobbyDrawingRequest onePoint = stroke("stroke-2", "s1");
        onePoint.setPoints(List.of(point(.2, .2)));
        assertNull(service.apply(onePoint));
    }

    @Test
    void clearMineKeepsEraserSoErasedContentDoesNotReappear() {
        LobbyDrawingService service = new LobbyDrawingService();
        assertNotNull(service.apply(stroke("pen-1", "mine")));
        LobbyDrawingRequest eraser = stroke("eraser-1", "mine");
        eraser.setTool("ERASER");
        assertNotNull(service.apply(eraser));

        LobbyDrawingMessage remaining = service.apply(action("CLEAR_MINE", "mine"));
        assertEquals(1, remaining.getStrokes().size());
        assertEquals("ERASER", remaining.getStrokes().get(0).getTool());
    }

    @Test
    void lockRejectsNewStrokesUntilAnyoneUnlocksIt() {
        LobbyDrawingService service = new LobbyDrawingService();

        assertTrue(service.apply(action("LOCK", "first-user")).isLocked());
        assertNull(service.apply(stroke("locked-stroke", "second-user")));
        assertFalse(service.apply(action("UNLOCK", "another-user")).isLocked());
        assertNotNull(service.apply(stroke("unlocked-stroke", "second-user")));
    }

    private static LobbyDrawingRequest action(String type, String sessionId) {
        LobbyDrawingRequest request = new LobbyDrawingRequest();
        request.setType(type);
        request.setSessionId(sessionId);
        return request;
    }

    private static LobbyDrawingRequest stroke(String strokeId, String sessionId) {
        LobbyDrawingRequest request = action("STROKE", sessionId);
        request.setStrokeId(strokeId);
        request.setNickname("tester");
        request.setTool("PEN");
        request.setColor("#60a5fa");
        request.setWidth(6);
        request.setPoints(List.of(point(.1, .2), point(.3, .4)));
        return request;
    }

    private static LobbyDrawingPoint point(double x, double y) {
        LobbyDrawingPoint point = new LobbyDrawingPoint();
        point.setX(x);
        point.setY(y);
        return point;
    }
}
