package com.studyplatform.model.ubongo;

import java.util.*;

/** Immutable puzzle card: which cells are blocked + which pieces to place. */
public class PuzzleCard {

    private final boolean[][] blocked;   // 5x5; true = blocked (cannot place)
    private final List<String> pieceIds; // piece IDs required for this puzzle

    public PuzzleCard(boolean[][] blocked, List<String> pieceIds) {
        this.blocked = new boolean[5][5];
        for (int r = 0; r < 5; r++) this.blocked[r] = blocked[r].clone();
        this.pieceIds = Collections.unmodifiableList(new ArrayList<>(pieceIds));
    }

    /** Returns a defensive copy of the blocked array. */
    public boolean[][] getBlocked() {
        boolean[][] copy = new boolean[5][5];
        for (int r = 0; r < 5; r++) copy[r] = blocked[r].clone();
        return copy;
    }

    public List<String> getPieceIds() { return pieceIds; }

    /** Total cells that must be filled (non-blocked). */
    public int openCells() {
        int count = 0;
        for (boolean[] row : blocked) for (boolean b : row) if (!b) count++;
        return count;
    }
}
