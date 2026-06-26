package com.studyplatform.model.ubongo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class UbongoGame {
    private static final Random RNG = new Random();
    private static volatile List<PuzzleCard> puzzlePool;
    private static int lastPuzzleIndex = -1;

    private final PuzzleCard puzzle;
    private final List<UbongoPlayerState> playerStates;
    private final int numPlayers;
    private final long startTime;
    private int winner = -1;

    public UbongoGame(int numPlayers) {
        this.numPlayers = numPlayers;
        this.startTime = System.currentTimeMillis();
        this.puzzle = nextPuzzle();
        this.playerStates = new ArrayList<>();
        for (int i = 0; i < numPlayers; i += 1) {
            playerStates.add(new UbongoPlayerState());
        }
    }

    private static List<PuzzleCard> pool() {
        List<PuzzleCard> current = puzzlePool;
        if (current != null) return current;
        synchronized (UbongoGame.class) {
            if (puzzlePool != null) return puzzlePool;
            System.out.println("[Ubongo] Generating puzzle pool (6-7 pieces)...");
            List<PuzzleCard> generated = UbongoSolver.generatePuzzles(40, 6, 7, 15000);
            if (generated.isEmpty()) {
                generated = UbongoSolver.generatePuzzles(10, 4, 5, 8000);
            }
            if (generated.isEmpty()) {
                throw new ExceptionInInitializerError("[Ubongo] Failed to generate any puzzles.");
            }
            puzzlePool = Collections.unmodifiableList(generated);
            System.out.println("[Ubongo] Pool ready: " + puzzlePool.size() + " puzzles");
            return puzzlePool;
        }
    }

    private static PuzzleCard nextPuzzle() {
        List<PuzzleCard> pool = pool();
        synchronized (RNG) {
            int index = RNG.nextInt(pool.size());
            if (pool.size() > 1 && index == lastPuzzleIndex) {
                index = (index + 1 + RNG.nextInt(pool.size() - 1)) % pool.size();
            }
            lastPuzzleIndex = index;
            return pool.get(index);
        }
    }

    public String placePiece(int playerIndex, String pieceId, int row, int col, int orientIdx) {
        if (playerIndex < 0 || playerIndex >= numPlayers) return "Invalid player";
        UbongoPlayerState state = playerStates.get(playerIndex);
        if (state.isSolved()) return "Player already solved";

        String err = state.place(pieceId, row, col, orientIdx, puzzle);
        if (err != null) return err;

        if (state.checkSolved(puzzle)) {
            state.setSolved(true);
            state.setSolveTimeMs(System.currentTimeMillis() - startTime);
            if (winner == -1) winner = playerIndex;
        }
        return null;
    }

    public String removePiece(int playerIndex, String pieceId) {
        if (playerIndex < 0 || playerIndex >= numPlayers) return "Invalid player";
        UbongoPlayerState state = playerStates.get(playerIndex);
        if (state.isSolved()) return "Player already solved";
        return state.remove(pieceId) ? null : "Piece is not placed";
    }

    public PuzzleCard getPuzzle() {
        return puzzle;
    }

    public List<UbongoPlayerState> getPlayerStates() {
        return playerStates;
    }

    public int getNumPlayers() {
        return numPlayers;
    }

    public int getWinner() {
        return winner;
    }

    public long getStartTime() {
        return startTime;
    }
}
