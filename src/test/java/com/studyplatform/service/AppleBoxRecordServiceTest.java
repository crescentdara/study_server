package com.studyplatform.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.studyplatform.service.AppleBoxRecordService.RecordStore;

import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AppleBoxRecordServiceTest {
    @TempDir
    Path tempDir;

    private Map<String, Integer> scores(String nickname, int score) {
        Map<String, Integer> scores = new LinkedHashMap<>();
        scores.put(nickname, score);
        return scores;
    }

    @Test
    void bestScoreAndAverageSurviveRestart() {
        Path recordPath = tempDir.resolve("applebox-records.json");
        AppleBoxRecordService service = new AppleBoxRecordService(new ObjectMapper(), recordPath);

        Map<String, Integer> firstMatch = new LinkedHashMap<>();
        firstMatch.put("철수", 40);
        firstMatch.put("영희", 20);
        assertThat(service.recordScores("match-1", firstMatch)).isTrue();
        assertThat(service.recordScores("match-2", scores("철수", 60))).isTrue();

        // 파일에서 다시 읽어도 같은 값이어야 한다
        AppleBoxRecordService reloaded = new AppleBoxRecordService(new ObjectMapper(), recordPath);
        Map<String, Map<String, Object>> records = reloaded.recordsFor(List.of("철수", "영희"));

        assertThat(records.get("철수"))
                .containsEntry("best", 60)
                .containsEntry("games", 2)
                .containsEntry("lastScore", 60)
                .containsEntry("average", 50L)
                .containsEntry("rank", 1);
        assertThat(records.get("영희"))
                .containsEntry("best", 20)
                .containsEntry("games", 1)
                .containsEntry("rank", 2);
    }

    @Test
    void lowerScoreDoesNotOverwriteBest() {
        AppleBoxRecordService service = new AppleBoxRecordService(
                new ObjectMapper(),
                tempDir.resolve("best-records.json")
        );

        service.recordScores("high", scores("철수", 80));
        service.recordScores("low", scores("철수", 30));

        assertThat(service.recordsFor(List.of("철수")).get("철수"))
                .containsEntry("best", 80)
                .containsEntry("lastScore", 30)
                .containsEntry("games", 2);
    }

    @Test
    void duplicateMatchIdIsNotRecordedTwice() {
        AppleBoxRecordService service = new AppleBoxRecordService(
                new ObjectMapper(),
                tempDir.resolve("duplicate-records.json")
        );

        assertThat(service.recordScores("same-match", scores("철수", 50))).isTrue();
        assertThat(service.recordScores("same-match", scores("철수", 50))).isFalse();

        assertThat(service.recordsFor(List.of("철수")).get("철수"))
                .containsEntry("games", 1)
                .containsEntry("best", 50);
    }

    @Test
    void leaderboardRanksByBestScoreAndHonoursLimit() {
        AppleBoxRecordService service = new AppleBoxRecordService(
                new ObjectMapper(),
                tempDir.resolve("leaderboard-records.json")
        );

        service.recordScores("m1", scores("철수", 30));
        service.recordScores("m2", scores("영희", 90));
        service.recordScores("m3", scores("민수", 60));

        List<Map<String, Object>> leaderboard = service.leaderboard(10);
        assertThat(leaderboard).hasSize(3);
        assertThat(leaderboard.get(0)).containsEntry("nickname", "영희").containsEntry("rank", 1);
        assertThat(leaderboard.get(1)).containsEntry("nickname", "민수").containsEntry("rank", 2);
        assertThat(leaderboard.get(2)).containsEntry("nickname", "철수").containsEntry("rank", 3);

        assertThat(service.leaderboard(1)).hasSize(1);
    }

    @Test
    void blankNicknameAndEmptyScoresAreIgnored() {
        AppleBoxRecordService service = new AppleBoxRecordService(
                new ObjectMapper(),
                tempDir.resolve("blank-records.json")
        );

        assertThat(service.recordScores("blank", scores("   ", 50))).isFalse();
        assertThat(service.recordScores("empty", Map.of())).isFalse();
        assertThat(service.recordScores(null, scores("철수", 50))).isFalse();
        assertThat(service.leaderboard(10)).isEmpty();
    }

    /**
     * 예전 규칙으로 쌓인 기존 파일 위에 이어서 누적된다.
     *
     * 예전에는 0점으로 끝난 판도 판수에 넣었기 때문에 기존 값에는 그 판들이 섞여 있다.
     * 판별 기록이 남아 있지 않아 소급해서 걷어낼 수는 없으므로, 기존 값은 그대로 두고
     * 새 판만 새 규칙(점수 > 0)으로 더한다.
     */
    @Test
    void newRoundsAccumulateOnTopOfExistingRecords() throws Exception {
        Path recordPath = tempDir.resolve("legacy-records.json");
        RecordStore stored = new RecordStore();
        AppleBoxRecordService.PlayerRecord legacy = new AppleBoxRecordService.PlayerRecord();
        legacy.nickname = "거북이";
        legacy.best = 68;
        legacy.games = 21;
        legacy.totalScore = 131;
        legacy.lastScore = 6;
        legacy.bestAt = 1_786_002_990_732L;
        legacy.lastPlayedAt = 1_786_057_864_262L;
        stored.players.put("거북이", legacy);
        new ObjectMapper().writeValue(recordPath.toFile(), stored);

        AppleBoxRecordService service = new AppleBoxRecordService(new ObjectMapper(), recordPath);

        // 0점 판은 기존 값을 건드리지 않는다
        assertThat(service.recordScores("zero-round", scores("거북이", 0))).isFalse();
        assertThat(service.recordsFor(List.of("거북이")).get("거북이"))
                .containsEntry("games", 21)
                .containsEntry("best", 68);

        // 점수를 낸 판은 기존 판수 위에 더해지고, 최고점도 갱신된다
        assertThat(service.recordScores("played-round", scores("거북이", 80))).isTrue();
        assertThat(service.recordsFor(List.of("거북이")).get("거북이"))
                .containsEntry("games", 22)
                .containsEntry("best", 80)
                .containsEntry("lastScore", 80);

        // 주간 장부는 예전 파일에 없던 것이라 이번 판부터 센다
        assertThat(service.weeklyLeaderboard(10))
                .singleElement()
                .satisfies(record -> assertThat(record).containsEntry("games", 1));
    }

    /** 주간 랭킹은 누적과 별개로 쌓인다. */
    @Test
    void weeklyRankingIsKeptSeparatelyFromTheAllTimeRanking() {
        AppleBoxRecordService service = new AppleBoxRecordService(
                new ObjectMapper(),
                tempDir.resolve("weekly-records.json")
        );

        service.recordScores("m1", scores("철수", 40));
        service.recordScores("m2", scores("영희", 70));

        assertThat(service.leaderboard(10)).hasSize(2);
        assertThat(service.weeklyLeaderboard(10))
                .hasSize(2)
                .satisfies(records -> {
                    assertThat(records.get(0)).containsEntry("nickname", "영희").containsEntry("best", 70);
                    assertThat(records.get(1)).containsEntry("nickname", "철수").containsEntry("best", 40);
                });
    }

    /** 월요일이 지나면 주간 랭킹만 비워지고 누적은 그대로 남는다. */
    @Test
    void weeklyRankingResetsOnMondayWhileAllTimeSurvives() throws Exception {
        Path recordPath = tempDir.resolve("week-roll-records.json");
        AppleBoxRecordService service = new AppleBoxRecordService(new ObjectMapper(), recordPath);
        service.recordScores("last-week", scores("철수", 55));

        assertThat(service.weeklyLeaderboard(10)).hasSize(1);

        // 지난주에 기록된 파일을 흉내 내어 주 키를 이전 주로 되돌린다
        RecordStore stored = new ObjectMapper().readValue(recordPath.toFile(), RecordStore.class);
        stored.weekKey = LocalDate.parse(service.currentWeekStart()).minusWeeks(1).toString();
        new ObjectMapper().writeValue(recordPath.toFile(), stored);

        AppleBoxRecordService afterMonday = new AppleBoxRecordService(new ObjectMapper(), recordPath);

        assertThat(afterMonday.weeklyLeaderboard(10)).isEmpty();
        assertThat(afterMonday.leaderboard(10))
                .singleElement()
                .satisfies(record -> assertThat(record).containsEntry("best", 55));

        // 새 주의 첫 기록부터 주간 순위가 다시 쌓인다
        afterMonday.recordScores("this-week", scores("철수", 20));
        assertThat(afterMonday.weeklyLeaderboard(10))
                .singleElement()
                .satisfies(record -> assertThat(record).containsEntry("best", 20));
        assertThat(afterMonday.leaderboard(10))
                .singleElement()
                .satisfies(record -> assertThat(record).containsEntry("best", 55));
    }

    /** 주 시작일은 항상 그 주의 월요일이다. */
    @Test
    void weekStartIsTheMondayOfTheCurrentWeek() {
        AppleBoxRecordService service = new AppleBoxRecordService(
                new ObjectMapper(),
                tempDir.resolve("week-start-records.json")
        );

        assertThat(LocalDate.parse(service.currentWeekStart()).getDayOfWeek())
                .isEqualTo(DayOfWeek.MONDAY);
    }

    @Test
    void nicknameWithoutHistoryReportsEmptyRecord() {
        AppleBoxRecordService service = new AppleBoxRecordService(
                new ObjectMapper(),
                tempDir.resolve("empty-records.json")
        );

        assertThat(service.recordsFor(List.of("처음온사람")).get("처음온사람"))
                .containsEntry("nickname", "처음온사람")
                .containsEntry("rank", 0)
                .containsEntry("best", 0)
                .containsEntry("games", 0)
                .containsEntry("lastPlayedAt", 0L);
    }
}
