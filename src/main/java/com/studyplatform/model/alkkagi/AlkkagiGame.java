package com.studyplatform.model.alkkagi;

import java.util.ArrayList;
import java.util.List;

public class AlkkagiGame {
    private final int numPlayers;
    private final List<AlkkagiStone> stones = new ArrayList<>();
    private int currentTurn = 0;
    private int winner = -1;
    private int shotCount = 0;
    private int nextShotId = 1;
    private AlkkagiShot activeShot = null;

    public AlkkagiGame(int numPlayers) {
        this.numPlayers = Math.max(2, Math.min(2, numPlayers));
        initStones();
    }

    private void initStones() {
        double[] lanes = {0.20, 0.35, 0.50, 0.65, 0.80};
        int id = 0;
        for (double y : lanes) {
            stones.add(new AlkkagiStone(id++, 0, 0.14, y, true));
        }
        for (double y : lanes) {
            stones.add(new AlkkagiStone(id++, 1, 0.86, y, true));
        }
    }

    public String beginShot(int playerIndex, int stoneId, double vx, double vy) {
        if (winner >= 0) return "Game already finished";
        if (activeShot != null) return "Shot already resolving";
        if (playerIndex != currentTurn) return "Not your turn";
        if (stoneId < 0 || stoneId >= stones.size()) return "Invalid stone id";
        AlkkagiStone stone = stones.get(stoneId);
        if (!stone.isActive()) return "Stone is already out";
        if (stone.getOwner() != playerIndex) return "Cannot shoot opponent stone";
        double speed = Math.hypot(vx, vy);
        if (speed < 0.5) return "Shot is too weak";
        if (speed > 32) {
            double scale = 32 / speed;
            vx *= scale;
            vy *= scale;
        }
        activeShot = new AlkkagiShot(nextShotId++, playerIndex, stoneId, clampVelocity(vx), clampVelocity(vy));
        return null;
    }

    public String applyShotResult(int playerIndex, int shotId, List<AlkkagiStone> nextStones) {
        if (winner >= 0) return "Game already finished";
        if (activeShot == null) return "No shot is resolving";
        if (activeShot.getId() != shotId) return "Shot id mismatch";
        if (activeShot.getPlayerIndex() != playerIndex) return "Only shooter can confirm result";
        if (nextStones == null || nextStones.size() != stones.size()) return "Invalid stone state";

        for (AlkkagiStone stone : nextStones) {
            if (stone.getId() < 0 || stone.getId() >= stones.size()) return "Invalid stone id";
            stone.setX(clamp(stone.getX()));
            stone.setY(clamp(stone.getY()));
        }

        stones.clear();
        stones.addAll(copySorted(nextStones));
        shotCount++;
        activeShot = null;
        updateWinner();
        if (winner < 0) {
            currentTurn = (currentTurn + 1) % numPlayers;
        }
        return null;
    }

    public void clearActiveShot() {
        activeShot = null;
    }

    private List<AlkkagiStone> copySorted(List<AlkkagiStone> source) {
        List<AlkkagiStone> copy = new ArrayList<>();
        source.stream()
                .sorted((a, b) -> Integer.compare(a.getId(), b.getId()))
                .forEach(s -> copy.add(new AlkkagiStone(s.getId(), s.getOwner(), s.getX(), s.getY(), s.isActive())));
        return copy;
    }

    private void updateWinner() {
        int p0 = activeCount(0);
        int p1 = activeCount(1);
        if (p0 == 0 && p1 == 0) winner = currentTurn;
        else if (p0 == 0) winner = 1;
        else if (p1 == 0) winner = 0;
    }

    private int activeCount(int owner) {
        int count = 0;
        for (AlkkagiStone stone : stones) {
            if (stone.getOwner() == owner && stone.isActive()) count++;
        }
        return count;
    }

    private double clamp(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) return 0.5;
        return Math.max(-0.2, Math.min(1.2, value));
    }

    private double clampVelocity(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) return 0;
        return Math.max(-32, Math.min(32, value));
    }

    public int getNumPlayers() { return numPlayers; }
    public List<AlkkagiStone> getStones() { return stones; }
    public int getCurrentTurn() { return currentTurn; }
    public int getWinner() { return winner; }
    public int getShotCount() { return shotCount; }
    public AlkkagiShot getActiveShot() { return activeShot; }
}
