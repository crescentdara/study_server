package com.studyplatform.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AppleSoloServiceTest {
    @TempDir
    Path tempDir;
    private AppleSoloService service;
    private AppleBoxRecordService recordService;

    @BeforeEach
    void setUp() {
        recordService = new AppleBoxRecordService(
                new ObjectMapper(),
                tempDir.resolve("applebox-records.json")
        );
        service = new AppleSoloService(recordService);
    }

    /** 시작을 누른 순간이 곧 판의 시작 — 대기 상태가 없으므로 시간이 그대로 남아 있어야 한다. */
    @Test
    void startBeginsImmediatelyWithTheFullTimeLimit() {
        Map<String, Object> started = service.start("철수");
        Map<String, Object> gameData = gameData(started);

        assertThat(started.get("finished")).isEqualTo(false);
        assertThat(started.get("score")).isEqualTo(0);
        assertThat(gameData.get("remainingSeconds")).isEqualTo(gameData.get("durationSeconds"));
        assertThat(gameData.get("numPlayers")).isEqualTo(1);
        assertThat(recordService.leaderboard(10)).isEmpty();
    }

    @Test
    void clearAddsScoreAndFinishSavesItToTheSharedLeaderboard() {
        Map<String, Object> started = service.start("철수");
        String instanceId = (String) started.get("instanceId");
        fixCells(started, 4, 6);

        Map<String, Object> afterClear = service.clear(instanceId, 0, 0, 0, 1);
        assertThat(afterClear.get("score")).isEqualTo(2);

        Map<String, Object> finished = service.finish(instanceId);
        assertThat(finished.get("finished")).isEqualTo(true);

        assertThat(recordService.leaderboard(10))
                .singleElement()
                .satisfies(record -> {
                    assertThat(record).containsEntry("nickname", "철수");
                    assertThat(record).containsEntry("best", 2);
                    assertThat(record).containsEntry("games", 1);
                    assertThat(record).containsEntry("rank", 1);
                });
    }

    @Test
    void wrongSumIsIgnored() {
        Map<String, Object> started = service.start("철수");
        String instanceId = (String) started.get("instanceId");
        fixCells(started, 4, 5); // 합 9

        assertThat(service.clear(instanceId, 0, 0, 0, 1).get("score")).isEqualTo(0);
    }

    @Test
    void finishingTwiceDoesNotCountTheSameRoundTwice() {
        Map<String, Object> started = service.start("철수");
        String instanceId = (String) started.get("instanceId");
        fixCells(started, 4, 6);
        service.clear(instanceId, 0, 0, 0, 1);

        service.finish(instanceId);
        service.finish(instanceId);

        assertThat(recordService.leaderboard(10))
                .singleElement()
                .satisfies(record -> assertThat(record).containsEntry("games", 1));
    }

    /** 개인 점수는 판마다 따로지만, 랭킹에는 그 사람의 최고 점수가 남는다. */
    @Test
    void personalBestIsWhatTheSharedRankingKeeps() {
        Map<String, Object> first = service.start("철수");
        String firstId = (String) first.get("instanceId");
        fixCells(first, 1, 2, 7); // 1+2+7 = 10 → 3칸
        service.clear(firstId, 0, 0, 0, 2);
        service.finish(firstId);

        Map<String, Object> second = service.start("철수");
        String secondId = (String) second.get("instanceId");
        fixCells(second, 4, 6);
        service.clear(secondId, 0, 0, 0, 1); // 2칸 — 최고 점수보다 낮다
        service.finish(secondId);

        assertThat(recordService.leaderboard(10))
                .singleElement()
                .satisfies(record -> {
                    assertThat(record).containsEntry("best", 3);
                    assertThat(record).containsEntry("lastScore", 2);
                    assertThat(record).containsEntry("games", 2);
                });
    }

    @Test
    void unknownSessionIsRejected() {
        assertThatThrownBy(() -> service.clear("no-such-session", 0, 0, 0, 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.finish(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> gameData(Map<String, Object> snapshot) {
        return (Map<String, Object>) snapshot.get("gameData");
    }

    /**
     * 첫 행 앞쪽 칸 값을 고정한다.
     *
     * 보드는 무작위로 채워지므로 합이 10인 범위를 테스트에서 미리 알 수 없다.
     * 응답에 담긴 board가 게임이 쓰는 배열 그대로이므로 여기서 값을 정해 준다.
     */
    private void fixCells(Map<String, Object> snapshot, int... values) {
        int[] board = (int[]) gameData(snapshot).get("board");
        for (int index = 0; index < values.length; index += 1) {
            board[index] = values[index];
        }
    }
}
