package com.studyplatform.controller;

import org.springframework.context.event.EventListener;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

@Controller
public class PresenceController {
    private final Map<String, String> connections = new ConcurrentHashMap<>();
    private final SimpMessagingTemplate messaging;

    public PresenceController(SimpMessagingTemplate messaging) { this.messaging = messaging; }

    public record AddressCount(String ip, int connections) {}

    @MessageMapping("/presence/register")
    public synchronized void register(SimpMessageHeaderAccessor headers) {
        Map<String, Object> attributes = headers.getSessionAttributes();
        if (headers.getSessionId() == null || attributes == null) return;
        Object ip = attributes.get("clientIp");
        if (!(ip instanceof String address)) return;
        connections.put(headers.getSessionId(), address);
        broadcast();
    }

    @EventListener
    public synchronized void disconnect(SessionDisconnectEvent event) {
        if (connections.remove(event.getSessionId()) != null) broadcast();
    }

    @GetMapping("/api/presence/ips")
    @ResponseBody
    public synchronized List<AddressCount> snapshot() {
        Map<String, Integer> counts = new TreeMap<>();
        connections.values().forEach(ip -> counts.merge(ip, 1, Integer::sum));
        List<AddressCount> addresses = counts.entrySet().stream()
                .map(entry -> new AddressCount(entry.getKey(), entry.getValue())).toList();
        return addresses;
    }

    private void broadcast() {
        messaging.convertAndSend("/topic/presence/ips", snapshot());
    }
}
