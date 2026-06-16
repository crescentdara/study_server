package com.studyplatform.model.catchmind;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

@Data
public class CatchMindGame {
    private final int numPlayers;
    private final int maxRounds;
    private final int[] scores;
    private final List<Map<String, Object>> strokes = new CopyOnWriteArrayList<>();
    private final List<String> recentGuesses = new CopyOnWriteArrayList<>();
    private int round = 1;
    private int currentTurn = 0;
    private int winner = -1;
    private String secretWord = "";
    private String revealedWord = "";
    private int solvedBy = -1;
    private boolean roundSolved = false;

    public CatchMindGame(int numPlayers) {
        this.numPlayers = numPlayers;
        this.maxRounds = Math.max(2, numPlayers * 2);
        this.scores = new int[numPlayers];
    }

    public void nextRound() {
        if (round >= maxRounds) {
            finish();
            return;
        }
        round += 1;
        currentTurn = (currentTurn + 1) % Math.max(1, numPlayers);
        secretWord = "";
        revealedWord = "";
        solvedBy = -1;
        roundSolved = false;
        strokes.clear();
        recentGuesses.clear();
    }

    public void finish() {
        int best = -1;
        int bestScore = Integer.MIN_VALUE;
        for (int i = 0; i < scores.length; i += 1) {
            if (scores[i] > bestScore) {
                best = i;
                bestScore = scores[i];
            }
        }
        winner = best;
    }

    public String getMaskedWord() {
        return "_".repeat(secretWord == null ? 0 : secretWord.length());
    }

    public List<Map<String, Object>> getStrokesSnapshot() {
        return new ArrayList<>(strokes);
    }

}
