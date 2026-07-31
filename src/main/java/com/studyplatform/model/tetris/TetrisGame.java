package com.studyplatform.model.tetris;

import lombok.Data;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Data
public class TetrisGame {
    private final int rows = 20;
    private final int cols = 10;
    private final int numPlayers;
    private final Map<Integer, TetrisPlayerState> playerStates = new ConcurrentHashMap<>();
    private final Map<Integer, List<Map<String, Object>>> garbageQueues = new ConcurrentHashMap<>();
    private final Map<Integer, Integer> comboCounts = new ConcurrentHashMap<>();
    private final Map<Integer, Integer> lastAttackers = new ConcurrentHashMap<>();
    private final Map<Integer, Integer> targetCursors = new ConcurrentHashMap<>();
    private final List<Map<String, Object>> attackLog = new CopyOnWriteArrayList<>();
    private final List<Map<String, Object>> distractEvents = new CopyOnWriteArrayList<>();
    private final List<Integer> eliminationOrder = new CopyOnWriteArrayList<>();
    private final List<Integer> finalRanking = new CopyOnWriteArrayList<>();
    private final Set<Integer> readyPlayers = ConcurrentHashMap.newKeySet();
    private final Set<String> processedAttackKeys = ConcurrentHashMap.newKeySet();
    private final List<String> processedAttackOrder = new CopyOnWriteArrayList<>();
    private final String instanceId = UUID.randomUUID().toString();
    private int winner = -1;
    private boolean paused = false;
    private boolean aborted = false;
    private String abortReason = "";
    private String previousAbortReason = "";
    private boolean recordSaved = false;

    public TetrisGame(int numPlayers) {
        this.numPlayers = numPlayers;
        for (int i = 0; i < numPlayers; i += 1) {
            playerStates.put(i, new TetrisPlayerState());
            garbageQueues.put(i, new CopyOnWriteArrayList<>());
            comboCounts.put(i, 0);
            lastAttackers.put(i, -1);
            targetCursors.put(i, 0);
        }
    }
}
