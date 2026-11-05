package com.studyplatform.service;

import com.studyplatform.dto.response.ChatMessage;
import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class ChatHistoryService {
    private static final int MAX_HISTORY = 100;
    private final Deque<ChatMessage> lobby = new ArrayDeque<>();
    private final AtomicLong idSeq = new AtomicLong();
    private long clearedThrough;
    public record LobbyState(List<ChatMessage> messages, long clearedThrough) {}

    public long nextId() {
        return idSeq.incrementAndGet();
    }

    public boolean addLobby(ChatMessage message) {
        synchronized (lobby) {
            if (message.getId() <= clearedThrough) return false;
            add(lobby, message);
            return true;
        }
    }

    public long clearLobby() {
        synchronized (lobby) {
            clearedThrough = idSeq.get();
            lobby.clear();
            return clearedThrough;
        }
    }

    public List<ChatMessage> lobbyHistory() {
        return snapshot(lobby);
    }

    public LobbyState lobbyState() {
        synchronized (lobby) {
            return new LobbyState(new ArrayList<>(lobby), clearedThrough);
        }
    }

    public ChatMessage findLobbyById(long id) {
        synchronized (lobby) {
            for (ChatMessage message : lobby) {
                if (message.getId() == id) return message;
            }
        }
        return null;
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
