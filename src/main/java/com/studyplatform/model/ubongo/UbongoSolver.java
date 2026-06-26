package com.studyplatform.model.ubongo;

import java.util.*;

/**
 * Backtracking solver for Ubongo puzzles.
 *
 * Strategy — "fill the first empty cell":
 *   1. Find the top-left unoccupied, non-blocked cell.
 *   2. That cell MUST be covered by some unused piece.
 *   3. Try every unused piece in every orientation at every position that covers it.
 *   4. If no piece can cover it → prune (return false).
 *
 * This keeps the search space tiny for 5×5 boards.
 */
public class UbongoSolver {

    private static final int N = 5;

    // ── Public API ────────────────────────────────────────────────────────────

    /** Returns true if the piece set can tile all non-blocked cells. */
    public static boolean canSolve(boolean[][] blocked, List<String> pieceIds) {
        boolean[][] board = copyAndBlock(blocked);
        List<UbongoPiece> pieces = new ArrayList<>();
        for (String id : pieceIds) pieces.add(UbongoPiece.get(id));
        return solve(board, pieces, new boolean[pieces.size()]);
    }

    /**
     * Randomly generate puzzle cards.
     * @param count     target number of puzzles
     * @param minPieces min pieces per puzzle (inclusive)
     * @param maxPieces max pieces per puzzle (inclusive)
     * @param maxTries  max generation attempts (guards startup time)
     */
    public static List<PuzzleCard> generatePuzzles(int count, int minPieces, int maxPieces, int maxTries) {
        Random rng = new Random();
        List<String> allIds = UbongoPiece.allIds();
        List<PuzzleCard> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        for (int attempt = 0; attempt < maxTries && result.size() < count; attempt++) {
            // ── Pick pieces greedily so the total fits in N*N cells ────────
            int numPieces = minPieces + rng.nextInt(maxPieces - minPieces + 1);
            List<String> chosen = pickPieces(rng, allIds, numPieces, N * N);
            if (chosen.size() < numPieces) continue; // couldn't find enough fitting pieces

            int totalCells = chosen.stream().mapToInt(id -> UbongoPiece.get(id).size).sum();
            if (totalCells < 12) continue; // too few cells → trivial board

            // ── Randomly place blocked cells in remaining space ────────────
            int numBlocked = N * N - totalCells;
            if (numBlocked < 2) continue;

            List<Integer> positions = new ArrayList<>();
            for (int i = 0; i < N * N; i++) positions.add(i);
            Collections.shuffle(positions, rng);

            boolean[][] blocked = new boolean[N][N];
            for (int i = 0; i < numBlocked; i++) {
                int pos = positions.get(i);
                blocked[pos / N][pos % N] = true;
            }

            // ── Validate with BFS solver ───────────────────────────────────
            String signature = signature(blocked, chosen);
            if (seen.contains(signature)) continue;

            if (canSolve(blocked, chosen)) {
                seen.add(signature);
                result.add(new PuzzleCard(blocked, new ArrayList<>(chosen)));
                System.out.printf("[Ubongo] Puzzle %d/%d ready (%d pieces, %d open cells)%n",
                        result.size(), count, chosen.size(), totalCells);
            }
        }

        System.out.printf("[Ubongo] Pool: %d/%d puzzles generated%n", result.size(), count);
        return result;
    }

    /**
     * Randomly select pieces from the pool such that their total cell count ≤ capacity.
     * Shuffles the pool and greedily picks pieces that still fit.
     */
    private static List<String> pickPieces(Random rng, List<String> allIds, int target, int capacity) {
        List<String> pool = new ArrayList<>(allIds);
        Collections.shuffle(pool, rng);
        List<String> chosen = new ArrayList<>();
        int used = 0;
        for (String id : pool) {
            if (chosen.size() >= target) break;
            int size = UbongoPiece.get(id).size;
            if (used + size <= capacity) { chosen.add(id); used += size; }
        }
        return chosen;
    }

    private static String signature(boolean[][] blocked, List<String> pieceIds) {
        List<String> sorted = new ArrayList<>(pieceIds);
        Collections.sort(sorted);
        StringBuilder sb = new StringBuilder(String.join(",", sorted)).append('|');
        for (int r = 0; r < N; r++)
            for (int c = 0; c < N; c++)
                sb.append(blocked[r][c] ? '1' : '0');
        return sb.toString();
    }

    // ── Backtracking ──────────────────────────────────────────────────────────

    private static boolean solve(boolean[][] board, List<UbongoPiece> pieces, boolean[] used) {
        int[] target = firstEmpty(board);
        if (target == null) {
            for (boolean u : used) if (!u) return false;
            return true;
        }

        for (int pi = 0; pi < pieces.size(); pi++) {
            if (used[pi]) continue;
            UbongoPiece piece = pieces.get(pi);

            for (int[][] orient : piece.orientations) {
                // Try each cell of this orientation as the one covering target
                for (int[] cell : orient) {
                    int sr = target[0] - cell[0];
                    int sc = target[1] - cell[1];

                    if (canPlace(board, orient, sr, sc)) {
                        apply(board, orient, sr, sc, true);
                        used[pi] = true;

                        if (solve(board, pieces, used)) return true;

                        apply(board, orient, sr, sc, false);
                        used[pi] = false;
                    }
                }
            }
        }

        return false; // no piece can cover target
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static int[] firstEmpty(boolean[][] board) {
        for (int r = 0; r < N; r++)
            for (int c = 0; c < N; c++)
                if (!board[r][c]) return new int[]{r, c};
        return null;
    }

    static boolean canPlace(boolean[][] board, int[][] orient, int sr, int sc) {
        for (int[] cell : orient) {
            int r = sr + cell[0], c = sc + cell[1];
            if (r < 0 || r >= N || c < 0 || c >= N || board[r][c]) return false;
        }
        return true;
    }

    private static void apply(boolean[][] board, int[][] orient, int sr, int sc, boolean val) {
        for (int[] cell : orient) board[sr + cell[0]][sc + cell[1]] = val;
    }

    private static boolean[][] copyAndBlock(boolean[][] blocked) {
        boolean[][] b = new boolean[N][N];
        for (int r = 0; r < N; r++) b[r] = blocked[r].clone();
        return b;
    }
}
