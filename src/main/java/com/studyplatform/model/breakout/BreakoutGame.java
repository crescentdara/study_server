package com.studyplatform.model.breakout;

import lombok.Data;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Data
public class BreakoutGame {
    private final int width = 420;
    private final int height = 520;
    private final int numPlayers;
    private final Map<Integer, BreakoutPlayerState> playerStates = new ConcurrentHashMap<>();
    private int winner = -1;

    public BreakoutGame(int numPlayers) {
        this.numPlayers = numPlayers;
        for (int i = 0; i < numPlayers; i += 1) {
            playerStates.put(i, new BreakoutPlayerState());
        }
    }
}
