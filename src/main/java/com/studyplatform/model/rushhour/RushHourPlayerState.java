package com.studyplatform.model.rushhour;

import java.util.ArrayList;
import java.util.List;

public class RushHourPlayerState {
    private List<RushHourVehicle> vehicles;
    private int moves;
    private boolean solved;
    private long solveTimeMs;

    public RushHourPlayerState(List<RushHourVehicle> initialVehicles) {
        this.vehicles = new ArrayList<>();
        for (RushHourVehicle v : initialVehicles) this.vehicles.add(v.copy());
        this.moves = 0;
        this.solved = false;
        this.solveTimeMs = 0;
    }

    public List<RushHourVehicle> getVehicles() { return vehicles; }
    public int getMoves() { return moves; }
    public boolean isSolved() { return solved; }
    public long getSolveTimeMs() { return solveTimeMs; }

    public void setSolved(boolean solved) { this.solved = solved; }
    public void setSolveTimeMs(long t) { this.solveTimeMs = t; }
    public void incrementMoves() { this.moves++; }

    /** Build 6x6 occupancy grid: -1=empty, else vehicle id */
    public int[][] buildGrid() {
        int[][] grid = new int[6][6];
        for (int[] row : grid) java.util.Arrays.fill(row, -1);
        for (RushHourVehicle v : vehicles) {
            for (int i = 0; i < v.getLength(); i++) {
                int r = v.isHorizontal() ? v.getRow() : v.getRow() + i;
                int c = v.isHorizontal() ? v.getCol() + i : v.getCol();
                if (r >= 0 && r < 6 && c >= 0 && c < 6) grid[r][c] = v.getId();
            }
        }
        return grid;
    }

    /** Try to move vehicle to targetRow/targetCol (top-left). Returns error or null on success. */
    public String moveVehicle(int vehicleId, int targetRow, int targetCol, long elapsedMs) {
        RushHourVehicle v = null;
        for (RushHourVehicle vh : vehicles) {
            if (vh.getId() == vehicleId) { v = vh; break; }
        }
        if (v == null) return "Vehicle not found";

        int origRow = v.getRow(), origCol = v.getCol();
        if (targetRow == origRow && targetCol == origCol) return "No movement";

        // Validate axis
        if (v.isHorizontal() && targetRow != origRow) return "Vehicle moves horizontally only";
        if (!v.isHorizontal() && targetCol != origCol) return "Vehicle moves vertically only";

        // Check bounds
        if (v.isHorizontal()) {
            if (targetCol < 0 || targetCol + v.getLength() > 6) return "Out of bounds";
        } else {
            if (targetRow < 0 || targetRow + v.getLength() > 6) return "Out of bounds";
        }

        // Check path is clear
        int[][] grid = buildGrid();
        if (v.isHorizontal()) {
            int minCol = Math.min(targetCol, origCol);
            int maxCol = Math.max(targetCol + v.getLength() - 1, origCol + v.getLength() - 1);
            for (int c = minCol; c <= maxCol; c++) {
                int g = grid[origRow][c];
                if (g != -1 && g != vehicleId) return "Path blocked";
            }
        } else {
            int minRow = Math.min(targetRow, origRow);
            int maxRow = Math.max(targetRow + v.getLength() - 1, origRow + v.getLength() - 1);
            for (int r = minRow; r <= maxRow; r++) {
                int g = grid[r][origCol];
                if (g != -1 && g != vehicleId) return "Path blocked";
            }
        }

        v.setRow(targetRow);
        v.setCol(targetCol);
        moves++;

        // Check win: red car (id=0) must reach col 4 (right end for length=2 car at row 2)
        if (vehicleId == 0 && v.isHorizontal() && v.getCol() + v.getLength() == 6) {
            solved = true;
            solveTimeMs = elapsedMs;
        }

        return null;
    }
}
