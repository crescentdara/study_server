package com.studyplatform.service;

import com.studyplatform.dto.response.ChatMessage;
import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

@Service
public class ChatHistoryService {
    private static final int MAX_HISTORY = 100;
    private final Deque<ChatMessage> lobby = new ArrayDeque<>();

    public void addLobby(ChatMessage message) {
        add(lobby, message);
    }

    public List<ChatMessage> lobbyHistory() {
        return snapshot(lobby);
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
