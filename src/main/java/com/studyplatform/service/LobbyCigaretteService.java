package com.studyplatform.service;

import com.studyplatform.dto.request.LobbyCigaretteRequest;
import com.studyplatform.dto.response.LobbyCigaretteMessage;
import com.studyplatform.dto.response.LobbyCigaretteState;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class LobbyCigaretteService {
    static final int MAX_CIGARETTES = 6;
    static final long STALE_AFTER_MS = 45_000L;

    private final Map<String, LobbyCigaretteState> cigarettes = new LinkedHashMap<>();

    public synchronized LobbyCigaretteMessage apply(LobbyCigaretteRequest request) {
        if (request == null) return null;
        String type = safe(request.getType(), 20).toUpperCase();
        String sessionId = safe(request.getSessionId(), 80);
        prune(System.currentTimeMillis());

        if ("ENTER".equals(type)) return LobbyCigaretteMessage.snapshot(copySnapshot());
        if (sessionId.isBlank()) return null;
        if ("HEARTBEAT".equals(type)) {
            LobbyCigaretteState current = cigarettes.get(sessionId);
            if (current != null) current.setUpdatedAt(System.currentTimeMillis());
            return LobbyCigaretteMessage.snapshot(copySnapshot());
        }
        if ("REMOVE".equals(type)) {
            cigarettes.remove(sessionId);
            return LobbyCigaretteMessage.remove(sessionId);
        }
        if (!List.of("SPAWN", "MOVE", "UPDATE", "HOLD", "FLICK", "PUFF", "EXTINGUISH").contains(type)) {
            return null;
        }
        if (!cigarettes.containsKey(sessionId) && cigarettes.size() >= MAX_CIGARETTES) return null;

        LobbyCigaretteState previous = cigarettes.get(sessionId);
        LobbyCigaretteState next = new LobbyCigaretteState(
                sessionId,
                safe(request.getNickname(), 24).isBlank() ? "anonymous" : safe(request.getNickname(), 24),
                clamp(request.getX(), 0.04, 0.96),
                clamp(request.getY(), 0.08, 0.92),
                clamp(request.getBurn(), 0.0, 1.0),
                request.isLit(),
                request.isHolding(),
                safe(request.getActionId(), 80),
                System.currentTimeMillis()
        );
        if (previous != null && next.getNickname().equals("anonymous")) next.setNickname(previous.getNickname());
        cigarettes.put(sessionId, next);
        return LobbyCigaretteMessage.upsert(copy(next));
    }

    synchronized int size() {
        return cigarettes.size();
    }

    private void prune(long now) {
        cigarettes.values().removeIf(state -> now - state.getUpdatedAt() > STALE_AFTER_MS);
    }

    private List<LobbyCigaretteState> copySnapshot() {
        List<LobbyCigaretteState> result = new ArrayList<>();
        cigarettes.values().forEach(state -> result.add(copy(state)));
        return result;
    }

    private static LobbyCigaretteState copy(LobbyCigaretteState state) {
        return new LobbyCigaretteState(state.getSessionId(), state.getNickname(), state.getX(), state.getY(),
                state.getBurn(), state.isLit(), state.isHolding(), state.getActionId(), state.getUpdatedAt());
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
