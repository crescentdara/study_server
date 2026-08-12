package com.studyplatform.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZoneId;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 사과게임 점수 랭킹 저장소
 *
 * 테트리스는 1:1 승패 기반 Elo(TetrisRecordService)를 쓰지만, 사과게임은
 * 점수제이므로 '닉네임별 최고 점수' 리더보드로 관리한다.
 *
 * 장부는 두 개다 — 계속 쌓이는 누적(players)과 월요일마다 비워지는
 * 주간(weeklyPlayers). 한 판의 점수는 두 곳에 각각 기록된다.
 *
 * 저장 위치는 data/applebox-records.json 이고 갱신할 때마다 임시 파일에 쓴 뒤
 * 원자적으로 교체한다(중간에 죽어도 파일이 깨지지 않도록).
 */
@Service
public class AppleBoxRecordService {
    private final ObjectMapper objectMapper;
    private final Path recordPath;
    private RecordStore store;

    @Autowired
    public AppleBoxRecordService(
            ObjectMapper objectMapper,
            @Value("${applebox.records.path:data/applebox-records.json}") String recordPath
    ) {
        this(objectMapper, Path.of(recordPath));
    }

    AppleBoxRecordService(ObjectMapper objectMapper, Path recordPath) {
        this.objectMapper = objectMapper;
        this.recordPath = recordPath.toAbsolutePath().normalize();
        this.store = load();
    }

    /**
     * 한 판의 결과를 기록한다.
     *
     * @param matchId 게임 인스턴스 ID — 같은 판이 두 번 저장되지 않게 막는 키
     * @param scores  닉네임 → 정리한 칸 수
     * @return 실제로 저장됐으면 true (중복 호출이면 false)
     */
    public synchronized boolean recordScores(String matchId, Map<String, Integer> scores) {
        if (matchId == null || matchId.isBlank() || scores == null || scores.isEmpty()) return false;
        if (store.recordedMatchIds.contains(matchId)) return false;

        long now = System.currentTimeMillis();
        rollWeekIfNeeded(now);

        boolean changed = false;
        for (Map.Entry<String, Integer> entry : scores.entrySet()) {
            String name = displayName(entry.getKey());
            if (name.isBlank()) continue;
            int score = Math.max(0, entry.getValue() == null ? 0 : entry.getValue());
            /*
             * 한 칸도 정리하지 못한 판은 세지 않는다.
             *
             * 보드만 확인하고 새로 시작하는 경우가 많은데, 그걸 판수에 넣으면 판수와
             * 평균이 부풀려지고 0점이 랭킹에 올라온다. 그래서 '한 번이라도 정리에
             * 성공한 판'만 한 판으로 인정한다.
             */
            if (score <= 0) continue;
            // 누적과 주간은 같은 점수를 각자의 장부에 따로 적는다
            applyScore(store.players, name, score, now);
            applyScore(store.weeklyPlayers, name, score, now);
            changed = true;
        }
        if (!changed) return false;

        store.recordedMatchIds.add(matchId);
        persist();
        return true;
    }

    private void applyScore(Map<String, PlayerRecord> ledger, String name, int score, long now) {
        PlayerRecord record = ledger.computeIfAbsent(key(name), ignored -> new PlayerRecord());
        record.nickname = name;
        record.games += 1;
        record.totalScore += score;
        record.lastScore = score;
        record.lastPlayedAt = now;
        if (score > record.best) {
            record.best = score;
            record.bestAt = now;
        }
    }

    /** 누적 최고 점수 내림차순 상위 목록. rank는 1부터. */
    public synchronized List<Map<String, Object>> leaderboard(int limit) {
        return topOf(store.players, limit);
    }

    /**
     * 이번 주 최고 점수 순위 — 월요일이 지나면 비워진다.
     *
     * 아무도 플레이하지 않은 채 주가 바뀌었을 수도 있으므로 조회할 때도 주를 확인한다.
     * (여기서는 파일에 쓰지 않고, 다음 기록이 저장될 때 함께 반영된다)
     */
    public synchronized List<Map<String, Object>> weeklyLeaderboard(int limit) {
        rollWeekIfNeeded(System.currentTimeMillis());
        return topOf(store.weeklyPlayers, limit);
    }

    /** 이번 주가 시작된 날(월요일) — "2026-08-03" */
    public synchronized String currentWeekStart() {
        return weekKeyOf(System.currentTimeMillis());
    }

    private List<Map<String, Object>> topOf(Map<String, PlayerRecord> ledger, int limit) {
        int size = limit <= 0 ? 20 : Math.min(100, limit);
        List<PlayerRecord> sorted = sortedRecords(ledger);
        List<Map<String, Object>> result = new ArrayList<>();
        for (int index = 0; index < sorted.size() && index < size; index += 1) {
            result.add(toPublicRecord(sorted.get(index), index + 1));
        }
        return result;
    }

    /** 저장된 주가 지났으면 주간 장부를 비운다 (월요일 초기화) */
    private void rollWeekIfNeeded(long now) {
        String current = weekKeyOf(now);
        if (current.equals(store.weekKey)) return;
        store.weekKey = current;
        store.weeklyPlayers.clear();
    }

    /** 그 시각이 속한 주의 월요일 날짜를 키로 쓴다 — 월요일이 되면 값이 바뀌므로 자동 초기화된다. */
    private String weekKeyOf(long epochMilli) {
        return Instant.ofEpochMilli(epochMilli)
                .atZone(ZoneId.systemDefault())
                .toLocalDate()
                .with(DayOfWeek.MONDAY)
                .toString();
    }

    /** 방 안에서 참가자들의 기록을 함께 보여주기 위한 조회 */
    public synchronized Map<String, Map<String, Object>> recordsFor(Collection<String> nicknames) {
        Map<String, Map<String, Object>> result = new LinkedHashMap<>();
        if (nicknames == null) return result;
        List<PlayerRecord> sorted = sortedRecords();
        for (String nickname : nicknames) {
            String name = displayName(nickname);
            PlayerRecord record = store.players.get(key(name));
            int rank = 0;
            for (int index = 0; index < sorted.size(); index += 1) {
                if (sorted.get(index) == record) { rank = index + 1; break; }
            }
            result.put(name, record == null ? emptyRecord(name) : toPublicRecord(record, rank));
        }
        return result;
    }

    private List<PlayerRecord> sortedRecords() {
        return sortedRecords(store.players);
    }

    private List<PlayerRecord> sortedRecords(Map<String, PlayerRecord> ledger) {
        return ledger.values().stream()
                .sorted(Comparator.comparingInt((PlayerRecord record) -> -record.best)
                        .thenComparingLong(record -> record.bestAt == 0 ? Long.MAX_VALUE : record.bestAt)
                        .thenComparing(record -> record.nickname, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private Map<String, Object> toPublicRecord(PlayerRecord record, int rank) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("nickname", record.nickname);
        result.put("rank", rank);
        result.put("best", record.best);
        result.put("games", record.games);
        result.put("lastScore", record.lastScore);
        result.put("average", record.games == 0 ? 0 : Math.round((double) record.totalScore / record.games));
        result.put("lastPlayedAt", record.lastPlayedAt);
        return result;
    }

    private Map<String, Object> emptyRecord(String nickname) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("nickname", nickname);
        result.put("rank", 0);
        result.put("best", 0);
        result.put("games", 0);
        result.put("lastScore", 0);
        result.put("average", 0);
        result.put("lastPlayedAt", 0L);
        return result;
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
            if (loaded == null) return new RecordStore();
            // 주간 필드가 없던 시절의 파일도 그대로 읽을 수 있어야 한다
            if (loaded.recordedMatchIds == null) loaded.recordedMatchIds = new LinkedHashSet<>();
            if (loaded.players == null) loaded.players = new LinkedHashMap<>();
            if (loaded.weeklyPlayers == null) loaded.weeklyPlayers = new LinkedHashMap<>();
            if (loaded.weekKey == null) loaded.weekKey = "";
            return loaded;
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
            throw new IllegalStateException("Failed to save APPLE_BOX records.", exception);
        }
    }

    public static class RecordStore {
        public Set<String> recordedMatchIds = new LinkedHashSet<>();
        public Map<String, PlayerRecord> players = new LinkedHashMap<>();
        /** 주간 랭킹이 속한 주의 월요일 날짜. 이 값이 바뀌면 weeklyPlayers를 비운다. */
        public String weekKey = "";
        /** 누적과 별도로 관리하는 이번 주 장부 */
        public Map<String, PlayerRecord> weeklyPlayers = new LinkedHashMap<>();
    }

    public static class PlayerRecord {
        public String nickname = "";
        public int best;
        public int games;
        public int totalScore;
        public int lastScore;
        public long bestAt;
        public long lastPlayedAt;
    }
}
