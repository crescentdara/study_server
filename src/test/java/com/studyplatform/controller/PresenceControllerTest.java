package com.studyplatform.controller;

import com.studyplatform.dto.response.ChatMessage;
import com.studyplatform.service.ChatHistoryService;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.assertThat;

class PresenceControllerTest {
    private SimpMessageHeaderAccessor headers(String session) {
        var headers = SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);
        headers.setSessionId(session);
        headers.setSessionAttributes(new HashMap<>(Map.of("clientIp", "127.0.0.1")));
        return headers;
    }

    private Map<String, String> visitor(String browser, String channel, String name) {
        return Map.of("browserId", browser, "notificationId", channel, "visitId", channel, "nickname", name);
    }

    @Test
    void arrivalGoesOnlyToExistingVisitorsAndNotForHeartbeatsOrAnotherTab() {
        var messaging = mock(SimpMessagingTemplate.class);
        var controller = new PresenceController(messaging, new ChatHistoryService());
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

    @Test
    void eachNewVisitClearsHistoryButHeartbeatDoesNot() {
        var messaging = mock(SimpMessagingTemplate.class);
        var history = new ChatHistoryService();
        var controller = new PresenceController(messaging, history);
        controller.register(visitor("browser-a", "visit-a", "A"), headers("a"));
        var message = new ChatMessage();
        message.setId(history.nextId());
        assertThat(history.addLobby(message)).isTrue();

        controller.register(visitor("browser-a", "visit-a", "A"), headers("a"));
        assertThat(history.lobbyHistory()).containsExactly(message);

        controller.register(visitor("browser-b", "visit-b", "B"), headers("b"));
        assertThat(history.lobbyHistory()).isEmpty();
        verify(messaging).convertAndSend(eq("/topic/lobby/chat-cleared"), eq(Map.of("clearedThrough", message.getId())));
        verify(messaging).convertAndSend(eq("/topic/presence/ready/visit-b"), eq(Map.of("ready", true)));
    }
}
