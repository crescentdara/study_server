package com.studyplatform.service;

import com.studyplatform.dto.request.LobbyVendingRequest;
import com.studyplatform.dto.response.LobbyVendingEvent;
import com.studyplatform.dto.response.LobbyVendingMessage;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class LobbyVendingService {
    private static final Set<String> DRINKS = Set.of("MIX_COFFEE", "HOT_CHOCOLATE", "YULMU_TEA", "MILK");
    private static final int MAX_CUPS = 12;
    private final Map<String, LobbyVendingEvent> cups = new LinkedHashMap<>();

    public synchronized LobbyVendingMessage apply(LobbyVendingRequest request) {
        if (request == null) return null;
        String type = safe(request.getType(), 20).toUpperCase();
        if (type.isBlank()) type = "DISPENSE";
        if ("ENTER".equals(type)) return LobbyVendingMessage.snapshot(snapshot());

        String eventId = safe(request.getEventId(), 80);
        if ("REMOVE".equals(type)) {
            if (eventId.isBlank() || cups.remove(eventId) == null) return null;
            return LobbyVendingMessage.remove(eventId);
        }
        if ("MOVE".equals(type)) {
            LobbyVendingEvent current = cups.get(eventId);
            if (current == null) return null;
            current.setX(clamp(request.getX(), .02, .98));
            current.setY(clamp(request.getY(), .05, .95));
            current.setTimestamp(System.currentTimeMillis());
            return LobbyVendingMessage.cup("MOVE", copy(current));
        }
        if (!"DISPENSE".equals(type)) return null;

        String drink = safe(request.getDrink(), 30).toUpperCase();
        if (!DRINKS.contains(drink)) return null;
        if (eventId.isBlank()) eventId = UUID.randomUUID().toString();
        String nickname = safe(request.getNickname(), 24);
        if (nickname.isBlank()) nickname = "anonymous";
        LobbyVendingEvent cup = new LobbyVendingEvent(
                eventId,
                safe(request.getSessionId(), 80),
                nickname,
                drink,
                clamp(request.getX(), .02, .98),
                clamp(request.getY(), .05, .95),
                System.currentTimeMillis()
        );
        cups.put(eventId, cup);
        while (cups.size() > MAX_CUPS) cups.remove(cups.keySet().iterator().next());
        return LobbyVendingMessage.cup("DISPENSE", copy(cup));
    }

    synchronized int size() {
        return cups.size();
    }

    private List<LobbyVendingEvent> snapshot() {
        List<LobbyVendingEvent> result = new ArrayList<>();
        cups.values().forEach(cup -> result.add(copy(cup)));
        return result;
    }

    private static LobbyVendingEvent copy(LobbyVendingEvent cup) {
        return new LobbyVendingEvent(cup.getEventId(), cup.getSessionId(), cup.getNickname(), cup.getDrink(),
                cup.getX(), cup.getY(), cup.getTimestamp());
    }

    private static double clamp(double value, double min, double max) {
        if (!Double.isFinite(value)) return min;
        return Math.max(min, Math.min(max, value));
    }

    private static String safe(String value, int maxLength) {
        if (value == null) return "";
        String trimmed = value.trim();
        return trimmed.substring(0, Math.min(maxLength, trimmed.length()));
    }
}
