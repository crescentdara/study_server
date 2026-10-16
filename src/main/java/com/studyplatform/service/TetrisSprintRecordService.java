package com.studyplatform.service;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Persistent personal-best records for the single-player 40-line sprint. */
public class TetrisSprintRecordService {
    private final ObjectMapper objectMapper;
    private final Path recordPath;
    private Store store;

    public TetrisSprintRecordService(ObjectMapper objectMapper, Path recordPath) {
        this.objectMapper = objectMapper;
        this.recordPath = recordPath.toAbsolutePath().normalize();
        this.store = load();
    }

    public synchronized Map<String, Object> record(String nickname, long elapsedMs) {
        String displayName = nickname == null ? "" : nickname.trim();
        if (displayName.isBlank() || elapsedMs < 0) return Map.of();
        String key = displayName.toLowerCase(Locale.ROOT);
        Record record = store.players.computeIfAbsent(key, ignored -> new Record());
        record.nickname = displayName;
        record.attempts += 1;
        record.lastTimeMs = elapsedMs;
        boolean personalBest = record.bestTimeMs <= 0 || elapsedMs < record.bestTimeMs;
        if (personalBest) record.bestTimeMs = elapsedMs;
        persist();
        Map<String, Object> result = publicRecord(record);
        result.put("personalBest", personalBest);
        result.put("rank", rankOf(record));
        return result;
    }

    public synchronized List<Map<String, Object>> leaderboard(int limit) {
        int size = limit <= 0 ? 20 : Math.min(100, limit);
        return store.players.values().stream()
                .filter(record -> record.bestTimeMs > 0)
                .sorted(Comparator.comparingLong((Record record) -> record.bestTimeMs)
                        .thenComparing(record -> record.nickname, String.CASE_INSENSITIVE_ORDER))
                .limit(size)
                .map(record -> {
                    Map<String, Object> row = publicRecord(record);
                    row.put("rank", rankOf(record));
                    return row;
                })
                .toList();
    }

    private int rankOf(Record target) {
        return (int) store.players.values().stream()
                .filter(record -> record.bestTimeMs > 0)
                .sorted(Comparator.comparingLong((Record record) -> record.bestTimeMs)
                        .thenComparing(record -> record.nickname, String.CASE_INSENSITIVE_ORDER))
                .takeWhile(record -> record != target)
                .count() + 1;
    }

    private Map<String, Object> publicRecord(Record record) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("nickname", record.nickname);
        row.put("bestTimeMs", record.bestTimeMs);
        row.put("lastTimeMs", record.lastTimeMs);
        row.put("attempts", record.attempts);
        return row;
    }

    private Store load() {
        if (!Files.exists(recordPath)) return new Store();
        try {
            Store loaded = objectMapper.readValue(recordPath.toFile(), Store.class);
            return loaded == null ? new Store() : loaded;
        } catch (IOException ignored) {
            return new Store();
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
            throw new IllegalStateException("Failed to save Tetris sprint records.", exception);
        }
    }

    public static class Store {
        public Map<String, Record> players = new LinkedHashMap<>();
    }

    public static class Record {
        public String nickname = "";
        public long bestTimeMs;
        public long lastTimeMs;
        public int attempts;
    }
}
