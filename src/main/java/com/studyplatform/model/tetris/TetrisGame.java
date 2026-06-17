package com.studyplatform.model.tetris;

import lombok.Data;

import java.util.List;
import java.util.Map;
import java.util.Set;
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
    private final Set<String> processedAttackKeys = ConcurrentHashMap.newKeySet();
    private int winner = -1;

    public TetrisGame(int numPlayers) {
        this.numPlayers = numPlayers;
        for (int i = 0; i < numPlayers; i += 1) {
            playerStates.put(i, new TetrisPlayerState());
            garbageQueues.put(i, new CopyOnWriteArrayList<>());
            comboCounts.put(i, 0);
        }
    }
}
