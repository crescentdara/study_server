package com.studyplatform.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class TetrisRecordService {
    private static final int PLACEMENT_MATCHES = 5;
    private static final int INITIAL_RATING = 800;
    private static final int PLACEMENT_K_FACTOR = 80;
    private static final int RANKED_K_FACTOR = 32;
    private static final double LOSS_RP_MULTIPLIER = 0.70;
    private static final int MASTER_RATING = 2800;
    private static final int GRANDMASTER_RATING = 3200;
    private static final int CHALLENGER_RATING = 3600;
    private static final String[] TIERS = {"IRON", "BRONZE", "SILVER", "GOLD", "PLATINUM", "EMERALD", "DIAMOND"};
    private static final String[] DIVISIONS = {"IV", "III", "II", "I"};
    private final ObjectMapper objectMapper;
    private final Path recordPath;
    private RecordStore store;

    @Autowired
    public TetrisRecordService(
            ObjectMapper objectMapper,
            @Value("${tetris.records.path:data/tetris-records.json}") String recordPath
    ) {
        this(objectMapper, Path.of(recordPath));
    }

    TetrisRecordService(ObjectMapper objectMapper, Path recordPath) {
        this.objectMapper = objectMapper;
        this.recordPath = recordPath.toAbsolutePath().normalize();
        this.store = load();
    }

    public synchronized boolean recordCompletedMatch(String matchId, List<String> ranking) {
        if (matchId == null || matchId.isBlank() || ranking == null || ranking.size() < 2) return false;
        List<String> names = ranking.stream().map(this::displayName).toList();
        if (names.stream().anyMatch(String::isBlank)) return false;
        Set<String> uniqueNames = new LinkedHashSet<>(names.stream().map(this::key).toList());
        if (uniqueNames.size() != names.size() || store.completedMatchIds.contains(matchId)) return false;

        Map<String, Integer> ratingsBefore = new LinkedHashMap<>();
        Map<String, String> ranksBefore = new LinkedHashMap<>();
        for (String name : names) {
            PlayerRecord record = player(name);
            ratingsBefore.put(key(name), record.rating);
            ranksBefore.put(key(name), rankLabel(record));
        }

        for (int index = 0; index < names.size(); index += 1) {
            PlayerRecord player = player(names.get(index));
            player.matches += 1;
            if (index == 0) player.wins += 1;
            else player.losses += 1;
            updateRank(player, names, index, ratingsBefore, matchId, ranksBefore.get(key(names.get(index))));
        }

        for (int higher = 0; higher < names.size(); higher += 1) {
            for (int lower = higher + 1; lower < names.size(); lower += 1) {
                PlayerRecord winner = player(names.get(higher));
                PlayerRecord loser = player(names.get(lower));
                opponent(winner, names.get(lower)).wins += 1;
                opponent(loser, names.get(higher)).losses += 1;
            }
        }

        store.completedMatchIds.add(matchId);
        persist();
        return true;
    }

    public synchronized Map<String, Map<String, Object>> recordsFor(Collection<String> nicknames) {
        Map<String, Map<String, Object>> result = new LinkedHashMap<>();
        if (nicknames == null) return result;
        for (String nickname : nicknames) {
            String displayName = displayName(nickname);
            PlayerRecord player = store.players.get(key(displayName));
            result.put(displayName, toPublicRecord(player));
        }
        return result;
    }

    private Map<String, Object> toPublicRecord(PlayerRecord player) {
        Map<String, Object> result = new LinkedHashMap<>();
        int matches = player == null ? 0 : player.matches;
        int wins = player == null ? 0 : player.wins;
        int losses = player == null ? 0 : player.losses;
        result.put("matches", matches);
        result.put("wins", wins);
        result.put("losses", losses);
        Rank rank = rankOf(player);
        result.put("placementGames", player == null ? 0 : Math.min(PLACEMENT_MATCHES, player.placementGames));
        result.put("placementRequired", PLACEMENT_MATCHES);
        result.put("ranked", rank.ranked());
        result.put("tier", rank.tier());
        result.put("division", rank.division());
        result.put("rp", rank.rp());
        result.put("lastRankDelta", player == null ? 0 : player.lastRankDelta);
        result.put("lastRankChanged", player != null && player.lastRankChanged);
        result.put("lastRankBefore", player == null ? "UNRANKED" : player.lastRankBefore);
        result.put("lastRankAfter", player == null ? "UNRANKED" : player.lastRankAfter);
        result.put("lastRankMatchId", player == null ? "" : player.lastRankMatchId);
        Map<String, Object> opponents = new LinkedHashMap<>();
        if (player != null) {
            player.opponents.values().stream()
                    .sorted((left, right) -> left.nickname.compareToIgnoreCase(right.nickname))
                    .forEach(record -> opponents.put(record.nickname, Map.of(
                            "wins", record.wins,
                            "losses", record.losses
                    )));
        }
        result.put("opponents", opponents);
        return result;
    }

    private PlayerRecord player(String nickname) {
        String playerKey = key(nickname);
        PlayerRecord record = store.players.computeIfAbsent(playerKey, ignored -> new PlayerRecord());
        record.nickname = displayName(nickname);
        return record;
    }

    private void updateRank(
            PlayerRecord player,
            List<String> ranking,
            int playerPosition,
            Map<String, Integer> ratingsBefore,
            String matchId,
            String rankBefore
    ) {
        double actualTotal = 0;
        double expectedTotal = 0;
        int ownRating = ratingsBefore.getOrDefault(key(player.nickname), INITIAL_RATING);
        for (int opponentPosition = 0; opponentPosition < ranking.size(); opponentPosition += 1) {
            if (opponentPosition == playerPosition) continue;
            int opponentRating = ratingsBefore.getOrDefault(key(ranking.get(opponentPosition)), INITIAL_RATING);
            actualTotal += playerPosition < opponentPosition ? 1.0 : 0.0;
            expectedTotal += 1.0 / (1.0 + Math.pow(10.0, (opponentRating - ownRating) / 400.0));
        }
        int opponents = ranking.size() - 1;
        int kFactor = player.placementGames < PLACEMENT_MATCHES ? PLACEMENT_K_FACTOR : RANKED_K_FACTOR;
        double rawDelta = kFactor * ((actualTotal / opponents) - (expectedTotal / opponents));
        int delta = (int) Math.round(rawDelta < 0 ? rawDelta * LOSS_RP_MULTIPLIER : rawDelta);
        player.rating = Math.max(0, ownRating + delta);
        player.placementGames = Math.min(PLACEMENT_MATCHES, player.placementGames + 1);
        player.lastRankDelta = delta;
        player.lastRankBefore = rankBefore;
        player.lastRankAfter = rankLabel(player);
        player.lastRankChanged = !player.lastRankBefore.equals(player.lastRankAfter);
        player.lastRankMatchId = matchId;
    }

    private Rank rankOf(PlayerRecord player) {
        if (player == null || player.placementGames < PLACEMENT_MATCHES) {
            return new Rank(false, "UNRANKED", "", 0);
        }
        int rating = Math.max(0, player.rating);
        if (rating >= CHALLENGER_RATING) return new Rank(true, "CHALLENGER", "", rating - MASTER_RATING);
        if (rating >= GRANDMASTER_RATING) return new Rank(true, "GRANDMASTER", "", rating - MASTER_RATING);
        if (rating >= MASTER_RATING) return new Rank(true, "MASTER", "", rating - MASTER_RATING);
        int tierIndex = Math.min(TIERS.length - 1, rating / 400);
        int withinTier = rating % 400;
        return new Rank(true, TIERS[tierIndex], DIVISIONS[Math.min(3, withinTier / 100)], withinTier % 100);
    }

    private String rankLabel(PlayerRecord player) {
        Rank rank = rankOf(player);
        return rank.ranked() ? rank.tier() + (rank.division().isBlank() ? "" : " " + rank.division()) : "UNRANKED";
    }

    private OpponentRecord opponent(PlayerRecord player, String nickname) {
        String opponentKey = key(nickname);
        OpponentRecord record = player.opponents.computeIfAbsent(opponentKey, ignored -> new OpponentRecord());
        record.nickname = displayName(nickname);
        return record;
    }

    private String key(String nickname) {
        return displayName(nickname).toLowerCase(Locale.ROOT);
    }

    private String displayName(String nickname) {
        return nickname == null ? "" : nickname.trim();
    }

    private RecordStore load() {
        if (!Files.exists(recordPath)) return new RecordStore();
        try {
            RecordStore loaded = objectMapper.readValue(recordPath.toFile(), RecordStore.class);
            return loaded == null ? new RecordStore() : loaded;
        } catch (IOException ignored) {
            return new RecordStore();
        }
    }

    private void persist() {
        try {
            Path parent = recordPath.getParent();
            if (parent != null) Files.createDirectories(parent);
            Path temporary = recordPath.resolveSibling(recordPath.getFileName() + ".tmp");
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(temporary.toFile(), store);
            try {
                Files.move(temporary, recordPath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, recordPath, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to save TETRIS records.", exception);
        }
    }

    public static class RecordStore {
        public Set<String> completedMatchIds = new LinkedHashSet<>();
        public Map<String, PlayerRecord> players = new LinkedHashMap<>();
    }

    public static class PlayerRecord {
        public String nickname = "";
        public int matches;
        public int wins;
        public int losses;
        public int rating = INITIAL_RATING;
        public int placementGames;
        public int lastRankDelta;
        public boolean lastRankChanged;
        public String lastRankBefore = "UNRANKED";
        public String lastRankAfter = "UNRANKED";
        public String lastRankMatchId = "";
        public Map<String, OpponentRecord> opponents = new LinkedHashMap<>();
    }

    private record Rank(boolean ranked, String tier, String division, int rp) {}

    public static class OpponentRecord {
        public String nickname = "";
        public int wins;
        public int losses;
    }
}
