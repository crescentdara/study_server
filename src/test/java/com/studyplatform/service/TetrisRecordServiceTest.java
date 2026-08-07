package com.studyplatform.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TetrisRecordServiceTest {
    @TempDir
    Path tempDir;

    @Test
    void completedMultiplayerMatchPersistsOverallAndHeadToHeadRecords() {
        Path recordPath = tempDir.resolve("records.json");
        TetrisRecordService service = new TetrisRecordService(new ObjectMapper(), recordPath);

        assertThat(service.recordCompletedMatch(
                "match-1",
                List.of("철수", "영희", "민수")
        )).isTrue();

        TetrisRecordService reloaded = new TetrisRecordService(new ObjectMapper(), recordPath);
        Map<String, Map<String, Object>> records = reloaded.recordsFor(List.of("철수", "영희", "민수"));

        assertThat(records.get("철수"))
                .containsEntry("matches", 1)
                .containsEntry("wins", 1)
                .containsEntry("losses", 0);
        assertThat(records.get("영희"))
                .containsEntry("matches", 1)
                .containsEntry("wins", 0)
                .containsEntry("losses", 1);
        assertThat(records.get("민수"))
                .containsEntry("matches", 1)
                .containsEntry("wins", 0)
                .containsEntry("losses", 1);

        @SuppressWarnings("unchecked")
        Map<String, Object> ironOpponents = (Map<String, Object>) records.get("철수").get("opponents");
        assertThat(ironOpponents).containsKeys("영희", "민수");
        @SuppressWarnings("unchecked")
        Map<String, Object> youngheeOpponents = (Map<String, Object>) records.get("영희").get("opponents");
        assertThat(youngheeOpponents.get("민수")).isEqualTo(Map.of("wins", 1, "losses", 0));
    }

    @Test
    void duplicateMatchIdIsNotRecordedTwice() {
        TetrisRecordService service = new TetrisRecordService(
                new ObjectMapper(),
                tempDir.resolve("records.json")
        );

        assertThat(service.recordCompletedMatch("same-match", List.of("A", "B"))).isTrue();
        assertThat(service.recordCompletedMatch("same-match", List.of("A", "B"))).isFalse();

        assertThat(service.recordsFor(List.of("A")).get("A"))
                .containsEntry("matches", 1)
                .containsEntry("wins", 1);
    }

    @Test
    void lossesAreDiscountedToSeventyPercentOfEquivalentWins() {
        TetrisRecordService placement = new TetrisRecordService(
                new ObjectMapper(),
                tempDir.resolve("loss-discount-placement.json")
        );
        placement.recordCompletedMatch("placement-equal", List.of("A", "B"));
        Map<String, Map<String, Object>> placementRecords = placement.recordsFor(List.of("A", "B"));
        assertThat(placementRecords.get("A")).containsEntry("lastRankDelta", 40);
        assertThat(placementRecords.get("B")).containsEntry("lastRankDelta", -28);

        Path rankedPath = tempDir.resolve("loss-discount-ranked.json");
        TetrisRecordService.RecordStore store = new TetrisRecordService.RecordStore();
        for (String nickname : List.of("A", "B")) {
            TetrisRecordService.PlayerRecord player = new TetrisRecordService.PlayerRecord();
            player.nickname = nickname;
            player.rating = 1_000;
            player.placementGames = 5;
            store.players.put(nickname.toLowerCase(), player);
        }
        try {
            new ObjectMapper().writeValue(rankedPath.toFile(), store);
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
        TetrisRecordService ranked = new TetrisRecordService(new ObjectMapper(), rankedPath);
        ranked.recordCompletedMatch("ranked-equal", List.of("A", "B"));
        Map<String, Map<String, Object>> rankedRecords = ranked.recordsFor(List.of("A", "B"));
        assertThat(rankedRecords.get("A")).containsEntry("lastRankDelta", 16);
        assertThat(rankedRecords.get("B")).containsEntry("lastRankDelta", -11);
    }

    @Test
    void fivePlacementMatchesRevealTierAndPromotionState() {
        TetrisRecordService service = new TetrisRecordService(
                new ObjectMapper(),
                tempDir.resolve("ranked-records.json")
        );

        for (int match = 1; match <= 5; match += 1) {
            assertThat(service.recordCompletedMatch("placement-" + match, List.of("A", "B"))).isTrue();
        }

        Map<String, Object> winner = service.recordsFor(List.of("A")).get("A");
        Map<String, Object> loser = service.recordsFor(List.of("B")).get("B");

        assertThat(winner)
                .containsEntry("placementGames", 5)
                .containsEntry("placementRequired", 5)
                .containsEntry("ranked", true)
                .containsEntry("tier", "SILVER")
                .containsEntry("division", "III")
                .containsEntry("lastRankBefore", "UNRANKED")
                .containsEntry("lastRankAfter", "SILVER III")
                .containsEntry("lastRankChanged", true);
        assertThat((Integer) winner.get("rp")).isBetween(0, 99);
        assertThat((Integer) winner.get("lastRankDelta")).isPositive();

        assertThat(loser)
                .containsEntry("ranked", true)
                .containsEntry("tier", "BRONZE");
        assertThat((Integer) loser.get("lastRankDelta")).isNegative();
    }

    @Test
    void rankedLossRemovesRpAndMultiplayerSecondPlaceCanGainRp() {
        TetrisRecordService duel = new TetrisRecordService(
                new ObjectMapper(),
                tempDir.resolve("duel-records.json")
        );
        for (int match = 1; match <= 5; match += 1) {
            duel.recordCompletedMatch("seed-" + match, List.of("A", "B"));
        }
        duel.recordCompletedMatch("upset", List.of("B", "A"));
        assertThat((Integer) duel.recordsFor(List.of("A")).get("A").get("lastRankDelta")).isNegative();
        assertThat((Integer) duel.recordsFor(List.of("B")).get("B").get("lastRankDelta")).isPositive();

        TetrisRecordService multiplayer = new TetrisRecordService(
                new ObjectMapper(),
                tempDir.resolve("multiplayer-rank.json")
        );
        multiplayer.recordCompletedMatch("four-player", List.of("A", "B", "C", "D"));
        assertThat((Integer) multiplayer.recordsFor(List.of("B")).get("B").get("lastRankDelta")).isPositive();
        assertThat((Integer) multiplayer.recordsFor(List.of("C")).get("C").get("lastRankDelta")).isNegative();
    }

    /** 로비 목록은 배치를 마친 사람이 먼저, 같은 그룹에서는 레이팅 순으로 나온다. */
    @Test
    void leaderboardPutsRankedPlayersFirstAndOrdersByRating() {
        TetrisRecordService service = new TetrisRecordService(
                new ObjectMapper(),
                tempDir.resolve("leaderboard-records.json")
        );

        // A는 5판을 이겨 배치를 끝내고, C·D는 한 판만 해서 배치 중이다
        for (int match = 1; match <= 5; match += 1) {
            service.recordCompletedMatch("placement-" + match, List.of("A", "B"));
        }
        service.recordCompletedMatch("rookies", List.of("C", "D"));

        List<Map<String, Object>> board = service.leaderboard(10);

        assertThat(board).hasSize(4);
        assertThat(board.get(0))
                .containsEntry("nickname", "A")
                .containsEntry("rank", 1)
                .containsEntry("ranked", true)
                .containsEntry("wins", 5)
                .containsEntry("losses", 0)
                .containsEntry("winRate", 100L)
                .containsEntry("tier", "SILVER");
        assertThat(board.get(1))
                .containsEntry("nickname", "B")
                .containsEntry("rank", 2)
                .containsEntry("ranked", true);
        // 배치 중인 사람은 레이팅과 무관하게 뒤로 간다
        assertThat(board.get(2)).containsEntry("ranked", false).containsEntry("placementGames", 1);
        assertThat(board.get(3)).containsEntry("ranked", false);
        // 상대전적은 목록에서 덜어낸다
        assertThat(board.get(0)).doesNotContainKey("opponents");
    }

    @Test
    void leaderboardHonoursLimitAndSkipsPlayersWithoutMatches() {
        TetrisRecordService service = new TetrisRecordService(
                new ObjectMapper(),
                tempDir.resolve("leaderboard-limit-records.json")
        );
        service.recordCompletedMatch("m1", List.of("A", "B", "C"));

        assertThat(service.leaderboard(2)).hasSize(2);
        // 이름만 조회한 적 있는 사람은 목록에 오르지 않는다
        service.recordsFor(List.of("구경꾼"));
        assertThat(service.leaderboard(10)).hasSize(3);
    }

    @Test
    void challengerIsTheTopRankAboveGrandmaster() throws Exception {
        Path recordPath = tempDir.resolve("challenger-records.json");
        TetrisRecordService.RecordStore store = new TetrisRecordService.RecordStore();
        TetrisRecordService.PlayerRecord player = new TetrisRecordService.PlayerRecord();
        player.nickname = "최강자";
        player.rating = 3600;
        player.placementGames = 5;
        store.players.put("최강자", player);
        new ObjectMapper().writeValue(recordPath.toFile(), store);

        Map<String, Object> record = new TetrisRecordService(new ObjectMapper(), recordPath)
                .recordsFor(List.of("최강자"))
                .get("최강자");

        assertThat(record)
                .containsEntry("tier", "CHALLENGER")
                .containsEntry("division", "")
                .containsEntry("rp", 800);
    }
}
