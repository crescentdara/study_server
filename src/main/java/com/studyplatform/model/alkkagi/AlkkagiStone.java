package com.studyplatform.model.alkkagi;

public class AlkkagiStone {
    private int id;
    private int owner;
    private double x;
    private double y;
    private boolean active;

    public AlkkagiStone() {
    }

    public AlkkagiStone(int id, int owner, double x, double y, boolean active) {
        this.id = id;
        this.owner = owner;
        this.x = x;
        this.y = y;
        this.active = active;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getOwner() { return owner; }
    public void setOwner(int owner) { this.owner = owner; }

    public double getX() { return x; }
    public void setX(double x) { this.x = x; }

    public double getY() { return y; }
    public void setY(double y) { this.y = y; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
