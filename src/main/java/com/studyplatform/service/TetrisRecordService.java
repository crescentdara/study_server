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

        for (int index = 0; index < names.size(); index += 1) {
            PlayerRecord player = player(names.get(index));
            player.matches += 1;
            if (index == 0) player.wins += 1;
            else player.losses += 1;
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
        public Map<String, OpponentRecord> opponents = new LinkedHashMap<>();
    }

    public static class OpponentRecord {
        public String nickname = "";
        public int wins;
        public int losses;
    }
}
