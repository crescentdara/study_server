package com.studyplatform.model.davinci;

import java.util.*;

public class DaVinciGame {
    // tile ID: 0-11=black 0-11, 12-23=white 0-11, 24=black joker, 25=white joker
    private final List<Integer> pool;
    private final List<List<Integer>> playerTiles;
    private final List<List<Boolean>> revealed;

    private int currentTurn;
    private int winner = -1;
    private final int numPlayers;

    // Turn state
    private Integer pendingTileId = null;     // drawn from pool, not yet placed
    private Integer drawnTileId   = null;     // placed this turn (for highlight)
    private boolean drawnRevealed = false;
    private int correctGuessesThisTurn = 0;

    public static int tileNumber(int id) { return id >= 24 ? -1 : id % 12; }
    /** Visual color of the tile (joker takes on its own color: 24=black, 25=white) */
    public static String tileColor(int id) {
        if (id == 24) return "black"; // black joker
        if (id == 25) return "white"; // white joker
        return id < 12 ? "black" : "white";
    }
    public static boolean isJoker(int id) { return id >= 24; }

    /** Ascending sort key: number*2 + color (black=0 < white=1). Jokers return -1. */
    public static int tileOrder(int id) {
        if (isJoker(id)) return -1;
        return tileNumber(id) * 2 + (id < 12 ? 0 : 1);
    }

    /** Check that inserting id at pos maintains ascending order (jokers in row are wildcards). */
    private static boolean isValidInsertPosition(List<Integer> row, int id, int pos) {
        if (isJoker(id)) return true;
        int ord = tileOrder(id);
        // nearest non-joker to the left
        for (int i = pos - 1; i >= 0; i--) {
            if (!isJoker(row.get(i))) {
                if (tileOrder(row.get(i)) > ord) return false;
                break;
            }
        }
        // nearest non-joker to the right
        for (int i = pos; i < row.size(); i++) {
            if (!isJoker(row.get(i))) {
                if (tileOrder(row.get(i)) < ord) return false;
                break;
            }
        }
        return true;
    }

    public DaVinciGame(int numPlayers) {
        this.numPlayers = numPlayers;
        pool = new ArrayList<>();
        for (int i = 0; i < 26; i++) pool.add(i);
        Collections.shuffle(pool);

        playerTiles = new ArrayList<>();
        revealed    = new ArrayList<>();
        for (int p = 0; p < numPlayers; p++) {
            List<Integer> hand = new ArrayList<>();
            List<Boolean> rev  = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                hand.add(pool.remove(0));
                rev.add(false);
            }
            sortRow(hand, rev);
            playerTiles.add(hand);
            revealed.add(rev);
        }
        currentTurn = 0;
    }

    private static void sortRow(List<Integer> tiles, List<Boolean> revs) {
        int n = tiles.size();
        Integer[] idx = new Integer[n];
        for (int i = 0; i < n; i++) idx[i] = i;
        Arrays.sort(idx, (a, b) -> compareTiles(tiles.get(a), tiles.get(b)));
        List<Integer> st = new ArrayList<>();
        List<Boolean> sr = new ArrayList<>();
        for (int i : idx) { st.add(tiles.get(i)); sr.add(revs.get(i)); }
        tiles.clear(); tiles.addAll(st);
        revs.clear();  revs.addAll(sr);
    }

    private static int compareTiles(int a, int b) {
        // jokers go to end on initial sort
        if (isJoker(a) && isJoker(b)) return Integer.compare(a, b);
        if (isJoker(a)) return 1;
        if (isJoker(b)) return -1;
        int na = tileNumber(a), nb = tileNumber(b);
        if (na != nb) return Integer.compare(na, nb);
        return tileColor(a).equals("black") ? -1 : 1;
    }

    /** Draw a tile from pool — stores as pendingTileId (not yet placed in row). */
    public String drawTile(int playerIndex) {
        if (playerIndex != currentTurn) return "Not your turn";
        if (drawnTileId != null || pendingTileId != null) return "Already drew this turn";
        if (pool.isEmpty()) return "Pool is empty";
        pendingTileId = pool.remove(0);
        drawnRevealed = false;
        correctGuessesThisTurn = 0;
        return null;
    }

    /** Place the pending tile at the given position in the player's row. */
    public String placeTile(int playerIndex, int position) {
        if (playerIndex != currentTurn) return "Not your turn";
        if (pendingTileId == null) return "No tile to place";
        List<Integer> row = playerTiles.get(playerIndex);
        List<Boolean> rev = revealed.get(playerIndex);
        int pos = Math.max(0, Math.min(row.size(), position));
        if (!isValidInsertPosition(row, pendingTileId, pos)) {
            return "Invalid position: tiles must be in ascending order";
        }
        row.add(pos, pendingTileId);
        rev.add(pos, false);
        drawnTileId   = pendingTileId;
        pendingTileId = null;
        return null;
    }

    /**
     * Guess a tile.
     * Pool-empty rule: if pool was empty this turn (no pending/drawn tile), guess is allowed directly.
     */
    public String guess(int playerIndex, int targetPlayer, int targetPos, int guessedNumber) {
        if (playerIndex != currentTurn) return "Not your turn";
        if (pendingTileId != null) return "Must place your tile before guessing";
        // Must have drawn unless pool was empty (drawnTileId==null means pool was empty skip)
        // We allow guess without draw only when pool is empty
        if (drawnTileId == null && !pool.isEmpty()) return "Must draw first";

        if (targetPlayer == playerIndex) return "Cannot guess your own tile";
        if (isEliminated(targetPlayer)) return "That player is eliminated";
        List<Integer> tRow = playerTiles.get(targetPlayer);
        List<Boolean> tRev = revealed.get(targetPlayer);
        if (targetPos < 0 || targetPos >= tRow.size()) return "Invalid position";
        if (tRev.get(targetPos)) return "That tile is already revealed";

        int tileId = tRow.get(targetPos);
        int actualNumber = isJoker(tileId) ? -1 : tileNumber(tileId);
        boolean correct  = (actualNumber == guessedNumber);

        if (correct) {
            tRev.set(targetPos, true);
            correctGuessesThisTurn++;
            checkWin();
            return null;
        } else {
            revealDrawnTile(playerIndex);
            advanceTurn();
            return "WRONG";
        }
    }

    private void revealDrawnTile(int playerIndex) {
        if (drawnTileId == null) return;
        List<Integer> row = playerTiles.get(playerIndex);
        List<Boolean> rev = revealed.get(playerIndex);
        int pos = row.lastIndexOf(drawnTileId); // use lastIndexOf in case of duplicates
        if (pos >= 0) rev.set(pos, true);
        drawnRevealed = true;
    }

    public String pass(int playerIndex) {
        if (playerIndex != currentTurn) return "Not your turn";
        if (pendingTileId != null) return "Must place your tile before passing";
        if (drawnTileId == null && !pool.isEmpty()) return "Must draw first";
        if (correctGuessesThisTurn == 0) return "Must guess at least once before passing";
        advanceTurn();
        return null;
    }

    private void advanceTurn() {
        pendingTileId  = null;
        drawnTileId    = null;
        drawnRevealed  = false;
        correctGuessesThisTurn = 0;
        int next   = (currentTurn + 1) % numPlayers;
        int tries  = 0;
        while (isEliminated(next) && tries < numPlayers) {
            next = (next + 1) % numPlayers;
            tries++;
        }
        currentTurn = next;
        checkWin();
    }

    private void checkWin() {
        List<Integer> alive = new ArrayList<>();
        for (int p = 0; p < numPlayers; p++) {
            if (!isEliminated(p)) alive.add(p);
        }
        if (alive.size() == 1) winner = alive.get(0);
        else if (alive.isEmpty()) winner = 0;
    }

    public boolean isEliminated(int p) {
        return revealed.get(p).stream().allMatch(r -> r);
    }

    // --- Getters ---
    public int getNumPlayers()               { return numPlayers; }
    public int getCurrentTurn()              { return currentTurn; }
    public int getWinner()                   { return winner; }
    public List<Integer> getPool()           { return pool; }
    public List<List<Integer>> getPlayerTiles() { return playerTiles; }
    public List<List<Boolean>> getRevealed()    { return revealed; }
    public Integer getPendingTileId()        { return pendingTileId; }
    public Integer getDrawnTileId()          { return drawnTileId; }
    public boolean isDrawnRevealed()         { return drawnRevealed; }
    public int getCorrectGuessesThisTurn()   { return correctGuessesThisTurn; }
}
