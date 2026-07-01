package com.studyplatform.model.alkkagi;

public class AlkkagiShot {
    private int id;
    private int playerIndex;
    private int stoneId;
    private double vx;
    private double vy;

    public AlkkagiShot() {
    }

    public AlkkagiShot(int id, int playerIndex, int stoneId, double vx, double vy) {
        this.id = id;
        this.playerIndex = playerIndex;
        this.stoneId = stoneId;
        this.vx = vx;
        this.vy = vy;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getPlayerIndex() { return playerIndex; }
    public void setPlayerIndex(int playerIndex) { this.playerIndex = playerIndex; }

    public int getStoneId() { return stoneId; }
    public void setStoneId(int stoneId) { this.stoneId = stoneId; }

    public double getVx() { return vx; }
    public void setVx(double vx) { this.vx = vx; }

    public double getVy() { return vy; }
    public void setVy(double vy) { this.vy = vy; }
}
