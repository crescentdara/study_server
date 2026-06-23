package com.studyplatform.model.ubongo;

import java.util.*;

/**
 * Ubongo game model.
 *
 * 40 puzzle cards are generated once at class-load time.
 * Each game (constructor call) picks a random card from the pool so every
 * game/restart gets a different puzzle.
 */
public class UbongoGame {

    // ── Puzzle pool (generated once at startup) ───────────────────────────────

    private static final List<PuzzleCard> POOL = new ArrayList<>();
    private static final Random RNG = new Random();

    static {
        System.out.println("[Ubongo] Generating puzzle pool (6–7 pieces)...");
        POOL.addAll(UbongoSolver.generatePuzzles(40, 6, 7, 15000));
        if (POOL.isEmpty())
            throw new ExceptionInInitializerError("[Ubongo] Failed to generate any puzzles.");
        System.out.println("[Ubongo] Pool ready: " + POOL.size() + " puzzles");
    }

    // ── Instance state ────────────────────────────────────────────────────────

    private final PuzzleCard puzzle;
    private final List<UbongoPlayerState> playerStates;
    private final int    numPlayers;
    private final long   startTime;
    private int winner = -1;

    public UbongoGame(int numPlayers) {
        this.numPlayers   = numPlayers;
        this.startTime    = System.currentTimeMillis();
        this.puzzle       = POOL.get(RNG.nextInt(POOL.size()));
        this.playerStates = new ArrayList<>();
        for (int i = 0; i < numPlayers; i++) playerStates.add(new UbongoPlayerState());
    }

    // ── Game actions ──────────────────────────────────────────────────────────

    /**
     * Place a piece for the given player.
     * @return null on success, error message on failure
     */
    public String placePiece(int playerIndex, String pieceId, int row, int col, int orientIdx) {
        if (playerIndex < 0 || playerIndex >= numPlayers) return "Invalid player";
        UbongoPlayerState state = playerStates.get(playerIndex);
        if (state.isSolved()) return "Already solved";

        String err = state.place(pieceId, row, col, orientIdx, puzzle);
        if (err != null) return err;

        if (state.checkSolved(puzzle)) {
            state.setSolved(true);
            state.setSolveTimeMs(System.currentTimeMillis() - startTime);
            if (winner == -1) winner = playerIndex;
        }
        return null;
    }

    /** Remove a previously placed piece for the given player. */
    public boolean removePiece(int playerIndex, String pieceId) {
        if (playerIndex < 0 || playerIndex >= numPlayers) return false;
        return playerStates.get(playerIndex).remove(pieceId);
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public PuzzleCard                getPuzzle()       { return puzzle; }
    public List<UbongoPlayerState>   getPlayerStates() { return playerStates; }
    public int                       getNumPlayers()   { return numPlayers; }
    public int                       getWinner()       { return winner; }
    public long                      getStartTime()    { return startTime; }
}
