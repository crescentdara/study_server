package com.studyplatform.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.studyplatform.dto.request.StudyMoveRequest;
import com.studyplatform.model.Player;
import com.studyplatform.model.Room;
import com.studyplatform.model.StudyStatus;
import com.studyplatform.model.StudyType;
import com.studyplatform.model.applebox.AppleBoxGame;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AppleBoxServiceTest {
    @TempDir
    Path tempDir;
    private AppleBoxService service;
    private AppleBoxRecordService recordService;

    @BeforeEach
    void setUp() {
        recordService = new AppleBoxRecordService(
                new ObjectMapper(),
                tempDir.resolve("applebox-records.json")
        );
        service = new AppleBoxService(recordService);
    }

    /**
     * 대기 중인 방은 제한 시간이 지나도 끝나지 않아야 한다.
     *
     * 이 검사가 없던 동안에는 방을 만들어 두고 제한 시간(기본 120초)이 지나면
     * 시작을 누르기 전에 방이 FINISHED로 넘어가고, 0점짜리 기록까지 저장됐다.
     */
    @Test
    void waitingRoomIsNotSettledEvenAfterTimeLimitPasses() throws Exception {
        Room room = room(1, StudyStatus.WAITING);
        expireClock((AppleBoxGame) room.getGameData());

        var state = service.buildInitialState(room);

        assertThat(room.getStatus()).isEqualTo(StudyStatus.WAITING);
        assertThat(state.getStatus()).isEqualTo(StudyStatus.WAITING);
        assertThat(recordService.leaderboard(10)).isEmpty();
        // 시작 전에는 시계가 흐르지 않으므로 남은 시간은 제한 시간 그대로다
        @SuppressWarnings("unchecked")
        Map<String, Object> gameData = (Map<String, Object>) state.getGameData();
        assertThat(gameData.get("remainingSeconds"))
                .isEqualTo(((AppleBoxGame) room.getGameData()).getDurationSeconds());
    }

    @Test
    void playingRoomFinishesAndSavesRecordWhenTimeIsUp() throws Exception {
        Room room = room(1, StudyStatus.PLAYING);
        AppleBoxGame game = (AppleBoxGame) room.getGameData();
        game.getPlayerStates().get(0).setScore(12);
        expireClock(game);

        service.buildInitialState(room);

        assertThat(room.getStatus()).isEqualTo(StudyStatus.FINISHED);
        assertThat(game.getFinalRanking()).containsExactly(0);
        assertThat(recordService.leaderboard(10))
                .singleElement()
                .satisfies(record -> {
                    assertThat(record).containsEntry("nickname", "player-0");
                    assertThat(record).containsEntry("best", 12);
                    assertThat(record).containsEntry("rank", 1);
                });
    }

    @Test
    void clearIsIgnoredBeforeStartAndCountsWhilePlaying() {
        Room room = room(1, StudyStatus.WAITING);
        AppleBoxGame game = (AppleBoxGame) room.getGameData();
        // A1 + B1 = 10 이 되도록 두 칸만 고정한다
        game.getBoard()[0] = 4;
        game.getBoard()[1] = 6;

        service.processMove(room, room.getPlayers().get(0), clearRequest(0, 0, 0, 1));
        assertThat(game.getPlayerStates().get(0).getScore()).isZero();

        room.setStatus(StudyStatus.PLAYING);
        service.processMove(room, room.getPlayers().get(0), clearRequest(0, 0, 0, 1));
        assertThat(game.getPlayerStates().get(0).getScore()).isEqualTo(2);
    }

    @Test
    void finishedRoomIgnoresFurtherMoves() {
        Room room = room(1, StudyStatus.PLAYING);
        AppleBoxGame game = (AppleBoxGame) room.getGameData();
        game.getBoard()[0] = 4;
        game.getBoard()[1] = 6;
        room.setStatus(StudyStatus.FINISHED);

        service.processMove(room, room.getPlayers().get(0), clearRequest(0, 0, 0, 1));

        assertThat(game.getPlayerStates().get(0).getScore()).isZero();
    }

    private Room room(int playerCount, StudyStatus status) {
        Room room = new Room("apple-test", StudyType.APPLE_BOX);
        for (int index = 0; index < playerCount; index += 1) {
            room.getPlayers().add(new Player("session-" + index, "player-" + index, index));
        }
        room.setGameData(new AppleBoxGame(playerCount));
        room.setStatus(status);
        return room;
    }

    private StudyMoveRequest clearRequest(int r1, int c1, int r2, int c2) {
        StudyMoveRequest request = new StudyMoveRequest();
        request.setMoveType("APPLE_CLEAR");
        request.setSessionId("session-0");
        request.setPayload(Map.of("r1", r1, "c1", c1, "r2", r2, "c2", c2));
        return request;
    }

    /** 제한 시간이 이미 지난 상황을 만든다 (startedAt은 final이라 테스트에서만 리플렉션으로 되돌린다) */
    private void expireClock(AppleBoxGame game) throws Exception {
        Field startedAt = AppleBoxGame.class.getDeclaredField("startedAt");
        startedAt.setAccessible(true);
        startedAt.setLong(game, System.currentTimeMillis() - (game.getDurationSeconds() + 5) * 1000L);
    }
}
