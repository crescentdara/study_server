package com.studyplatform.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** 끝없는 계단의 이번 주 기록. 월요일이 되면 주간 기록만 자동 초기화한다. */
@Service
public class InfiniteStairsRecordService {
    private final ObjectMapper objectMapper;
    private final Path recordPath;
    private Store store;

    public InfiniteStairsRecordService(
            ObjectMapper objectMapper,
            @Value("${infinite-stairs.records.path:data/infinite-stairs-records.json}") String recordPath
    ) {
        this.objectMapper = objectMapper;
        this.recordPath = Path.of(recordPath).toAbsolutePath().normalize();
        this.store = load();
    }

    public synchronized void record(String nickname, int score) {
        String name = displayName(nickname);
        if (name.isBlank() || score <= 0) return;
        rollWeekIfNeeded();
        PlayerRecord player = store.players.computeIfAbsent(key(name), ignored -> new PlayerRecord());
        player.nickname = name;
        player.games += 1;
        player.lastScore = score;
        player.lastPlayedAt = System.currentTimeMillis();
        if (score > player.best) {
            player.best = score;
            player.bestAt = player.lastPlayedAt;
        }
        persist();
    }

    public synchronized List<Map<String, Object>> leaderboard(int limit) {
        rollWeekIfNeeded();
        int max = limit <= 0 ? 10 : Math.min(limit, 100);
        List<PlayerRecord> ranked = store.players.values().stream()
                .sorted(Comparator.comparingInt((PlayerRecord item) -> -item.best)
                        .thenComparingLong(item -> item.bestAt == 0 ? Long.MAX_VALUE : item.bestAt)
                        .thenComparing(item -> item.nickname, String.CASE_INSENSITIVE_ORDER))
                .toList();
        List<Map<String, Object>> result = new ArrayList<>();
        for (int index = 0; index < ranked.size() && index < max; index++) {
            PlayerRecord item = ranked.get(index);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("rank", index + 1);
            row.put("nickname", item.nickname);
            row.put("best", item.best);
            row.put("games", item.games);
            row.put("lastScore", item.lastScore);
            result.add(row);
        }
        return result;
    }

    public synchronized String weekStart() {
        rollWeekIfNeeded();
        return store.weekKey;
    }

    private void rollWeekIfNeeded() {
        String current = weekKey(System.currentTimeMillis());
        if (current.equals(store.weekKey)) return;
        store.weekKey = current;
        store.players.clear();
        persist();
    }

    private String weekKey(long timestamp) {
        return Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).toLocalDate()
                .with(DayOfWeek.MONDAY).toString();
    }

    private String key(String nickname) { return displayName(nickname).toLowerCase(Locale.ROOT); }
    private String displayName(String nickname) { return nickname == null ? "" : nickname.trim(); }

    private Store load() {
        if (!Files.exists(recordPath)) return new Store();
        try {
            Store loaded = objectMapper.readValue(recordPath.toFile(), Store.class);
            if (loaded == null) return new Store();
            if (loaded.weekKey == null) loaded.weekKey = "";
            if (loaded.players == null) loaded.players = new LinkedHashMap<>();
            return loaded;
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
            throw new IllegalStateException("Failed to save infinite stairs records.", exception);
        }
    }

    public static class Store {
        public String weekKey = "";
        public Map<String, PlayerRecord> players = new LinkedHashMap<>();
    }

    public static class PlayerRecord {
        public String nickname = "";
        public int best;
        public int games;
        public int lastScore;
        public long bestAt;
        public long lastPlayedAt;
    }
}
