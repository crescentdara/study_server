package com.studyplatform.controller;

import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PresenceControllerTest {
    private SimpMessageHeaderAccessor headers(String session) {
        var headers = SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);
        headers.setSessionId(session);
        headers.setSessionAttributes(new HashMap<>(Map.of("clientIp", "127.0.0.1")));
        return headers;
    }

    private Map<String, String> visitor(String browser, String channel, String name) {
        return Map.of("browserId", browser, "notificationId", channel, "nickname", name);
    }

    @Test
    void arrivalGoesOnlyToExistingVisitorsAndNotForHeartbeatsOrAnotherTab() {
        var messaging = mock(SimpMessagingTemplate.class);
        var controller = new PresenceController(messaging);
        controller.register(visitor("browser-a", "channel-a", "A"), headers("a"));
        verify(messaging, never()).convertAndSend(startsWith("/topic/presence/arrivals/"), any(Object.class));
        clearInvocations(messaging);
        controller.register(visitor("browser-b", "channel-b", "B"), headers("b"));
        verify(messaging).convertAndSend(eq("/topic/presence/arrivals/channel-a"), eq(new PresenceController.Arrival("B")));
        verify(messaging, never()).convertAndSend(eq("/topic/presence/arrivals/channel-b"), any(Object.class));
        clearInvocations(messaging);
        controller.register(visitor("browser-b", "channel-b", "B"), headers("b"));
        controller.register(visitor("browser-b", "channel-c", "B"), headers("c"));
        verify(messaging, never()).convertAndSend(startsWith("/topic/presence/arrivals/"), any(Object.class));
    }
}
