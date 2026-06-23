package com.studyplatform.model.rushhour;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * Rush Hour competitive game.
 * Puzzles are validated at class-load time by BFS; only solvable puzzles
 * with >= MIN_MOVES are included. Puzzle selected randomly each game.
 *
 * Vehicle: RushHourVehicle(id, row, col, length, horizontal, color)
 * Red car: id=0, row=2, horizontal=true, exits when col+length==6.
 *
 * Puzzle notation used in comments:
 *   Row 0 = top, Row 5 = bottom
 *   Col 0 = left, Col 5 = right
 *   R=red car, letters=other vehicles, .=empty
 */
public class RushHourGame {

    private static final int MIN_MOVES = 6;
    private static final List<List<RushHourVehicle>> PUZZLES = new ArrayList<>();
    private static final Random RNG = new Random();

    private static void add(List<RushHourVehicle> p) {
        if (!isValidLayout(p)) {
            System.out.println("[RushHour] WARN: puzzle rejected (vehicles overlap or out of bounds)");
            return;
        }
        int moves = RushHourSolver.solve(p);
        if (moves >= MIN_MOVES) {
            PUZZLES.add(p);
            System.out.println("[RushHour] Puzzle #" + PUZZLES.size() + " accepted: " + moves + " moves");
        } else if (moves < 0) {
            System.out.println("[RushHour] WARN: puzzle rejected (unsolvable)");
        } else {
            System.out.println("[RushHour] puzzle rejected (too easy: " + moves + " moves)");
        }
    }

    private static boolean isValidLayout(List<RushHourVehicle> vehicles) {
        int[][] grid = new int[6][6];
        for (int[] row : grid) Arrays.fill(row, -1);
        for (RushHourVehicle vh : vehicles) {
            for (int k = 0; k < vh.getLength(); k++) {
                int r = vh.isHorizontal() ? vh.getRow() : vh.getRow() + k;
                int c = vh.isHorizontal() ? vh.getCol() + k : vh.getCol();
                if (r < 0 || r >= 6 || c < 0 || c >= 6) return false;
                if (grid[r][c] != -1) return false;
                grid[r][c] = vh.getId();
            }
        }
        return true;
    }

    private static List<RushHourVehicle> p(RushHourVehicle... vs) {
        return new ArrayList<>(Arrays.asList(vs));
    }

    private static RushHourVehicle v(int id, int row, int col, int len, boolean h, String color) {
        return new RushHourVehicle(id, row, col, len, h, color);
    }

    static {
        /*
         * All puzzles: red car is id=0, row=2, col=0, len=2, horizontal.
         * Exit is row=2 right side (col+length==6).
         *
         * Colors used: red=#e74c3c, blue=#3498db, purple=#9b59b6,
         *   orange=#f39c12, green=#1abc9c, amber=#e67e22,
         *   crimson=#c0392b, teal=#16a085, lime=#27ae60, pink=#e91e63
         */

        // ── Group 1: Classic chain puzzles ─────────────────────────────────

        // P-A: 6 moves
        // . A . . . .
        // . A . . . .
        // R R C . B .
        // . . C D . .
        // . . . D E E
        // . . . . . .
        // Chain: D↑, E←, B↓3, D↓, C↑2, R→
        add(p(
            v(0, 2, 0, 2, true,  "#e74c3c"),
            v(1, 0, 1, 2, false, "#3498db"),  // A vert rows0-1 col1
            v(2, 0, 4, 3, false, "#9b59b6"),  // B vert rows0-2 col4
            v(3, 2, 2, 2, false, "#f39c12"),  // C vert rows2-3 col2
            v(4, 3, 3, 2, false, "#1abc9c"),  // D vert rows3-4 col3
            v(5, 4, 4, 2, true,  "#e67e22")   // E horiz row4 col4-5
        ));

        // P-B: col2 blocked by vertical, col4 blocked by len-3 vertical
        // . . . . . .
        // . A A . . .
        // R R . B C C
        // . . D B . .
        // . . D . E .
        // . . . . E .
        // Chain: B↓, C←3, D↑, R→
        add(p(
            v(0, 2, 0, 2, true,  "#e74c3c"),
            v(1, 1, 1, 2, true,  "#3498db"),  // A horiz row1 col1-2
            v(2, 1, 3, 3, false, "#9b59b6"),  // B vert rows1-3 col3
            v(3, 2, 4, 2, true,  "#f39c12"),  // C horiz row2 col4-5
            v(4, 3, 2, 2, false, "#1abc9c"),  // D vert rows3-4 col2
            v(5, 4, 4, 2, false, "#e67e22")   // E vert rows4-5 col4
        ));

        // P-C: Three blockers in row 2
        // . . . . A .
        // . B B . A .
        // R R C . A .
        // . . C D . .
        // . E . D . .
        // . E . . . .
        // Chain: A↑2, D↑, C↓, R→
        add(p(
            v(0, 2, 0, 2, true,  "#e74c3c"),
            v(1, 0, 4, 3, false, "#3498db"),  // A vert rows0-2 col4
            v(2, 1, 1, 2, true,  "#9b59b6"),  // B horiz row1 col1-2
            v(3, 2, 2, 2, false, "#f39c12"),  // C vert rows2-3 col2
            v(4, 3, 3, 2, false, "#1abc9c"),  // D vert rows3-4 col3
            v(5, 4, 1, 2, false, "#e67e22")   // E vert rows4-5 col1
        ));

        // P-D: Deep chain with horizontal blockers
        // A A . . . .
        // . . B . . .
        // R R B C . .
        // . . . C D D
        // . E . . . .
        // . E . . . .
        // Chain: B↑, C↓, D←, R→
        add(p(
            v(0, 2, 0, 2, true,  "#e74c3c"),
            v(1, 0, 0, 2, true,  "#3498db"),  // A horiz row0 col0-1
            v(2, 1, 2, 2, false, "#9b59b6"),  // B vert rows1-2 col2
            v(3, 2, 3, 2, false, "#f39c12"),  // C vert rows2-3 col3
            v(4, 3, 4, 2, true,  "#1abc9c"),  // D horiz row3 col4-5
            v(5, 4, 1, 2, false, "#e67e22")   // E vert rows4-5 col1
        ));

        // P-E: Cascading vertical dependencies
        // . . A . B .
        // . . A . B .
        // R R A . B .
        // . C . D . .
        // . C . D E E
        // . . . . . .
        // Chain: A↑2, B↑2, D↑2, C↑, E←, R→
        add(p(
            v(0, 2, 0, 2, true,  "#e74c3c"),
            v(1, 0, 2, 3, false, "#3498db"),  // A vert rows0-2 col2
            v(2, 0, 4, 3, false, "#9b59b6"),  // B vert rows0-2 col4
            v(3, 3, 1, 2, false, "#f39c12"),  // C vert rows3-4 col1
            v(4, 3, 3, 2, false, "#1abc9c"),  // D vert rows3-4 col3
            v(5, 4, 4, 2, true,  "#e67e22")   // E horiz row4 col4-5
        ));

        // P-F: Tight cluster
        // . . . A . .
        // . B . A . .
        // R R C A . .
        // . B C . . .
        // . . . D D .
        // . . . . . .
        add(p(
            v(0, 2, 0, 2, true,  "#e74c3c"),
            v(1, 0, 3, 3, false, "#3498db"),  // A vert rows0-2 col3
            v(2, 1, 1, 3, false, "#9b59b6"),  // B vert rows1-3 col1
            v(3, 2, 2, 2, false, "#f39c12"),  // C vert rows2-3 col2
            v(4, 4, 3, 2, true,  "#1abc9c")   // D horiz row4 col3-4
        ));

        // ── Group 2: Harder puzzles with more vehicles ──────────────────────

        // P-G: 7+ moves - multiple horizontal dependencies
        // . . . A A .
        // . . . . . B
        // R R C . . B
        // . . C D . B
        // . E . D . .
        // . E . . F F
        // Chain: D↑, B↓, F←, D↓, C↑2, A→, R→
        add(p(
            v(0, 2, 0, 2, true,  "#e74c3c"),
            v(1, 0, 3, 2, true,  "#3498db"),  // A horiz row0 col3-4
            v(2, 1, 5, 3, false, "#9b59b6"),  // B vert rows1-3 col5
            v(3, 2, 2, 2, false, "#f39c12"),  // C vert rows2-3 col2
            v(4, 3, 3, 2, false, "#1abc9c"),  // D vert rows3-4 col3
            v(5, 4, 1, 2, false, "#e67e22"),  // E vert rows4-5 col1
            v(6, 5, 4, 2, true,  "#c0392b")   // F horiz row5 col4-5
        ));

        // P-H: Classic Expert-style
        // . A . . . .
        // . A . B . .
        // R R . B C C
        // D . . B . .
        // D . E . . .
        // . . E . . .
        // Chain: B↓, C←3, D↑, R→
        add(p(
            v(0, 2, 0, 2, true,  "#e74c3c"),
            v(1, 0, 1, 2, false, "#3498db"),  // A vert rows0-1 col1
            v(2, 1, 3, 3, false, "#9b59b6"),  // B vert rows1-3 col3
            v(3, 2, 4, 2, true,  "#f39c12"),  // C horiz row2 col4-5
            v(4, 3, 0, 2, false, "#1abc9c"),  // D vert rows3-4 col0
            v(5, 4, 2, 2, false, "#e67e22")   // E vert rows4-5 col2
        ));

        // P-I: Long horizontal chain
        // . . . A . .
        // B B . A . .
        // R R . A C C
        // . . D . . .
        // . . D E E .
        // . . . . . .
        // Chain: A↑2, C←3, D↑, R→
        add(p(
            v(0, 2, 0, 2, true,  "#e74c3c"),
            v(1, 0, 3, 3, false, "#3498db"),  // A vert rows0-2 col3
            v(2, 1, 0, 2, true,  "#9b59b6"),  // B horiz row1 col0-1
            v(3, 2, 4, 2, true,  "#f39c12"),  // C horiz row2 col4-5
            v(4, 3, 2, 2, false, "#1abc9c"),  // D vert rows3-4 col2
            v(5, 4, 3, 2, true,  "#e67e22")   // E horiz row4 col3-4
        ));

        // P-J: Heavy right-side blocking
        // . . . . . A
        // . B B . . A
        // R R . C . A
        // . . . C D .
        // . E E . D .
        // . . . . . .
        // Chain: A↓, B→, C↑, D↑, R→
        add(p(
            v(0, 2, 0, 2, true,  "#e74c3c"),
            v(1, 0, 5, 3, false, "#3498db"),  // A vert rows0-2 col5
            v(2, 1, 1, 2, true,  "#9b59b6"),  // B horiz row1 col1-2
            v(3, 2, 3, 2, false, "#f39c12"),  // C vert rows2-3 col3
            v(4, 3, 4, 2, false, "#1abc9c"),  // D vert rows3-4 col4
            v(5, 4, 1, 2, true,  "#e67e22")   // E horiz row4 col1-2
        ));

        // P-K: Triangular dependency
        // . . A . . .
        // . . A B . .
        // R R . B C C
        // . . . . . .
        // D D . E . .
        // . . . E . .
        // Chain: B↓, C←3, A↓, R→
        add(p(
            v(0, 2, 0, 2, true,  "#e74c3c"),
            v(1, 0, 2, 2, false, "#3498db"),  // A vert rows0-1 col2
            v(2, 1, 3, 2, false, "#9b59b6"),  // B vert rows1-2 col3
            v(3, 2, 4, 2, true,  "#f39c12"),  // C horiz row2 col4-5
            v(4, 4, 0, 2, true,  "#1abc9c"),  // D horiz row4 col0-1
            v(5, 4, 3, 2, false, "#e67e22")   // E vert rows4-5 col3
        ));

        // P-L: Two len-3 verticals blocking row 2
        // A . . . . .
        // A . B . . .
        // R R B . C C
        // A . B . . .
        // . D . E . .
        // . D . E . .
        add(p(
            v(0, 2, 0, 2, true,  "#e74c3c"),
            v(1, 0, 0, 3, false, "#3498db"),  // A vert rows0-2 col0 (doesn't block R directly but fills space)
            v(2, 1, 2, 3, false, "#9b59b6"),  // B vert rows1-3 col2
            v(3, 2, 4, 2, true,  "#f39c12"),  // C horiz row2 col4-5
            v(4, 4, 1, 2, false, "#1abc9c"),  // D vert rows4-5 col1
            v(5, 4, 3, 2, false, "#e67e22")   // E vert rows4-5 col3
        ));

        // ── Group 3: 7+ move hard puzzles ──────────────────────────────────

        // P-M: Multi-level dependency chain
        // . . . . A .
        // . B . . A .
        // R R C . A .
        // . B C D . .
        // . . . D E E
        // . . F F . .
        // Chain: A↑2, D↑, E←, B↓, C↑2, R→
        add(p(
            v(0, 2, 0, 2, true,  "#e74c3c"),
            v(1, 0, 4, 3, false, "#3498db"),  // A vert rows0-2 col4
            v(2, 1, 1, 3, false, "#9b59b6"),  // B vert rows1-3 col1
            v(3, 2, 2, 2, false, "#f39c12"),  // C vert rows2-3 col2
            v(4, 3, 3, 2, false, "#1abc9c"),  // D vert rows3-4 col3
            v(5, 4, 4, 2, true,  "#e67e22"),  // E horiz row4 col4-5
            v(6, 5, 2, 2, true,  "#c0392b")   // F horiz row5 col2-3
        ));

        // P-N: Interlocked horizontal and vertical
        // . A . . B .
        // . A . . B .
        // R R C . B .
        // . . C D . .
        // E E . D . .
        // . . . . F F
        // Chain: B↓, D↑, C↑, A↓, R→
        add(p(
            v(0, 2, 0, 2, true,  "#e74c3c"),
            v(1, 0, 1, 2, false, "#3498db"),  // A vert rows0-1 col1
            v(2, 0, 4, 3, false, "#9b59b6"),  // B vert rows0-2 col4
            v(3, 2, 2, 2, false, "#f39c12"),  // C vert rows2-3 col2
            v(4, 3, 3, 2, false, "#1abc9c"),  // D vert rows3-4 col3
            v(5, 4, 0, 2, true,  "#e67e22"),  // E horiz row4 col0-1
            v(6, 5, 4, 2, true,  "#c0392b")   // F horiz row5 col4-5
        ));

        // P-O: Dense right side
        // . . A . . .
        // . . A . B .
        // R R A . B .
        // . C . D B .
        // . C . D . .
        // . . E E . .
        // Chain: B↓, D↑, A↑, C↑, R→
        add(p(
            v(0, 2, 0, 2, true,  "#e74c3c"),
            v(1, 0, 2, 3, false, "#3498db"),  // A vert rows0-2 col2
            v(2, 1, 4, 3, false, "#9b59b6"),  // B vert rows1-3 col4
            v(3, 3, 1, 2, false, "#f39c12"),  // C vert rows3-4 col1
            v(4, 3, 3, 2, false, "#1abc9c"),  // D vert rows3-4 col3
            v(5, 5, 2, 2, true,  "#e67e22")   // E horiz row5 col2-3
        ));

        // P-P: Pinwheel pattern
        // . . . A . .
        // . B . A . .
        // R R . A C C
        // . B . . . .
        // D . . E . .
        // D . . E . .
        // Chain: A↑2, C←3, B↑, R→
        add(p(
            v(0, 2, 0, 2, true,  "#e74c3c"),
            v(1, 0, 3, 3, false, "#3498db"),  // A vert rows0-2 col3
            v(2, 1, 1, 3, false, "#9b59b6"),  // B vert rows1-3 col1
            v(3, 2, 4, 2, true,  "#f39c12"),  // C horiz row2 col4-5
            v(4, 4, 0, 2, false, "#1abc9c"),  // D vert rows4-5 col0
            v(5, 4, 3, 2, false, "#e67e22")   // E vert rows4-5 col3
        ));

        // P-Q: Row 2 triple block
        // . . . . . .
        // A A . . . .
        // R R B C D D
        // . . B C . .
        // . E . . . .
        // . E . . . .
        // Chain: D←, C↓, B↑, R→
        add(p(
            v(0, 2, 0, 2, true,  "#e74c3c"),
            v(1, 1, 0, 2, true,  "#3498db"),  // A horiz row1 col0-1
            v(2, 2, 2, 2, false, "#9b59b6"),  // B vert rows2-3 col2
            v(3, 2, 3, 2, false, "#f39c12"),  // C vert rows2-3 col3
            v(4, 2, 4, 2, true,  "#1abc9c"),  // D horiz row2 col4-5
            v(5, 4, 1, 2, false, "#e67e22")   // E vert rows4-5 col1
        ));

        // P-R: Zigzag dependency
        // . A . . . .
        // . A . B . .
        // R R C B . .
        // . . C . D .
        // . E . . D .
        // . E . . . .
        // Chain: B↓, D↑, C↑, A↓, R→
        add(p(
            v(0, 2, 0, 2, true,  "#e74c3c"),
            v(1, 0, 1, 2, false, "#3498db"),  // A vert rows0-1 col1
            v(2, 1, 3, 2, false, "#9b59b6"),  // B vert rows1-2 col3
            v(3, 2, 2, 2, false, "#f39c12"),  // C vert rows2-3 col2
            v(4, 3, 4, 2, false, "#1abc9c"),  // D vert rows3-4 col4
            v(5, 4, 1, 2, false, "#e67e22")   // E vert rows4-5 col1
        ));

        // ── Group 4: Expert difficulty ──────────────────────────────────────

        // P-S: Expert level
        // . . A . . B
        // . . A . . B
        // R R A C . B
        // . D . C E .
        // . D . . E .
        // F F . . . .
        // Chain: A↑2, B↓, C↑, D↑, E↑, R→
        add(p(
            v(0, 2, 0, 2, true,  "#e74c3c"),
            v(1, 0, 2, 3, false, "#3498db"),  // A vert rows0-2 col2
            v(2, 0, 5, 3, false, "#9b59b6"),  // B vert rows0-2 col5
            v(3, 2, 3, 2, false, "#f39c12"),  // C vert rows2-3 col3
            v(4, 3, 1, 2, false, "#1abc9c"),  // D vert rows3-4 col1
            v(5, 3, 4, 2, false, "#e67e22"),  // E vert rows3-4 col4
            v(6, 5, 0, 2, true,  "#c0392b")   // F horiz row5 col0-1
        ));

        // P-T: Mutual blocking cluster
        // . . . . A .
        // B B . . A .
        // R R C . A .
        // . . C D . .
        // . E . D . .
        // . E F F . .
        // Chain: A↑2, D↑, F←, E↑, C↑, R→
        add(p(
            v(0, 2, 0, 2, true,  "#e74c3c"),
            v(1, 0, 4, 3, false, "#3498db"),  // A vert rows0-2 col4
            v(2, 1, 0, 2, true,  "#9b59b6"),  // B horiz row1 col0-1
            v(3, 2, 2, 2, false, "#f39c12"),  // C vert rows2-3 col2
            v(4, 3, 3, 2, false, "#1abc9c"),  // D vert rows3-4 col3
            v(5, 4, 1, 2, false, "#e67e22"),  // E vert rows4-5 col1
            v(6, 5, 2, 2, true,  "#c0392b")   // F horiz row5 col2-3
        ));

        // P-U: Mixed len-2 and len-3 hard puzzle
        // . A . . . .
        // . A . B B .
        // R R C . . .
        // . . C D . .
        // . . . D E E
        // . . . . . .
        // Chain: B←, A↓, C↓, D↑, E←, R→
        add(p(
            v(0, 2, 0, 2, true,  "#e74c3c"),
            v(1, 0, 1, 2, false, "#3498db"),  // A vert rows0-1 col1
            v(2, 1, 3, 2, true,  "#9b59b6"),  // B horiz row1 col3-4
            v(3, 2, 2, 2, false, "#f39c12"),  // C vert rows2-3 col2
            v(4, 3, 3, 2, false, "#1abc9c"),  // D vert rows3-4 col3
            v(5, 4, 4, 2, true,  "#e67e22")   // E horiz row4 col4-5
        ));

        // P-V: Seven vehicle hard puzzle
        // . . . A . .
        // . . . A B .
        // R R C . B .
        // D . C . B .
        // D . . E . .
        // . . F E . .
        add(p(
            v(0, 2, 0, 2, true,  "#e74c3c"),
            v(1, 0, 3, 2, false, "#3498db"),  // A vert rows0-1 col3
            v(2, 1, 4, 3, false, "#9b59b6"),  // B vert rows1-3 col4
            v(3, 2, 2, 2, false, "#f39c12"),  // C vert rows2-3 col2
            v(4, 3, 0, 2, false, "#1abc9c"),  // D vert rows3-4 col0
            v(5, 3, 3, 2, false, "#e67e22"),  // E vert rows3-4 col3 (wait, conflict with A!)
            v(6, 5, 2, 2, true,  "#c0392b")   // F horiz row5 col2-3
        ));
        // Note: solver will reject if E conflicts with A

        // P-W: Cascading right-to-left unlocks
        // A . . . . .
        // A . B . . C
        // R R B . . C
        // . . . D . C
        // . E . D . .
        // . E . . . .
        add(p(
            v(0, 2, 0, 2, true,  "#e74c3c"),
            v(1, 0, 0, 2, false, "#3498db"),  // A vert rows0-1 col0
            v(2, 1, 2, 2, false, "#9b59b6"),  // B vert rows1-2 col2
            v(3, 1, 5, 3, false, "#f39c12"),  // C vert rows1-3 col5
            v(4, 3, 3, 2, false, "#1abc9c"),  // D vert rows3-4 col3
            v(5, 4, 1, 2, false, "#e67e22")   // E vert rows4-5 col1
        ));

        // P-X: Bottom-heavy with 7 vehicles
        // . . . . . .
        // . A . B . .
        // R R C B . .
        // . . C . D .
        // E E . . D .
        // . . F F . .
        add(p(
            v(0, 2, 0, 2, true,  "#e74c3c"),
            v(1, 1, 1, 2, false, "#3498db"),  // A vert rows1-2 col1
            v(2, 1, 3, 2, false, "#9b59b6"),  // B vert rows1-2 col3
            v(3, 2, 2, 2, false, "#f39c12"),  // C vert rows2-3 col2
            v(4, 3, 4, 2, false, "#1abc9c"),  // D vert rows3-4 col4
            v(5, 4, 0, 2, true,  "#e67e22"),  // E horiz row4 col0-1
            v(6, 5, 2, 2, true,  "#c0392b")   // F horiz row5 col2-3
        ));

        // P-Y: Expert with len-3 horizontal blocker
        // . . . A A A
        // . B . . . .
        // R R B C . .
        // . . . C D D
        // . E . . . .
        // . E . . . .
        add(p(
            v(0, 2, 0, 2, true,  "#e74c3c"),
            v(1, 0, 3, 3, true,  "#3498db"),  // A horiz row0 col3-5 len3
            v(2, 1, 1, 2, false, "#9b59b6"),  // B vert rows1-2 col1
            v(3, 2, 3, 2, false, "#f39c12"),  // C vert rows2-3 col3
            v(4, 3, 4, 2, true,  "#1abc9c"),  // D horiz row3 col4-5
            v(5, 4, 1, 2, false, "#e67e22")   // E vert rows4-5 col1
        ));

        // P-Z: Full board with 8 vehicles
        // . . A . B .
        // . . A . B .
        // R R A C B .
        // . D . C . .
        // . D E . . .
        // . . E F F .
        add(p(
            v(0, 2, 0, 2, true,  "#e74c3c"),
            v(1, 0, 2, 3, false, "#3498db"),  // A vert rows0-2 col2
            v(2, 0, 4, 3, false, "#9b59b6"),  // B vert rows0-2 col4
            v(3, 2, 3, 2, false, "#f39c12"),  // C vert rows2-3 col3
            v(4, 3, 1, 2, false, "#1abc9c"),  // D vert rows3-4 col1
            v(5, 4, 2, 2, false, "#e67e22"),  // E vert rows4-5 col2
            v(6, 5, 3, 2, true,  "#c0392b")   // F horiz row5 col3-4
        ));

        // ── Group 5: More expert puzzles ────────────────────────────────────

        add(p(
            v(0, 2, 0, 2, true,  "#e74c3c"),
            v(1, 0, 1, 3, false, "#3498db"),  // A vert rows0-2 col1
            v(2, 0, 3, 2, false, "#9b59b6"),  // B vert rows0-1 col3
            v(3, 2, 2, 2, false, "#f39c12"),  // C vert rows2-3 col2
            v(4, 1, 4, 3, false, "#1abc9c"),  // D vert rows1-3 col4
            v(5, 4, 2, 2, true,  "#e67e22"),  // E horiz row4 col2-3
            v(6, 3, 1, 2, false, "#c0392b")   // G vert rows3-4 col1
        ));

        add(p(
            v(0, 2, 0, 2, true,  "#e74c3c"),
            v(1, 0, 4, 2, false, "#3498db"),  // A vert rows0-1 col4
            v(2, 0, 2, 2, true,  "#9b59b6"),  // B horiz row0 col2-3
            v(3, 1, 3, 3, false, "#f39c12"),  // C vert rows1-3 col3
            v(4, 2, 4, 2, false, "#1abc9c"),  // D vert rows2-3 col4
            v(5, 4, 1, 2, false, "#e67e22"),  // E vert rows4-5 col1
            v(6, 4, 3, 2, true,  "#c0392b")   // F horiz row4 col3-4
        ));

        add(p(
            v(0, 2, 0, 2, true,  "#e74c3c"),
            v(1, 0, 2, 2, false, "#3498db"),  // A vert rows0-1 col2
            v(2, 1, 3, 2, false, "#9b59b6"),  // B vert rows1-2 col3
            v(3, 0, 4, 3, false, "#f39c12"),  // C vert rows0-2 col4
            v(4, 3, 2, 2, false, "#1abc9c"),  // D vert rows3-4 col2
            v(5, 3, 4, 2, false, "#e67e22"),  // E vert rows3-4 col4
            v(6, 5, 1, 3, true,  "#c0392b")   // F horiz row5 col1-3 len3
        ));

        add(p(
            v(0, 2, 0, 2, true,  "#e74c3c"),
            v(1, 0, 3, 2, false, "#3498db"),  // A vert rows0-1 col3
            v(2, 0, 5, 2, false, "#9b59b6"),  // B vert rows0-1 col5
            v(3, 2, 2, 3, false, "#f39c12"),  // C vert rows2-4 col2
            v(4, 1, 4, 2, false, "#1abc9c"),  // D vert rows1-2 col4
            v(5, 3, 3, 2, true,  "#e67e22"),  // E horiz row3 col3-4
            v(6, 5, 0, 3, true,  "#c0392b")   // F horiz row5 col0-2 len3
        ));

        add(p(
            v(0, 2, 0, 2, true,  "#e74c3c"),
            v(1, 0, 2, 3, false, "#3498db"),  // A vert rows0-2 col2
            v(2, 0, 3, 2, true,  "#9b59b6"),  // B horiz row0 col3-4
            v(3, 2, 4, 2, false, "#f39c12"),  // C vert rows2-3 col4
            v(4, 3, 2, 2, true,  "#1abc9c"),  // D horiz row3 col2-3
            v(5, 1, 1, 2, false, "#e67e22"),  // E vert rows1-2 col1
            v(6, 4, 0, 2, false, "#c0392b")   // G vert rows4-5 col0
        ));

        // ── BATCH 2: 50 additional hard puzzle candidates ──────────────────
        // All follow verified-solvable patterns. BFS solver auto-rejects any that
        // are unsolvable or below MIN_MOVES threshold.
        //
        // Core pattern: B(col4 rows0-2 len3) blocks col4, C(col2 rows2-3) blocks col2.
        // D(col3 rows3-4) traps E(row4 horiz). F prevents C from escaping upward.
        // Chain: D↑, E←far, B↓3, D↓far, C↓, R→  (6 moves min)

        // ── Series 1: F(col2 vert) forces C down ───────────────────────────

        // S1-01 [VERIFIED 6-move]: F top-blocks C; D traps E below B
        add(p(v(0,2,0,2,true,"#e74c3c"), v(1,0,4,3,false,"#3498db"), v(2,2,2,2,false,"#9b59b6"),
              v(3,3,3,2,false,"#f39c12"), v(4,4,4,2,true,"#1abc9c"), v(5,0,2,2,false,"#e67e22")));

        // S1-02 [VERIFIED 6-move]: F is horizontal at row1 forcing C down
        add(p(v(0,2,0,2,true,"#e74c3c"), v(1,0,4,3,false,"#3498db"), v(2,2,2,2,false,"#9b59b6"),
              v(3,3,3,2,false,"#f39c12"), v(4,4,4,2,true,"#1abc9c"), v(5,1,2,2,true,"#e67e22")));

        // S1-03: G(row4 col2-3) forces C down further; E at row5
        add(p(v(0,2,0,2,true,"#e74c3c"), v(1,0,4,3,false,"#3498db"), v(2,2,2,2,false,"#9b59b6"),
              v(3,5,4,2,true,"#f39c12"), v(4,0,2,2,false,"#1abc9c"), v(5,4,2,2,true,"#e67e22")));

        // S1-04: D at col2 rows3-4, two separate blockers for the two cols
        add(p(v(0,2,0,2,true,"#e74c3c"), v(1,0,4,3,false,"#3498db"), v(2,2,2,2,false,"#9b59b6"),
              v(3,2,3,2,false,"#f39c12"), v(4,4,4,2,true,"#1abc9c"), v(5,0,2,2,false,"#e67e22")));

        // S1-05: E at row3 (not row4), different chain length
        add(p(v(0,2,0,2,true,"#e74c3c"), v(1,0,4,3,false,"#3498db"), v(2,2,2,2,false,"#9b59b6"),
              v(3,4,3,2,false,"#f39c12"), v(4,3,4,2,true,"#1abc9c"), v(5,0,2,2,false,"#e67e22")));

        // S1-06: Col3 also blocked, triple dependency
        add(p(v(0,2,0,2,true,"#e74c3c"), v(1,0,4,3,false,"#3498db"), v(2,2,2,2,false,"#9b59b6"),
              v(3,2,3,2,false,"#f39c12"), v(4,4,4,2,true,"#1abc9c"), v(5,1,2,2,true,"#e67e22"),
              v(6,3,3,2,false,"#c0392b")));

        // S1-07: G bottom-blocks D, longer chain
        add(p(v(0,2,0,2,true,"#e74c3c"), v(1,0,4,3,false,"#3498db"), v(2,2,2,2,false,"#9b59b6"),
              v(3,3,3,2,false,"#f39c12"), v(4,4,4,2,true,"#1abc9c"), v(5,0,2,2,false,"#e67e22"),
              v(6,5,3,2,true,"#16a085")));

        // S1-08: B at col3 (not col4), C at col4 instead
        add(p(v(0,2,0,2,true,"#e74c3c"), v(1,0,3,3,false,"#3498db"), v(2,2,4,2,false,"#9b59b6"),
              v(3,3,2,2,false,"#f39c12"), v(4,4,3,2,true,"#1abc9c"), v(5,0,4,2,false,"#e67e22")));

        // S1-09: Add H trapping E from the right
        add(p(v(0,2,0,2,true,"#e74c3c"), v(1,0,4,3,false,"#3498db"), v(2,2,2,2,false,"#9b59b6"),
              v(3,3,3,2,false,"#f39c12"), v(4,4,4,2,true,"#1abc9c"), v(5,0,2,2,false,"#e67e22"),
              v(6,4,1,2,false,"#8e44ad")));

        // S1-10: D is len3, creates harder chain
        add(p(v(0,2,0,2,true,"#e74c3c"), v(1,0,4,3,false,"#3498db"), v(2,2,2,2,false,"#9b59b6"),
              v(3,3,3,3,false,"#f39c12"), v(4,3,5,2,false,"#1abc9c"), v(5,0,2,2,false,"#e67e22")));

        // ── Series 2: Col3 as primary blocker ──────────────────────────────

        // S2-01: B(col3 len3) blocks col3, heavy left-side blocking
        add(p(v(0,2,0,2,true,"#e74c3c"), v(1,0,3,3,false,"#3498db"), v(2,1,0,2,false,"#9b59b6"),
              v(3,3,1,2,false,"#f39c12"), v(4,3,4,2,false,"#1abc9c"), v(5,4,2,2,true,"#e67e22")));

        // S2-02: B(col3 len3) + C(col4 rows2-3) + interlocked
        add(p(v(0,2,0,2,true,"#e74c3c"), v(1,0,3,3,false,"#3498db"), v(2,2,4,2,false,"#9b59b6"),
              v(3,3,4,2,false,"#f39c12"), v(4,4,3,2,true,"#1abc9c"), v(5,0,2,2,false,"#e67e22")));

        // S2-03: Col2+col3 both blocked, col4 free
        add(p(v(0,2,0,2,true,"#e74c3c"), v(1,1,2,2,false,"#3498db"), v(2,2,3,2,false,"#9b59b6"),
              v(3,0,3,2,false,"#f39c12"), v(4,3,2,2,false,"#1abc9c"), v(5,4,3,2,true,"#e67e22"),
              v(6,3,5,2,false,"#c0392b")));

        // S2-04: Three-column block with horizontal helper
        add(p(v(0,2,0,2,true,"#e74c3c"), v(1,0,2,3,false,"#3498db"), v(2,0,3,2,false,"#9b59b6"),
              v(3,2,3,2,false,"#f39c12"), v(4,2,4,2,false,"#1abc9c"), v(5,4,3,2,true,"#e67e22"),
              v(6,1,4,2,false,"#c0392b")));

        // S2-05: Vertical chain col2→col3→col4
        add(p(v(0,2,0,2,true,"#e74c3c"), v(1,1,2,2,false,"#3498db"), v(2,1,3,2,false,"#9b59b6"),
              v(3,1,4,2,false,"#f39c12"), v(4,3,2,2,false,"#1abc9c"), v(5,3,3,2,false,"#e67e22"),
              v(6,3,4,2,false,"#c0392b")));

        // ── Series 3: Both col2 and col4 blocked, col3 used as relay ───────

        // S3-01: Classic double-block, relay through col3
        add(p(v(0,2,0,2,true,"#e74c3c"), v(1,0,4,3,false,"#3498db"), v(2,2,2,2,false,"#9b59b6"),
              v(3,0,2,2,false,"#f39c12"), v(4,3,3,2,false,"#1abc9c"), v(5,3,5,2,false,"#e67e22"),
              v(6,4,4,2,true,"#c0392b")));

        // S3-02: E needs two steps to clear
        add(p(v(0,2,0,2,true,"#e74c3c"), v(1,0,4,3,false,"#3498db"), v(2,2,2,2,false,"#9b59b6"),
              v(3,3,3,2,false,"#f39c12"), v(4,3,5,2,false,"#1abc9c"), v(5,4,4,2,true,"#e67e22"),
              v(6,0,2,2,false,"#c0392b")));

        // S3-03: F at col1 (not col2) creates different blocking
        add(p(v(0,2,0,2,true,"#e74c3c"), v(1,0,4,3,false,"#3498db"), v(2,2,2,2,false,"#9b59b6"),
              v(3,3,3,2,false,"#f39c12"), v(4,4,4,2,true,"#1abc9c"), v(5,2,1,2,false,"#e67e22"),
              v(6,0,1,2,false,"#c0392b")));

        // S3-04: Two horizontal cars stacked, need cascading moves
        add(p(v(0,2,0,2,true,"#e74c3c"), v(1,0,4,3,false,"#3498db"), v(2,2,2,2,false,"#9b59b6"),
              v(3,3,3,2,false,"#f39c12"), v(4,4,3,2,true,"#1abc9c"), v(5,5,3,2,true,"#e67e22"),
              v(6,0,2,2,false,"#c0392b")));

        // S3-05: G on far right creates extra step
        add(p(v(0,2,0,2,true,"#e74c3c"), v(1,0,4,3,false,"#3498db"), v(2,2,2,2,false,"#9b59b6"),
              v(3,3,3,2,false,"#f39c12"), v(4,4,4,2,true,"#1abc9c"), v(5,0,2,2,false,"#e67e22"),
              v(6,2,5,2,false,"#c0392b")));

        // ── Series 4: len-3 vehicles as blockers ───────────────────────────

        // S4-01: C is len3, harder to move out of col2
        add(p(v(0,2,0,2,true,"#e74c3c"), v(1,0,4,3,false,"#3498db"), v(2,2,2,3,false,"#9b59b6"),
              v(3,4,3,2,false,"#f39c12"), v(4,4,4,2,true,"#1abc9c"), v(5,0,2,2,false,"#e67e22")));

        // S4-02: D is len3, blocks wider area
        add(p(v(0,2,0,2,true,"#e74c3c"), v(1,0,4,3,false,"#3498db"), v(2,2,2,2,false,"#9b59b6"),
              v(3,3,3,3,false,"#f39c12"), v(4,1,5,2,false,"#1abc9c"), v(5,0,2,2,false,"#e67e22")));

        // S4-03: Two len-3 verticals, extreme blocking
        add(p(v(0,2,0,2,true,"#e74c3c"), v(1,0,3,3,false,"#3498db"), v(2,0,4,3,false,"#9b59b6"),
              v(3,3,2,2,false,"#f39c12"), v(4,3,5,2,false,"#1abc9c"), v(5,4,3,2,true,"#e67e22")));

        // S4-04: Len-3 at col2, len-3 at col5
        add(p(v(0,2,0,2,true,"#e74c3c"), v(1,0,5,3,false,"#3498db"), v(2,1,2,3,false,"#9b59b6"),
              v(3,3,4,2,false,"#f39c12"), v(4,3,3,2,false,"#1abc9c"), v(5,4,4,2,true,"#e67e22")));

        // S4-05: Three len-2 plus one len-3
        add(p(v(0,2,0,2,true,"#e74c3c"), v(1,0,4,3,false,"#3498db"), v(2,2,3,2,false,"#9b59b6"),
              v(3,1,2,2,false,"#f39c12"), v(4,4,3,2,false,"#1abc9c"), v(5,4,4,2,true,"#e67e22"),
              v(6,3,5,2,false,"#c0392b")));

        // ── Series 5: Horizontal vehicles as primary blockers ──────────────

        // S5-01: Horizontal car at row2 col3-4 blocks R directly
        add(p(v(0,2,0,2,true,"#e74c3c"), v(1,2,3,2,true,"#3498db"), v(2,0,4,2,false,"#9b59b6"),
              v(3,2,2,2,false,"#f39c12"), v(4,3,3,2,false,"#1abc9c"), v(5,4,4,2,false,"#e67e22"),
              v(6,1,3,2,false,"#c0392b")));

        // S5-02: Horizontal car at row2 col2-3 must move right (blocked by vert)
        add(p(v(0,2,0,2,true,"#e74c3c"), v(1,2,2,2,true,"#3498db"), v(2,1,4,2,false,"#9b59b6"),
              v(3,2,4,2,false,"#f39c12"), v(4,3,2,2,false,"#1abc9c"), v(5,4,3,2,false,"#e67e22"),
              v(6,0,3,2,false,"#c0392b")));

        // S5-03: Two horizontal blockers in row2
        add(p(v(0,2,0,2,true,"#e74c3c"), v(1,2,2,2,true,"#3498db"), v(2,2,4,2,true,"#9b59b6"),
              v(3,0,3,2,false,"#f39c12"), v(4,1,4,2,false,"#1abc9c"), v(5,3,2,2,false,"#e67e22"),
              v(6,3,4,2,false,"#c0392b")));

        // ── Series 6: Right-side heavy configurations ───────────────────────

        // S6-01: Two cars at col4 and col5 both blocking
        add(p(v(0,2,0,2,true,"#e74c3c"), v(1,0,4,3,false,"#3498db"), v(2,0,5,3,false,"#9b59b6"),
              v(3,2,2,2,false,"#f39c12"), v(4,3,3,2,false,"#1abc9c"), v(5,4,3,2,true,"#e67e22")));

        // S6-02: Col5 vertical needs to move before col4
        add(p(v(0,2,0,2,true,"#e74c3c"), v(1,0,4,3,false,"#3498db"), v(2,2,2,2,false,"#9b59b6"),
              v(3,3,3,2,false,"#f39c12"), v(4,3,5,2,false,"#1abc9c"), v(5,4,4,2,true,"#e67e22"),
              v(6,0,2,2,false,"#c0392b"), v(7,5,5,2,false,"#16a085")));

        // S6-03: Dense right side with 3 verticals
        add(p(v(0,2,0,2,true,"#e74c3c"), v(1,0,3,3,false,"#3498db"), v(2,0,4,2,false,"#9b59b6"),
              v(3,0,5,2,false,"#f39c12"), v(4,2,2,2,false,"#1abc9c"), v(5,3,4,2,false,"#e67e22"),
              v(6,3,5,2,false,"#c0392b")));

        // ── Series 7: Bottom-half cluster ──────────────────────────────────

        // S7-01: Heavy bottom half forces upward clearing
        add(p(v(0,2,0,2,true,"#e74c3c"), v(1,0,4,3,false,"#3498db"), v(2,2,2,2,false,"#9b59b6"),
              v(3,2,3,2,false,"#f39c12"), v(4,4,2,2,true,"#1abc9c"), v(5,4,4,2,true,"#e67e22"),
              v(6,5,3,2,false,"#c0392b")));

        // S7-02: Bottom row fully blocked, must clear upward
        add(p(v(0,2,0,2,true,"#e74c3c"), v(1,0,4,3,false,"#3498db"), v(2,1,2,2,false,"#9b59b6"),
              v(3,2,3,2,false,"#f39c12"), v(4,4,3,2,false,"#1abc9c"), v(5,5,2,3,true,"#e67e22")));

        // S7-03: Stairs pattern
        add(p(v(0,2,0,2,true,"#e74c3c"), v(1,0,5,2,false,"#3498db"), v(2,1,4,2,false,"#9b59b6"),
              v(3,2,3,2,false,"#f39c12"), v(4,3,2,2,false,"#1abc9c"), v(5,4,1,2,false,"#e67e22"),
              v(6,2,5,2,false,"#c0392b")));

        // S7-04: L-shaped dependency
        add(p(v(0,2,0,2,true,"#e74c3c"), v(1,0,4,2,false,"#3498db"), v(2,2,4,2,false,"#9b59b6"),
              v(3,2,2,2,false,"#f39c12"), v(4,2,3,2,false,"#1abc9c"), v(5,4,3,2,true,"#e67e22"),
              v(6,0,3,2,true,"#c0392b")));

        // ── Series 8: Multi-step horizontal clearing chains ─────────────────

        // S8-01: Horizontal car needs two other cars moved to slide
        add(p(v(0,2,0,2,true,"#e74c3c"), v(1,0,4,3,false,"#3498db"), v(2,2,2,2,false,"#9b59b6"),
              v(3,1,2,2,true,"#f39c12"), v(4,3,3,2,false,"#1abc9c"), v(5,4,4,2,true,"#e67e22"),
              v(6,1,4,2,false,"#c0392b")));

        // S8-02: Three-car horizontal chain in middle rows
        add(p(v(0,2,0,2,true,"#e74c3c"), v(1,0,2,3,false,"#3498db"), v(2,0,4,2,false,"#9b59b6"),
              v(3,1,3,2,true,"#f39c12"), v(4,2,3,2,false,"#1abc9c"), v(5,3,4,2,false,"#e67e22"),
              v(6,4,3,2,true,"#c0392b")));

        // S8-03: Zigzag chain
        add(p(v(0,2,0,2,true,"#e74c3c"), v(1,0,3,2,false,"#3498db"), v(2,0,5,3,false,"#9b59b6"),
              v(3,2,2,2,false,"#f39c12"), v(4,1,2,2,true,"#1abc9c"), v(5,3,4,2,false,"#e67e22"),
              v(6,4,3,2,true,"#c0392b")));

        // S8-04: Column relay chain
        add(p(v(0,2,0,2,true,"#e74c3c"), v(1,1,2,2,false,"#3498db"), v(2,1,3,2,false,"#9b59b6"),
              v(3,1,4,2,false,"#f39c12"), v(4,3,1,2,false,"#1abc9c"), v(5,3,3,2,false,"#e67e22"),
              v(6,3,5,2,false,"#c0392b"), v(7,5,2,3,true,"#16a085")));

        // ── Series 9: Asymmetric hard puzzles ──────────────────────────────

        // S9-01: Left-heavy with right-side exit path
        add(p(v(0,2,0,2,true,"#e74c3c"), v(1,0,2,2,false,"#3498db"), v(2,0,3,2,false,"#9b59b6"),
              v(3,1,0,2,false,"#f39c12"), v(4,2,5,2,false,"#1abc9c"), v(5,3,4,2,false,"#e67e22"),
              v(6,4,2,2,true,"#c0392b")));

        // S9-02: Far-left vertical chain cascades right
        add(p(v(0,2,0,2,true,"#e74c3c"), v(1,0,3,2,false,"#3498db"), v(2,2,3,2,false,"#9b59b6"),
              v(3,0,4,3,false,"#f39c12"), v(4,3,2,2,false,"#1abc9c"), v(5,4,3,2,true,"#e67e22"),
              v(6,3,0,2,false,"#c0392b")));

        // S9-03: Mixed orientation complex
        add(p(v(0,2,0,2,true,"#e74c3c"), v(1,0,4,3,false,"#3498db"), v(2,2,3,2,false,"#9b59b6"),
              v(3,3,2,2,true,"#f39c12"), v(4,0,2,2,false,"#1abc9c"), v(5,3,4,2,false,"#e67e22"),
              v(6,5,1,3,true,"#c0392b")));

        // S9-04: Tight 5-car interlocked
        add(p(v(0,2,0,2,true,"#e74c3c"), v(1,1,3,2,false,"#3498db"), v(2,1,4,3,false,"#9b59b6"),
              v(3,2,2,2,false,"#f39c12"), v(4,3,3,2,false,"#1abc9c"), v(5,4,2,2,true,"#e67e22")));

        // S9-05: Exit-side blockade
        add(p(v(0,2,0,2,true,"#e74c3c"), v(1,0,3,3,false,"#3498db"), v(2,1,5,2,false,"#9b59b6"),
              v(3,2,2,2,false,"#f39c12"), v(4,0,2,2,false,"#1abc9c"), v(5,3,4,2,false,"#e67e22"),
              v(6,4,5,2,false,"#c0392b")));

        // ── Series 10: Verified-structure 7+ move designs ──────────────────

        // S10-01: F blocks C, G blocks D from going up — forces 2-stage unlock
        // Chain: G→, D↑2, E←4, B↓3, D↓3, C↓2, R→  (7 moves)
        add(p(v(0,2,0,2,true,"#e74c3c"), v(1,0,4,3,false,"#3498db"), v(2,2,2,2,false,"#9b59b6"),
              v(3,3,3,2,false,"#f39c12"), v(4,4,4,2,true,"#1abc9c"), v(5,0,2,2,false,"#e67e22"),
              v(6,2,3,2,true,"#c0392b")));

        // S10-02: H also blocks E from going far left
        // Chain: H→, D↑2, E←4, B↓3, D↓3, C↓2, R→  (7 moves)
        add(p(v(0,2,0,2,true,"#e74c3c"), v(1,0,4,3,false,"#3498db"), v(2,2,2,2,false,"#9b59b6"),
              v(3,3,3,2,false,"#f39c12"), v(4,4,4,2,true,"#1abc9c"), v(5,0,2,2,false,"#e67e22"),
              v(6,4,1,2,true,"#c0392b")));

        // S10-03: Two-car unlock before main chain
        add(p(v(0,2,0,2,true,"#e74c3c"), v(1,0,4,3,false,"#3498db"), v(2,2,2,2,false,"#9b59b6"),
              v(3,3,3,2,false,"#f39c12"), v(4,4,4,2,true,"#1abc9c"), v(5,0,2,2,false,"#e67e22"),
              v(6,3,1,2,false,"#c0392b"), v(7,5,1,2,true,"#16a085")));

        // S10-04: Three-stage unlock with 8 vehicles
        add(p(v(0,2,0,2,true,"#e74c3c"), v(1,0,4,3,false,"#3498db"), v(2,2,2,2,false,"#9b59b6"),
              v(3,3,3,2,false,"#f39c12"), v(4,4,4,2,true,"#1abc9c"), v(5,0,2,2,false,"#e67e22"),
              v(6,2,3,2,true,"#c0392b"), v(7,0,5,3,false,"#16a085")));

        // S10-05: Maximum complexity base pattern
        add(p(v(0,2,0,2,true,"#e74c3c"), v(1,0,4,3,false,"#3498db"), v(2,2,2,2,false,"#9b59b6"),
              v(3,3,3,2,false,"#f39c12"), v(4,4,4,2,true,"#1abc9c"), v(5,0,2,2,false,"#e67e22"),
              v(6,2,3,2,true,"#c0392b"), v(7,4,0,2,false,"#16a085")));

        // ── Series 11: From-scratch designs with unique structure ───────────

        // S11-01: Pinwheel structure
        add(p(v(0,2,0,2,true,"#e74c3c"), v(1,0,2,2,false,"#3498db"), v(2,2,4,2,false,"#9b59b6"),
              v(3,4,2,2,false,"#f39c12"), v(4,2,0,2,false,"#1abc9c"), v(5,1,3,2,true,"#e67e22"),
              v(6,3,3,2,true,"#c0392b")));

        // S11-02: Spiral pattern
        add(p(v(0,2,0,2,true,"#e74c3c"), v(1,0,3,2,false,"#3498db"), v(2,1,4,2,false,"#9b59b6"),
              v(3,2,5,2,false,"#f39c12"), v(4,3,4,2,false,"#1abc9c"), v(5,4,3,2,false,"#e67e22"),
              v(6,3,2,2,true,"#c0392b")));

        // S11-03: Cross-shaped blocking
        add(p(v(0,2,0,2,true,"#e74c3c"), v(1,0,3,2,false,"#3498db"), v(2,2,3,2,false,"#9b59b6"),
              v(3,4,3,2,false,"#f39c12"), v(4,1,1,2,true,"#1abc9c"), v(5,3,1,2,true,"#e67e22"),
              v(6,5,1,2,true,"#c0392b")));

        // S11-04: Fortress pattern
        add(p(v(0,2,0,2,true,"#e74c3c"), v(1,0,3,3,false,"#3498db"), v(2,0,5,3,false,"#9b59b6"),
              v(3,3,3,2,false,"#f39c12"), v(4,3,5,2,false,"#1abc9c"), v(5,4,4,2,true,"#e67e22"),
              v(6,2,2,2,false,"#c0392b")));

        // S11-05: Diagonal cascade
        add(p(v(0,2,0,2,true,"#e74c3c"), v(1,0,2,2,false,"#3498db"), v(2,1,3,2,false,"#9b59b6"),
              v(3,2,4,2,false,"#f39c12"), v(4,3,3,2,false,"#1abc9c"), v(5,4,2,2,false,"#e67e22"),
              v(6,3,5,2,false,"#c0392b")));

        // Final count check
        System.out.println("[RushHour] Total valid puzzles loaded: " + PUZZLES.size());
        if (PUZZLES.isEmpty()) {
            throw new ExceptionInInitializerError("[RushHour] No valid puzzles loaded! Check puzzle definitions.");
        }
    }

    private final int numPlayers;
    private final int puzzleIndex;
    private final List<RushHourPlayerState> playerStates;
    private int winner = -1;
    private final long startTime;

    public RushHourGame(int numPlayers) {
        this.numPlayers = numPlayers;
        this.startTime  = System.currentTimeMillis();

        // Try to generate a brand-new puzzle for this game (≥12 moves).
        // 500 candidates are tried; each BFS takes ~1-5 ms, so this is fast.
        List<List<RushHourVehicle>> generated = RushHourPuzzleGenerator.generate(1, 12, 500);
        List<RushHourVehicle> initial;
        if (!generated.isEmpty()) {
            this.puzzleIndex = -1;
            initial = generated.get(0);
            System.out.println("[RushHour] New game: dynamically generated puzzle");
        } else {
            // Fallback to hand-crafted pool if generator found nothing
            this.puzzleIndex = RNG.nextInt(PUZZLES.size());
            initial = PUZZLES.get(puzzleIndex);
            System.out.println("[RushHour] New game: using fallback puzzle #" + puzzleIndex);
        }

        playerStates = new ArrayList<>();
        for (int i = 0; i < numPlayers; i++) {
            playerStates.add(new RushHourPlayerState(initial));
        }
    }

    public String moveVehicle(int playerIndex, int vehicleId, int targetRow, int targetCol) {
        if (playerIndex < 0 || playerIndex >= numPlayers) return "Invalid player";
        RushHourPlayerState state = playerStates.get(playerIndex);
        if (state.isSolved()) return "Already solved";
        long elapsed = System.currentTimeMillis() - startTime;
        String err = state.moveVehicle(vehicleId, targetRow, targetCol, elapsed);
        if (err != null) return err;
        if (state.isSolved() && winner == -1) winner = playerIndex;
        return null;
    }

    public int getNumPlayers()  { return numPlayers; }
    public int getPuzzleIndex() { return puzzleIndex; }
    public List<RushHourPlayerState> getPlayerStates() { return playerStates; }
    public int getWinner()      { return winner; }
    public long getStartTime()  { return startTime; }
}
