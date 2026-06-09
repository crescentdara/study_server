package com.studyplatform.model.incident;

import lombok.Data;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Data
public class IncidentAvoidGame {
    private final int width = 360;
    private final int height = 520;
    private final int numPlayers;
    private final Map<Integer, IncidentAvoidPlayerState> playerStates = new ConcurrentHashMap<>();
    private int winner = -1;

    public IncidentAvoidGame(int numPlayers) {
        this.numPlayers = numPlayers;
        for (int i = 0; i < numPlayers; i += 1) {
            playerStates.put(i, new IncidentAvoidPlayerState());
        }
    }
}
