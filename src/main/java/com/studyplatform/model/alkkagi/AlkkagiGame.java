package com.studyplatform.model.alkkagi;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class AlkkagiGame {
    public static final long TURN_TIME_LIMIT_MS = 0L;
    public static final long SHOT_RESULT_TIMEOUT_MS = 45_000L;
    private static final double STONE_RADIUS_X = 15.0 / 1200.0;
    private static final double STONE_RADIUS_Y = 15.0 / 760.0;
    private static final double POSITION_EPSILON = 0.00001;
    private final int numPlayers;
    private final List<AlkkagiStone> stones = new ArrayList<>();
    private final List<String> shotLog = new ArrayList<>();
    private final String mapType;
    private final long mapSeed;
    private int currentTurn = 0;
    private int winner = -1;
    private int shotCount = 0;
    private int nextShotId = 1;
    private long turnStartedAt = System.currentTimeMillis();
    private long activeShotStartedAt = 0L;
    private int mapPhase = 0;
    private AlkkagiShot activeShot = null;

    public AlkkagiGame(int numPlayers) {
        this.numPlayers = Math.max(1, Math.min(3, numPlayers));
        this.mapSeed = new Random().nextLong();
        Random random = new Random(mapSeed);
        List<String> maps = this.numPlayers == 3 ? Arrays.asList("HEX_ARENA", "HEX_TYPHOON", "HEX_RUINS") : Arrays.asList(
                "CLASSIC",
                "CENTER_HOLE",
                "CORNER_HOLES",
                "SIDE_POCKETS",
                "PILLARS",
                "BUMPER_FIELD",
                "PINBALL",
                "NARROW_BRIDGE",
                "RIVER",
                "ROULETTE_ARENA",
                "TYPHOON_ISLAND",
                "PORTAL_MAZE",
                "COLLAPSE_ICE"
        );
        this.mapType = maps.get(random.nextInt(maps.size()));
        initStones(random);
    }

    private void initStones(Random random) {
        int id = 0;
        List<String> specialPool = Arrays.asList(
                "HEAVY", "SLIPPERY", "BOMB", "LIGHT",
                "BLACK_HOLE", "WARP", "SPLIT", "GHOST",
                "LIGHTNING", "CURSE", "ROULETTE", "MINE"
        );
        for (int owner = 0; owner < numPlayers; owner++) {
            int stoneCount = 6;
            int specialCount = random.nextInt(stoneCount + 1);
            List<String> types = new ArrayList<>(Collections.nCopies(stoneCount, "NORMAL"));
            for (int index = 0; index < specialCount; index++) {
                types.set(index, specialPool.get(random.nextInt(specialPool.size())));
            }
            Collections.shuffle(types, random);
            for (int index = 0; index < stoneCount; index++) {
                double[] position = startPosition(owner, index, stoneCount);
                stones.add(new AlkkagiStone(id++, owner, position[0], position[1], true, types.get(index)));
            }
        }
    }

    private double[] startPosition(int owner, int index, int count) {
        double spread = count == 1 ? 0 : index / (double) (count - 1) - 0.5;
        if (numPlayers == 3) {
            if (owner == 0) return new double[]{0.16, 0.50 + spread * 0.48};
            if (owner == 1) return new double[]{0.84, 0.50 - spread * 0.48};
            return new double[]{0.50 + spread * 0.42, 0.15};
        }
        if (owner == 0) return new double[]{0.14, 0.50 + spread * 0.58};
        return new double[]{0.86, 0.50 - spread * 0.58};
    }

    public synchronized String beginShot(int playerIndex, int stoneId, double vx, double vy) {
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
        activeShotStartedAt = System.currentTimeMillis();
        return null;
    }

    public synchronized String applyShotResult(int playerIndex, int shotId, List<AlkkagiStone> nextStones) {
        if (winner >= 0) return "Game already finished";
        if (activeShot == null) return "No shot is resolving";
        if (activeShot.getId() != shotId) return "Shot id mismatch";
        if (activeShot.getPlayerIndex() != playerIndex) return "Only shooter can confirm result";
        if (nextStones == null || nextStones.size() != stones.size()) return "Invalid stone state";
        int previousTurn = currentTurn;
        int beforeOwn = activeCount(playerIndex);
        int beforeOpponents = activeOpponentCount(playerIndex);

        boolean[] receivedIds = new boolean[stones.size()];
        for (AlkkagiStone next : nextStones) {
            int id = next.getId();
            if (id < 0 || id >= stones.size() || receivedIds[id]) return "Invalid or duplicate stone id";
            receivedIds[id] = true;

            AlkkagiStone current = stones.get(id);
            if (next.getOwner() != current.getOwner()) return "Stone owner cannot change";
            // Stone types are server-owned. Older clients may omit this field, so preserve it instead of rejecting the turn.
            next.setType(current.getType());
            if (!isFinite(next.getX()) || !isFinite(next.getY())) return "Invalid stone position";
            if (!current.isActive()) {
                if (next.isActive() || !samePosition(current, next)) return "Out stone cannot be changed";
                continue;
            }
            if (!next.isActive() && !isOutOfPlay(next)) return "Stone can only be removed out of play";
            if (next.isActive() && !isInsideBoard(next)) return "Active stone is outside the board";
        }

        stones.clear();
        stones.addAll(copySorted(nextStones));
        shotCount++;
        mapPhase++;
        activeShot = null;
        activeShotStartedAt = 0L;
        updateWinner();
        int ownOut = beforeOwn - activeCount(playerIndex);
        int opponentOut = beforeOpponents - activeOpponentCount(playerIndex);
        pushLog("P" + (playerIndex + 1) + " shot: opponents -" + Math.max(0, opponentOut)
                + ", self -" + Math.max(0, ownOut));
        if (winner < 0) {
            currentTurn = nextActiveTurn(currentTurn);
            turnStartedAt = System.currentTimeMillis();
        } else {
            currentTurn = previousTurn;
        }
        return null;
    }

    public synchronized String timeoutTurn() {
        if (activeShot == null) return "No shot is resolving";
        if (System.currentTimeMillis() - activeShotStartedAt < SHOT_RESULT_TIMEOUT_MS) {
            return "Shot is still resolving";
        }
        int shooter = activeShot.getPlayerIndex();
        activeShot = null;
        activeShotStartedAt = 0L;
        currentTurn = nextActiveTurn(currentTurn);
        turnStartedAt = System.currentTimeMillis();
        pushLog("P" + (shooter + 1) + " shot timed out");
        return null;
    }

    private List<AlkkagiStone> copySorted(List<AlkkagiStone> source) {
        List<AlkkagiStone> copy = new ArrayList<>();
        source.stream()
                .sorted((a, b) -> Integer.compare(a.getId(), b.getId()))
                .forEach(s -> copy.add(new AlkkagiStone(s.getId(), s.getOwner(), s.getX(), s.getY(), s.isActive(), s.getType())));
        return copy;
    }

    private void updateWinner() {
        if (numPlayers == 1) {
            if (activeCount(0) == 0) winner = 0;
            return;
        }
        int alivePlayers = 0;
        int lastAlive = -1;
        for (int i = 0; i < numPlayers; i++) {
            if (activeCount(i) > 0) {
                alivePlayers++;
                lastAlive = i;
            }
        }
        if (alivePlayers == 0) winner = currentTurn;
        else if (alivePlayers == 1) winner = lastAlive;
    }

    private int activeCount(int owner) {
        int count = 0;
        for (AlkkagiStone stone : stones) {
            if (stone.getOwner() == owner && stone.isActive()) count++;
        }
        return count;
    }

    private int activeOpponentCount(int owner) {
        int count = 0;
        for (AlkkagiStone stone : stones) {
            if (stone.getOwner() != owner && stone.isActive()) count++;
        }
        return count;
    }

    private int nextActiveTurn(int from) {
        for (int step = 1; step <= numPlayers; step++) {
            int candidate = (from + step) % numPlayers;
            if (activeCount(candidate) > 0) return candidate;
        }
        return from;
    }

    private boolean samePosition(AlkkagiStone first, AlkkagiStone second) {
        return Math.abs(first.getX() - second.getX()) < POSITION_EPSILON
                && Math.abs(first.getY() - second.getY()) < POSITION_EPSILON;
    }

    private boolean isInsideBoard(AlkkagiStone stone) {
        if (mapType.startsWith("HEX_")) return isInsideHex(stone.getX(), stone.getY());
        // A stone remains in play until its whole radius has crossed the edge, matching the client simulation.
        return stone.getX() >= -STONE_RADIUS_X && stone.getX() <= 1 + STONE_RADIUS_X
                && stone.getY() >= -STONE_RADIUS_Y && stone.getY() <= 1 + STONE_RADIUS_Y;
    }

    private boolean isOutOfPlay(AlkkagiStone stone) {
        double x = stone.getX();
        double y = stone.getY();
        if (mapType.startsWith("HEX_") && !isInsideHex(x, y)) return true;
        if ("COLLAPSE_ICE".equals(mapType) && Math.hypot((x - 0.5) / 1.2, y - 0.5) > collapseRadius()) return true;
        if (x < -STONE_RADIUS_X || x > 1 + STONE_RADIUS_X || y < -STONE_RADIUS_Y || y > 1 + STONE_RADIUS_Y) return true;
        if ("CENTER_HOLE".equals(mapType) && inHole(x, y, 0.50, 0.50, 48.0)) return true;
        if ("CORNER_HOLES".equals(mapType) && (inHole(x, y, 0.10, 0.12, 44.0) || inHole(x, y, 0.90, 0.12, 44.0)
                || inHole(x, y, 0.10, 0.88, 44.0) || inHole(x, y, 0.90, 0.88, 44.0))) return true;
        if ("SIDE_POCKETS".equals(mapType) && (inHole(x, y, 0.06, 0.50, 48.0) || inHole(x, y, 0.94, 0.50, 48.0)
                || inHole(x, y, 0.50, 0.08, 38.0) || inHole(x, y, 0.50, 0.92, 38.0))) return true;
        if ("NARROW_BRIDGE".equals(mapType) && x > 0.38 && x < 0.62 && !(y > 0.33 && y < 0.67)) return true;
        if ("RIVER".equals(mapType) && y > 0.42 && y < 0.58 && !((x > 0.36 && x < 0.44) || (x > 0.56 && x < 0.64))) return true;
        return false;
    }

    private boolean inHole(double x, double y, double centerX, double centerY, double radiusPixels) {
        return Math.hypot((x - centerX) * 1200.0, (y - centerY) * 760.0) < radiusPixels;
    }

    private boolean isInsideHex(double x, double y) {
        if (y < 0.05 || y > 0.95) return false;
        double halfWidth = y < 0.25 ? (y - 0.05) * 1.85 : y > 0.75 ? (0.95 - y) * 1.85 : 0.37;
        return x >= 0.50 - halfWidth && x <= 0.50 + halfWidth;
    }

    private double collapseRadius() {
        return Math.max(0.24, 0.52 - mapPhase * 0.035);
    }

    private boolean isFinite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }

    private void pushLog(String message) {
        shotLog.add(message);
        while (shotLog.size() > 8) shotLog.remove(0);
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
    public int getMapPhase() { return mapPhase; }
    public long getTurnStartedAt() { return turnStartedAt; }
    public long getTurnTimeLimitMs() { return TURN_TIME_LIMIT_MS; }
    public List<String> getShotLog() { return shotLog; }
    public String getMapType() { return mapType; }
    public long getMapSeed() { return mapSeed; }
    public AlkkagiShot getActiveShot() { return activeShot; }
    public long getActiveShotStartedAt() { return activeShotStartedAt; }
}
