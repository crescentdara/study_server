package com.studyplatform.service;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ChatWarningService {
    private final Map<String, List<String>> warnings = new ConcurrentHashMap<>();

    public synchronized Map<String, List<String>> add(String nickname, String color) {
        warnings.computeIfAbsent(nickname, ignored -> new ArrayList<>()).add(color);
        return snapshot();
    }

    public synchronized Map<String, List<String>> removeLast(String nickname, String color) {
        List<String> cards = warnings.get(nickname);
        if (cards == null) return snapshot();
        for (int index = cards.size() - 1; index >= 0; index -= 1) {
            if (color.equals(cards.get(index))) {
                cards.remove(index);
                break;
            }
        }
        if (cards.isEmpty()) warnings.remove(nickname);
        return snapshot();
    }

    public synchronized Map<String, List<String>> clear(String nickname) {
        warnings.remove(nickname);
        return snapshot();
    }

    public synchronized Map<String, List<String>> snapshot() {
        Map<String, List<String>> copy = new LinkedHashMap<>();
        warnings.forEach((nickname, cards) -> copy.put(nickname, List.copyOf(cards)));
        return copy;
    }
}
