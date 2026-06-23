package com.studyplatform.model.rushhour;

import java.util.*;

/**
 * BFS solver for Rush Hour puzzles.
 * Returns the minimum number of moves to solve a puzzle, or -1 if unsolvable.
 * A "move" = sliding one vehicle any distance in one direction.
 */
public class RushHourSolver {
    private static final int B = 6;

    public static int solve(List<RushHourVehicle> vehicles) {
        int n = vehicles.size();
        String init = encode(vehicles, n);
        if (isWin(vehicles, init, n)) return 0;

        Deque<String> queue = new ArrayDeque<>();
        Map<String, Integer> dist = new HashMap<>();
        queue.add(init);
        dist.put(init, 0);

        while (!queue.isEmpty()) {
            String state = queue.poll();
            int d = dist.get(state);
            int[] pos = decode(state, n);
            int[][] grid = buildGrid(vehicles, pos, n);

            for (int vi = 0; vi < n; vi++) {
                RushHourVehicle v = vehicles.get(vi);
                int row = pos[vi * 2], col = pos[vi * 2 + 1];

                for (int[] m : getMoves(v, row, col, grid)) {
                    int[] np = pos.clone();
                    np[vi * 2]     = m[0];
                    np[vi * 2 + 1] = m[1];
                    String nextKey = encodeArr(np, n);

                    if (v.getId() == 0 && v.isHorizontal() && m[1] + v.getLength() == B) return d + 1;

                    if (!dist.containsKey(nextKey)) {
                        dist.put(nextKey, d + 1);
                        queue.add(nextKey);
                    }
                }
            }
        }
        return -1;
    }

    private static boolean isWin(List<RushHourVehicle> vehicles, String state, int n) {
        int[] pos = decode(state, n);
        for (int i = 0; i < n; i++) {
            RushHourVehicle v = vehicles.get(i);
            if (v.getId() == 0 && v.isHorizontal()) {
                return pos[i * 2 + 1] + v.getLength() == B;
            }
        }
        return false;
    }

    private static int[][] buildGrid(List<RushHourVehicle> vehicles, int[] pos, int n) {
        int[][] grid = new int[B][B];
        for (int[] row : grid) Arrays.fill(row, -1);
        for (int i = 0; i < n; i++) {
            RushHourVehicle v = vehicles.get(i);
            int row = pos[i * 2], col = pos[i * 2 + 1];
            for (int k = 0; k < v.getLength(); k++) {
                int r = v.isHorizontal() ? row     : row + k;
                int c = v.isHorizontal() ? col + k : col;
                if (r >= 0 && r < B && c >= 0 && c < B) grid[r][c] = v.getId();
            }
        }
        return grid;
    }

    private static List<int[]> getMoves(RushHourVehicle v, int row, int col, int[][] grid) {
        List<int[]> moves = new ArrayList<>();
        if (v.isHorizontal()) {
            for (int c = col - 1; c >= 0; c--) {
                if (grid[row][c] != -1) break;
                moves.add(new int[]{row, c});
            }
            for (int c = col + 1; c + v.getLength() - 1 < B; c++) {
                if (grid[row][c + v.getLength() - 1] != -1) break;
                moves.add(new int[]{row, c});
            }
        } else {
            for (int r = row - 1; r >= 0; r--) {
                if (grid[r][col] != -1) break;
                moves.add(new int[]{r, col});
            }
            for (int r = row + 1; r + v.getLength() - 1 < B; r++) {
                if (grid[r + v.getLength() - 1][col] != -1) break;
                moves.add(new int[]{r, col});
            }
        }
        return moves;
    }

    private static String encode(List<RushHourVehicle> vehicles, int n) {
        char[] c = new char[n * 2];
        for (int i = 0; i < n; i++) {
            c[i * 2]     = (char)('0' + vehicles.get(i).getRow());
            c[i * 2 + 1] = (char)('0' + vehicles.get(i).getCol());
        }
        return new String(c);
    }

    private static String encodeArr(int[] pos, int n) {
        char[] c = new char[n * 2];
        for (int i = 0; i < n * 2; i++) c[i] = (char)('0' + pos[i]);
        return new String(c);
    }

    private static int[] decode(String state, int n) {
        int[] pos = new int[n * 2];
        for (int i = 0; i < n * 2; i++) pos[i] = state.charAt(i) - '0';
        return pos;
    }
}
