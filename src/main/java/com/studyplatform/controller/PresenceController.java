package com.studyplatform.controller;

import com.studyplatform.service.ChatHistoryService;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
public class PresenceController {
    private static final long TIMEOUT_MS = 180_000;
    private final Map<String, Connection> connections = new HashMap<>();
    private final Map<String, Long> recentlySeen = new HashMap<>();
    private final Map<String, Long> recentVisits = new HashMap<>();
    private final SimpMessagingTemplate messaging;
    private final ChatHistoryService chatHistoryService;

    public PresenceController(SimpMessagingTemplate messaging, ChatHistoryService chatHistoryService) {
        this.messaging = messaging;
        this.chatHistoryService = chatHistoryService;
    }

    private record Connection(String ip, String nickname, String browserId, String notificationId, long lastSeen) {}
    public record Visitor(String ip, String nickname, int connections) {}
    public record Arrival(String nickname) {}
    private record VisitorKey(String ip, String nickname) {}

    @MessageMapping("/presence/register")
    public synchronized void register(@Payload(required = false) Map<String, String> payload,
                                      SimpMessageHeaderAccessor headers) {
        Map<String, Object> attributes = headers.getSessionAttributes();
        if (headers.getSessionId() == null || attributes == null) return;
        Object ip = attributes.get("clientIp");
        if (!(ip instanceof String address)) return;
        String nickname = payload == null ? "" : payload.getOrDefault("nickname", "");
        if (nickname == null) nickname = "";
        nickname = nickname.trim();
        if (nickname.length() > 80) nickname = nickname.substring(0, 80);
        String browserId = payload == null ? "" : payload.getOrDefault("browserId", "");
        String notificationId = payload == null ? "" : payload.getOrDefault("notificationId", "");
        String visitId = payload == null ? "" : payload.getOrDefault("visitId", "");
        if (browserId == null || !browserId.matches("[a-zA-Z0-9-]{1,64}")) browserId = "";
        if (notificationId == null || !notificationId.matches("[a-zA-Z0-9-]{1,64}")) notificationId = "";
        if (visitId == null || !visitId.matches("[a-zA-Z0-9-]{1,64}")) visitId = "";
        long now = System.currentTimeMillis();
        recentlySeen.entrySet().removeIf(entry -> entry.getValue() < now - TIMEOUT_MS);
        recentVisits.entrySet().removeIf(entry -> entry.getValue() < now - TIMEOUT_MS);
        Connection previous = connections.get(headers.getSessionId());
        boolean newBrowser = !browserId.isEmpty() && previous == null && !recentlySeen.containsKey(browserId);
        if (!visitId.isEmpty() && !recentVisits.containsKey(visitId)) {
            long clearedThrough = chatHistoryService.clearLobby();
            messaging.convertAndSend("/topic/lobby/chat-cleared", Map.of("clearedThrough", clearedThrough));
        }
        if (!visitId.isEmpty()) recentVisits.put(visitId, now);
        if (newBrowser) {
            Arrival arrival = new Arrival(nickname.isEmpty() ? "새 사용자" : nickname);
            connections.values().stream()
                    .filter(existing -> existing.lastSeen() >= now - TIMEOUT_MS && !existing.notificationId().isEmpty())
                    .map(Connection::notificationId).distinct()
                    .forEach(id -> messaging.convertAndSend("/topic/presence/arrivals/" + id, arrival));
        }
        if (!browserId.isEmpty()) recentlySeen.put(browserId, now);
        connections.put(headers.getSessionId(), new Connection(address, nickname, browserId, notificationId, now));
        if (previous == null || !previous.ip().equals(address) || !previous.nickname().equals(nickname)) broadcast();
        if (!notificationId.isEmpty() && !visitId.isEmpty()) {
            messaging.convertAndSend("/topic/presence/ready/" + notificationId, Map.of("ready", true));
        }
    }

    @EventListener
    public synchronized void disconnect(SessionDisconnectEvent event) {
        if (connections.remove(event.getSessionId()) != null) broadcast();
    }

    @GetMapping("/api/presence/ips")
    @ResponseBody
    public synchronized List<Visitor> snapshot() {
        long cutoff = System.currentTimeMillis() - TIMEOUT_MS;
        connections.values().removeIf(connection -> connection.lastSeen() < cutoff);
        Map<VisitorKey, Integer> counts = new HashMap<>();
        connections.values().forEach(connection -> counts.merge(new VisitorKey(connection.ip(), connection.nickname()), 1, Integer::sum));
        return counts.entrySet().stream()
                .map(entry -> new Visitor(entry.getKey().ip(), entry.getKey().nickname(), entry.getValue()))
                .sorted(Comparator.comparing(Visitor::ip).thenComparing(Visitor::nickname))
                .toList();
    }

    private void broadcast() { messaging.convertAndSend("/topic/presence/ips", snapshot()); }
}
