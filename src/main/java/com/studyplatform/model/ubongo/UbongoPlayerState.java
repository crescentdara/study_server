package com.studyplatform.model.ubongo;

import java.util.*;

/** Per-player state: which pieces are placed and where. */
public class UbongoPlayerState {

    public static class PlacedPiece {
        private int row, col, orientationIndex;
        public PlacedPiece(int row, int col, int orientationIndex) {
            this.row = row; this.col = col; this.orientationIndex = orientationIndex;
        }
        public int getRow()              { return row; }
        public int getCol()              { return col; }
        public int getOrientationIndex() { return orientationIndex; }
    }

    private final Map<String, PlacedPiece> placements = new LinkedHashMap<>();
    private boolean solved      = false;
    private long    solveTimeMs = 0;

    // ── Place ─────────────────────────────────────────────────────────────────

    /**
     * Attempt to place pieceId with the given orientation at (row, col).
     * Returns null on success, or an error message on failure.
     */
    public String place(String pieceId, int row, int col, int orientIdx, PuzzleCard puzzle) {
        if (solved) return "Player already solved";
        UbongoPiece piece = UbongoPiece.get(pieceId);
        if (piece == null)                           return "Unknown piece: " + pieceId;
        if (!puzzle.getPieceIds().contains(pieceId)) return "Piece not in puzzle";
        if (placements.containsKey(pieceId))         return "Piece already placed";
        if (orientIdx < 0 || orientIdx >= piece.orientations.size())
            return "Invalid orientation index";

        int[][] orient = piece.orientations.get(orientIdx);

        // Build current occupancy (blocked + already-placed pieces)
        boolean[][] occupied = buildOccupied(puzzle.getBlocked());

        for (int[] cell : orient) {
            int r = row + cell[0], c = col + cell[1];
            if (r < 0 || r >= 5 || c < 0 || c >= 5) return "Out of bounds";
            if (occupied[r][c])                       return "Cell blocked or occupied";
        }

        placements.put(pieceId, new PlacedPiece(row, col, orientIdx));
        return null;
    }

    // ── Remove ────────────────────────────────────────────────────────────────

    public boolean remove(String pieceId) {
        if (solved) return false;
        boolean removed = placements.remove(pieceId) != null;
        if (removed) solved = false;  // allow re-placement after partial removal
        return removed;
    }

    // ── Solve check ───────────────────────────────────────────────────────────

    /**
     * True when all puzzle pieces are placed.
     * Placement validity (no overlap, no blocked) is enforced in place(), so if all
     * N pieces are placed they exactly cover all N*B - blocked cells.
     */
    public boolean checkSolved(PuzzleCard puzzle) {
        if (placements.size() != puzzle.getPieceIds().size()) return false;
        boolean[][] occupied = buildOccupied(puzzle.getBlocked());
        for (int r = 0; r < 5; r += 1) {
            for (int c = 0; c < 5; c += 1) {
                if (!occupied[r][c]) return false;
            }
        }
        return true;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private boolean[][] buildOccupied(boolean[][] blocked) {
        boolean[][] occ = new boolean[5][5];
        for (int r = 0; r < 5; r++) occ[r] = blocked[r].clone();
        for (Map.Entry<String, PlacedPiece> e : placements.entrySet()) {
            UbongoPiece piece = UbongoPiece.get(e.getKey());
            PlacedPiece pp = e.getValue();
            int[][] orient = piece.orientations.get(pp.orientationIndex);
            for (int[] cell : orient) {
                occ[pp.row + cell[0]][pp.col + cell[1]] = true;
            }
        }
        return occ;
    }

    // ── Getters / Setters ─────────────────────────────────────────────────────

    public Map<String, PlacedPiece> getPlacements()  { return Collections.unmodifiableMap(placements); }
    public boolean isSolved()                         { return solved; }
    public long    getSolveTimeMs()                   { return solveTimeMs; }
    public void    setSolved(boolean s)               { this.solved = s; }
    public void    setSolveTimeMs(long t)             { this.solveTimeMs = t; }
}
