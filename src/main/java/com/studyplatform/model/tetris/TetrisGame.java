package com.studyplatform.model.tetris;

import lombok.Data;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Data
public class TetrisGame {
    private final int rows = 20;
    private final int cols = 10;
    private final int numPlayers;
    private final Map<Integer, TetrisPlayerState> playerStates = new ConcurrentHashMap<>();
    private int winner = -1;

    public TetrisGame(int numPlayers) {
        this.numPlayers = numPlayers;
        for (int i = 0; i < numPlayers; i += 1) {
            playerStates.put(i, new TetrisPlayerState());
        }
    }
}
