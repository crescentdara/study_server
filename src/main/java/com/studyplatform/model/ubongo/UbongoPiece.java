package com.studyplatform.model.ubongo;

import java.util.*;

/**
 * Defines all polyomino piece types used in Ubongo.
 * Each piece has a unique ID, color, cell count, and a list of all distinct
 * orientations (rotations + reflections), each represented as a sorted array
 * of [row, col] offsets normalized so the top-left is at (0, 0).
 */
public class UbongoPiece {

    public final String id;
    public final String color;
    public final int    size;          // number of cells
    public final List<int[][]> orientations;  // all unique orientations

    // ── Registry ─────────────────────────────────────────────────────────────

    private static final Map<String, UbongoPiece> REGISTRY = new LinkedHashMap<>();

    static {
        // ── Triominoes (3 cells) ──
        reg("I3", "#d4a017", c(0,0), c(0,1), c(0,2));
        reg("L3", "#a0522d", c(0,0), c(1,0), c(1,1));
        // ── Tetrominoes (4 cells) ──
        reg("I4", "#e74c3c", c(0,0), c(0,1), c(0,2), c(0,3));
        reg("O4", "#3498db", c(0,0), c(0,1), c(1,0), c(1,1));
        reg("T4", "#9b59b6", c(0,0), c(0,1), c(0,2), c(1,1));
        reg("S4", "#1abc9c", c(0,1), c(0,2), c(1,0), c(1,1));
        reg("Z4", "#f39c12", c(0,0), c(0,1), c(1,1), c(1,2));
        reg("L4", "#e67e22", c(0,0), c(0,1), c(0,2), c(1,0));
        reg("J4", "#c0392b", c(0,0), c(0,1), c(0,2), c(1,2));
        // ── Pentominoes (5 cells) ──
        reg("I5", "#16a085", c(0,0), c(0,1), c(0,2), c(0,3), c(0,4));
        reg("L5", "#8e44ad", c(0,0), c(0,1), c(0,2), c(0,3), c(1,0));
        reg("P5", "#27ae60", c(0,0), c(0,1), c(1,0), c(1,1), c(2,0));
        reg("T5", "#e91e63", c(0,0), c(0,1), c(0,2), c(1,1), c(2,1));
        reg("U5", "#00bcd4", c(0,0), c(0,2), c(1,0), c(1,1), c(1,2));
        reg("V5", "#ff5722", c(0,0), c(1,0), c(2,0), c(2,1), c(2,2));
        reg("W5", "#795548", c(0,0), c(1,0), c(1,1), c(2,1), c(2,2));
    }

    private static int[] c(int r, int col) { return new int[]{r, col}; }

    private static void reg(String id, String color, int[]... cells) {
        REGISTRY.put(id, new UbongoPiece(id, color, cells));
    }

    private UbongoPiece(String id, String color, int[][] base) {
        this.id    = id;
        this.color = color;
        this.size  = base.length;
        this.orientations = computeOrientations(base);
    }

    public static UbongoPiece get(String id)        { return REGISTRY.get(id); }
    public static Collection<UbongoPiece> all()      { return REGISTRY.values(); }
    public static List<String> allIds()              { return new ArrayList<>(REGISTRY.keySet()); }

    // ── Orientation computation ───────────────────────────────────────────────

    private static List<int[][]> computeOrientations(int[][] base) {
        List<int[][]> result = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        int[][] cur = base;
        for (int flip = 0; flip < 2; flip++) {
            for (int rot = 0; rot < 4; rot++) {
                int[][] norm = normalize(cur);
                if (seen.add(encode(norm))) result.add(norm);
                cur = rotate90(cur);
            }
            cur = flipH(normalize(cur));
        }
        return result;
    }

    static int[][] rotate90(int[][] cells) {
        int[][] r = new int[cells.length][2];
        for (int i = 0; i < cells.length; i++) {
            r[i][0] = cells[i][1];
            r[i][1] = -cells[i][0];
        }
        return r;
    }

    static int[][] flipH(int[][] cells) {
        int[][] r = new int[cells.length][2];
        for (int i = 0; i < cells.length; i++) {
            r[i][0] = cells[i][0];
            r[i][1] = -cells[i][1];
        }
        return r;
    }

    static int[][] normalize(int[][] cells) {
        int minR = Integer.MAX_VALUE, minC = Integer.MAX_VALUE;
        for (int[] cell : cells) {
            minR = Math.min(minR, cell[0]);
            minC = Math.min(minC, cell[1]);
        }
        int[][] r = new int[cells.length][2];
        for (int i = 0; i < cells.length; i++) {
            r[i][0] = cells[i][0] - minR;
            r[i][1] = cells[i][1] - minC;
        }
        Arrays.sort(r, (a, b) -> a[0] != b[0] ? a[0] - b[0] : a[1] - b[1]);
        return r;
    }

    private static String encode(int[][] cells) {
        StringBuilder sb = new StringBuilder();
        for (int[] c : cells) sb.append(c[0]).append(',').append(c[1]).append(';');
        return sb.toString();
    }
}
