package com.studyplatform.controller;

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
    private final SimpMessagingTemplate messaging;

    public PresenceController(SimpMessagingTemplate messaging) { this.messaging = messaging; }

    private record Connection(String ip, String nickname, long lastSeen) {}
    public record Visitor(String ip, String nickname, int connections) {}
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
        Connection previous = connections.put(headers.getSessionId(), new Connection(address, nickname, System.currentTimeMillis()));
        if (previous == null || !previous.ip().equals(address) || !previous.nickname().equals(nickname)) broadcast();
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
