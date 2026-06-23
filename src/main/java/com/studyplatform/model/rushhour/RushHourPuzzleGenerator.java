package com.studyplatform.model.rushhour;

import java.util.*;

/**
 * Random Rush Hour puzzle generator.
 *
 * Strategy: randomly place vehicles on the board, then use BFS to check
 * how many moves are needed. Only puzzles >= minMoves are kept.
 *
 * To get 15-25+ move puzzles reliably, the generator uses a "guided"
 * approach: it always places 2-3 vertical blockers across the red car's
 * exit row, then fills the rest randomly. This dramatically increases
 * the probability of generating hard puzzles.
 */
public class RushHourPuzzleGenerator {

    private static final int B = 6;
    private static final String[] COLORS = {
        "#3498db", "#9b59b6", "#f39c12", "#1abc9c",
        "#e67e22", "#c0392b", "#16a085", "#8e44ad",
        "#27ae60", "#e91e63", "#f39c12", "#00bcd4"
    };

    /**
     * Generate {@code count} puzzles each requiring at least {@code minMoves} BFS moves.
     * Stops after {@code maxAttempts} candidate generations regardless.
     */
    public static List<List<RushHourVehicle>> generate(int count, int minMoves, int maxAttempts) {
        Random rng = new Random();
        List<List<RushHourVehicle>> result = new ArrayList<>();

        for (int attempt = 0; attempt < maxAttempts && result.size() < count; attempt++) {
            List<RushHourVehicle> candidate = buildCandidate(rng);
            if (candidate == null) continue;

            int moves = RushHourSolver.solve(candidate);
            if (moves >= minMoves) {
                result.add(candidate);
                System.out.printf("[RushHour] Generated puzzle %d/%d: %d moves%n",
                        result.size(), count, moves);
            }
        }

        System.out.printf("[RushHour] Generator done: %d/%d puzzles with %d+ moves%n",
                result.size(), count, minMoves);
        return result;
    }

    /**
     * Build one random candidate board.
     * Always blocks red car's row with at least 1 vertical vehicle.
     * Adds 4-7 more random vehicles for complexity.
     */
    private static List<RushHourVehicle> buildCandidate(Random rng) {
        int[][] grid = new int[B][B];
        for (int[] row : grid) Arrays.fill(row, -1);
        List<RushHourVehicle> vehicles = new ArrayList<>();

        // Red car: row=2, col=0, len=2, horizontal (always)
        place(grid, vehicles, 0, 2, 0, 2, true);

        int id = 1;

        // ── Step 1: place 1-3 vertical blockers crossing row 2 ──────────────
        // Choose 1-2 blocker columns from {2, 3, 4}
        int[] cols = {2, 3, 4};
        shuffle(cols, rng);
        int numBlockers = 1 + rng.nextInt(2); // 1 or 2 blockers

        for (int bi = 0; bi < numBlockers && id < 10; bi++) {
            int col = cols[bi];
            int len = rng.nextBoolean() ? 2 : 3;
            // Top of vehicle: either starts at row1 (covers rows 1-2 or 1-3)
            //                 or starts at row2 (covers rows 2-3 or 2-4)
            int topRow = rng.nextBoolean() ? 1 : 2;
            if (topRow + len > B) topRow = B - len;

            if (canPlace(grid, topRow, col, len, false)) {
                place(grid, vehicles, id++, topRow, col, len, false);
            }
        }

        // If no blocker was placed, the puzzle is trivially solved — discard
        if (id == 1) return null;

        // ── Step 2: place 4-7 random vehicles ───────────────────────────────
        int numExtra = 4 + rng.nextInt(4);
        for (int e = 0; e < numExtra && id < 12; e++) {
            if (!placeRandom(grid, vehicles, id, rng)) continue;
            id++;
        }

        return vehicles.size() >= 4 ? vehicles : null;
    }

    /** Try to place a random vehicle; returns true if successful. */
    private static boolean placeRandom(int[][] grid, List<RushHourVehicle> vehicles, int id, Random rng) {
        for (int attempt = 0; attempt < 40; attempt++) {
            boolean horiz = rng.nextBoolean();
            int len       = rng.nextBoolean() ? 2 : 3;
            int row, col;

            if (horiz) {
                row = rng.nextInt(B);
                col = rng.nextInt(B - len + 1);
                // Avoid placing horizontal cars directly in row 2 at col>=2
                // (too simple a block; vertical vehicles handle that)
                if (row == 2 && col >= 2) continue;
            } else {
                row = rng.nextInt(B - len + 1);
                col = rng.nextInt(B);
            }

            if (canPlace(grid, row, col, len, horiz)) {
                place(grid, vehicles, id, row, col, len, horiz);
                return true;
            }
        }
        return false;
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static boolean canPlace(int[][] grid, int row, int col, int len, boolean horiz) {
        for (int k = 0; k < len; k++) {
            int r = horiz ? row     : row + k;
            int c = horiz ? col + k : col;
            if (r < 0 || r >= B || c < 0 || c >= B) return false;
            if (grid[r][c] != -1) return false;
        }
        return true;
    }

    private static void place(int[][] grid, List<RushHourVehicle> vehicles,
                              int id, int row, int col, int len, boolean horiz) {
        for (int k = 0; k < len; k++) {
            int r = horiz ? row     : row + k;
            int c = horiz ? col + k : col;
            grid[r][c] = id;
        }
        String color = id == 0 ? "#e74c3c" : COLORS[(id - 1) % COLORS.length];
        vehicles.add(new RushHourVehicle(id, row, col, len, horiz, color));
    }

    private static void shuffle(int[] arr, Random rng) {
        for (int i = arr.length - 1; i > 0; i--) {
            int j = rng.nextInt(i + 1);
            int t = arr[i]; arr[i] = arr[j]; arr[j] = t;
        }
    }
}
