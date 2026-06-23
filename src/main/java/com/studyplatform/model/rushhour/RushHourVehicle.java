package com.studyplatform.model.rushhour;

public class RushHourVehicle {
    private int id;
    private int row;
    private int col;
    private int length;
    private boolean horizontal;
    private String color;

    public RushHourVehicle() {}

    public RushHourVehicle(int id, int row, int col, int length, boolean horizontal, String color) {
        this.id = id;
        this.row = row;
        this.col = col;
        this.length = length;
        this.horizontal = horizontal;
        this.color = color;
    }

    public RushHourVehicle copy() {
        return new RushHourVehicle(id, row, col, length, horizontal, color);
    }

    public int getId() { return id; }
    public int getRow() { return row; }
    public int getCol() { return col; }
    public int getLength() { return length; }
    public boolean isHorizontal() { return horizontal; }
    public String getColor() { return color; }

    public void setRow(int row) { this.row = row; }
    public void setCol(int col) { this.col = col; }
}
