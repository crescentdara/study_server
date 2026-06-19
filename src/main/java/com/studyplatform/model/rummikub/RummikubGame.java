package com.studyplatform.model.rummikub;

import java.util.*;
import java.util.stream.Collectors;

public class RummikubGame {
    private final List<Integer> pool;
    private final List<List<Integer>> hands;
    private List<List<Integer>> table;
    private final boolean[] initialMeld;
    private int currentTurn = 0;
    private int winner = -1;
    private boolean hasDrawnThisTurn = false;
    private final int numPlayers;

    public static final String[] COLOR_NAMES = {"black", "red", "blue", "orange"};

    public RummikubGame(int numPlayers) {
        this.numPlayers = numPlayers;
        pool = new ArrayList<>();
        for (int i = 0; i < 106; i++) pool.add(i);
        Collections.shuffle(pool);
        hands = new ArrayList<>();
        for (int p = 0; p < numPlayers; p++) {
            List<Integer> hand = new ArrayList<>();
            for (int i = 0; i < 14; i++) hand.add(pool.remove(0));
            hands.add(hand);
        }
        table = new ArrayList<>();
        initialMeld = new boolean[numPlayers];
    }

    public static int tileNumber(int id) {
        if (id >= 104) return 0;
        return (id % 52) % 13 + 1;
    }

    public static int tileColor(int id) {
        if (id >= 104) return -1;
        return (id % 52) / 13;
    }

    public static boolean isJoker(int id) {
        return id >= 104;
    }

    public static boolean isValidSet(List<Integer> set) {
        if (set.size() < 3) return false;
        List<Integer> normals = set.stream().filter(t -> !isJoker(t)).collect(Collectors.toList());
        int jokers = set.size() - normals.size();
        if (normals.isEmpty()) return true;
        return isValidGroup(normals, jokers, set.size()) || isValidRun(normals, jokers, set.size());
    }

    private static boolean isValidGroup(List<Integer> normals, int jokers, int totalSize) {
        if (totalSize > 4) return false;
        int num = tileNumber(normals.get(0));
        Set<Integer> colors = new HashSet<>();
        for (int t : normals) {
            if (tileNumber(t) != num) return false;
            if (!colors.add(tileColor(t))) return false;
        }
        return true;
    }

    private static boolean isValidRun(List<Integer> normals, int jokers, int totalSize) {
        int color = tileColor(normals.get(0));
        List<Integer> nums = new ArrayList<>();
        for (int t : normals) {
            if (tileColor(t) != color) return false;
            nums.add(tileNumber(t));
        }
        Collections.sort(nums);
        for (int i = 1; i < nums.size(); i++) {
            if (nums.get(i) == nums.get(i - 1)) return false;
        }
        int gaps = 0;
        for (int i = 1; i < nums.size(); i++) gaps += nums.get(i) - nums.get(i - 1) - 1;
        if (gaps > jokers) return false;
        int remaining = jokers - gaps;
        int lo = nums.get(0), hi = nums.get(nums.size() - 1);
        for (int before = 0; before <= remaining; before++) {
            int s = lo - before;
            int e = s + totalSize - 1;
            if (s >= 1 && e <= 13 && e >= hi) return true;
        }
        return false;
    }

    /** Returns error message or null on success */
    public String drawTile(int playerIndex) {
        if (playerIndex != currentTurn) return "Not your turn";
        if (hasDrawnThisTurn) return "Already drew this turn";
        if (pool.isEmpty()) return "Pool is empty";
        hands.get(playerIndex).add(pool.remove(0));
        currentTurn = (currentTurn + 1) % numPlayers;
        hasDrawnThisTurn = false; // reset for the next player
        return null;
    }

    /**
     * Player submits a proposed full table state.
     * newTable: all sets on table after player's move.
     * Server validates conservation of tiles (hand+oldTable → newTable+newHand)
     * and set validity. On success updates state and advances turn.
     * Returns error message or null on success.
     */
    public String placeOnTable(int playerIndex, List<List<Integer>> newTable) {
        if (playerIndex != currentTurn) return "Not your turn";
        if (hasDrawnThisTurn) return "Already drew; cannot place tiles";

        List<Integer> oldFlat = table.stream().flatMap(Collection::stream).collect(Collectors.toList());
        List<Integer> newFlat = newTable.stream().flatMap(Collection::stream).collect(Collectors.toList());

        // Compute tiles used from hand = newFlat - oldFlat
        List<Integer> available = new ArrayList<>(oldFlat);
        List<Integer> usedFromHand = new ArrayList<>();
        for (int t : newFlat) {
            if (!available.remove(Integer.valueOf(t))) {
                // not in old table, must come from hand
                usedFromHand.add(t);
            }
        }
        // Tiles remaining in available were removed from table — not allowed
        if (!available.isEmpty()) return "Cannot remove existing table tiles";

        // Verify usedFromHand tiles are in player's hand
        List<Integer> handCopy = new ArrayList<>(hands.get(playerIndex));
        for (int t : usedFromHand) {
            if (!handCopy.remove(Integer.valueOf(t))) return "Tile not in your hand: " + t;
        }
        if (usedFromHand.isEmpty()) return "Must play at least one tile from hand";

        // Validate all sets
        for (List<Integer> set : newTable) {
            if (!isValidSet(set)) return "Invalid set: " + set;
        }

        // Initial meld check
        if (!initialMeld[playerIndex]) {
            int pts = usedFromHand.stream()
                    .filter(t -> !isJoker(t))
                    .mapToInt(RummikubGame::tileNumber)
                    .sum();
            if (pts < 30) return "First meld must score at least 30 points (yours: " + pts + ")";
            initialMeld[playerIndex] = true;
        }

        // Apply
        hands.set(playerIndex, handCopy);
        table = new ArrayList<>(newTable);
        if (hands.get(playerIndex).isEmpty()) winner = playerIndex;
        currentTurn = (currentTurn + 1) % numPlayers;
        hasDrawnThisTurn = false;
        return null;
    }

    // --- Getters ---
    public int getNumPlayers() { return numPlayers; }
    public int getCurrentTurn() { return currentTurn; }
    public int getWinner() { return winner; }
    public List<Integer> getPool() { return pool; }
    public List<List<Integer>> getHands() { return hands; }
    public List<List<Integer>> getTable() { return table; }
    public boolean[] getInitialMeld() { return initialMeld; }
    public boolean isHasDrawnThisTurn() { return hasDrawnThisTurn; }
}
