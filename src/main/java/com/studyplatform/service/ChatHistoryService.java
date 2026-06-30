package com.studyplatform.service;

import com.studyplatform.dto.response.ChatMessage;
import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ChatHistoryService {
    private static final int MAX_HISTORY = 100;
    private final Deque<ChatMessage> lobby = new ArrayDeque<>();
    private final Map<String, Deque<ChatMessage>> rooms = new ConcurrentHashMap<>();

    public void addLobby(ChatMessage message) {
        add(lobby, message);
    }

    public List<ChatMessage> lobbyHistory() {
        return snapshot(lobby);
    }

    public void addRoom(String roomId, ChatMessage message) {
        add(rooms.computeIfAbsent(roomId, key -> new ArrayDeque<>()), message);
    }

    public List<ChatMessage> roomHistory(String roomId) {
        Deque<ChatMessage> messages = rooms.get(roomId);
        return messages == null ? List.of() : snapshot(messages);
    }

    private static void add(Deque<ChatMessage> target, ChatMessage message) {
        synchronized (target) {
            target.addLast(message);
            while (target.size() > MAX_HISTORY) target.removeFirst();
        }
    }

    private static List<ChatMessage> snapshot(Deque<ChatMessage> source) {
        synchronized (source) {
            return new ArrayList<>(source);
        }
    }
}
