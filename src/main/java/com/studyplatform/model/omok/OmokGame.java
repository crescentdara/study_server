package com.studyplatform.model.omok;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class OmokGame {
    private final int size;
    private final int numPlayers;
    private final int[][] board;
    private final List<int[]> winPath = new ArrayList<>();

    private int currentTurn = 0;
    private int winner = -1;
    private int moveCount = 0;
    private int lastRow = -1;
    private int lastCol = -1;

    public OmokGame(int size, int numPlayers) {
        this.size = 19;
        this.numPlayers = numPlayers;
        this.board = new int[this.size][this.size];
    }

    public void placeStone(int playerIndex, int row, int col) {
        if (winner >= 0) throw new IllegalStateException("Game already finished.");
        if (playerIndex != currentTurn) throw new IllegalStateException("Not your turn.");
        if (row < 0 || row >= size || col < 0 || col >= size) {
            throw new IllegalArgumentException("Cell is outside the board.");
        }
        if (board[row][col] != 0) throw new IllegalArgumentException("Cell already has a stone.");

        int mark = playerIndex + 1;
        board[row][col] = mark;

        if (playerIndex == 0 && !hasFive(row, col, mark) && countOpenThrees(row, col, mark) >= 2) {
            board[row][col] = 0;
            winPath.clear();
            throw new IllegalArgumentException("P1 cannot place a 3-3 forbidden move.");
        }

        moveCount++;
        lastRow = row;
        lastCol = col;

        if (hasFive(row, col, mark)) {
            winner = playerIndex;
        } else {
            currentTurn = (currentTurn + 1) % numPlayers;
        }
    }

    public boolean isDraw() {
        return winner < 0 && moveCount >= size * size;
    }

    private boolean hasFive(int row, int col, int mark) {
        int[][] directions = { {1, 0}, {0, 1}, {1, 1}, {1, -1} };
        for (int[] d : directions) {
            List<int[]> path = collectLine(row, col, d[0], d[1], mark);
            if (path.size() >= 5) {
                winPath.clear();
                winPath.addAll(path);
                return true;
            }
        }
        return false;
    }

    private int countOpenThrees(int row, int col, int mark) {
        int count = 0;
        int[][] directions = { {1, 0}, {0, 1}, {1, 1}, {1, -1} };
        for (int[] d : directions) {
            if (hasOpenThree(row, col, d[0], d[1], mark)) count++;
        }
        return count;
    }

    private boolean hasOpenThree(int row, int col, int dr, int dc, int mark) {
        StringBuilder line = new StringBuilder();
        int center = 4;
        for (int i = -4; i <= 4; i++) {
            int r = row + dr * i;
            int c = col + dc * i;
            if (r < 0 || r >= size || c < 0 || c >= size) {
                line.append('O');
            } else if (board[r][c] == mark) {
                line.append('X');
            } else if (board[r][c] == 0) {
                line.append('.');
            } else {
                line.append('O');
            }
        }

        String s = line.toString();
        return containsPatternThroughCenter(s, ".XXX.", center)
                || containsPatternThroughCenter(s, ".XX.X.", center)
                || containsPatternThroughCenter(s, ".X.XX.", center);
    }

    private boolean containsPatternThroughCenter(String line, String pattern, int center) {
        for (int i = 0; i <= line.length() - pattern.length(); i++) {
            if (i <= center && center < i + pattern.length()
                    && line.substring(i, i + pattern.length()).equals(pattern)) {
                return true;
            }
        }
        return false;
    }

    private List<int[]> collectLine(int row, int col, int dr, int dc, int mark) {
        List<int[]> line = new ArrayList<>();
        collect(row, col, -dr, -dc, mark, line);
        line.add(new int[] { row, col });
        collect(row, col, dr, dc, mark, line);
        return line;
    }

    private void collect(int row, int col, int dr, int dc, int mark, List<int[]> line) {
        int r = row + dr;
        int c = col + dc;
        while (r >= 0 && r < size && c >= 0 && c < size && board[r][c] == mark) {
            if (dr < 0 || (dr == 0 && dc < 0)) {
                line.add(0, new int[] { r, c });
            } else {
                line.add(new int[] { r, c });
            }
            r += dr;
            c += dc;
        }
    }
}
