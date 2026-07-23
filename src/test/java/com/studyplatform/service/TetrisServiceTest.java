package com.studyplatform.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.studyplatform.dto.request.StudyMoveRequest;
import com.studyplatform.model.Player;
import com.studyplatform.model.Room;
import com.studyplatform.model.StudyStatus;
import com.studyplatform.model.StudyType;
import com.studyplatform.model.tetris.TetrisGame;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TetrisServiceTest {
    @TempDir
    Path tempDir;
    private TetrisService service;
    private TetrisRecordService recordService;

    @BeforeEach
    void setUp() {
        recordService = new TetrisRecordService(
                new ObjectMapper(),
                tempDir.resolve("tetris-records.json")
        );
        service = new TetrisService(recordService);
    }

    @Test
    void syncProcessesEveryQueuedAttackInOrderAndIgnoresDuplicates() {
        Room room = playingRoom(2);
        TetrisGame game = (TetrisGame) room.getGameData();
        List<Map<String, Object>> events = List.of(
                attack("attack-1", 2, 1),
                attack("attack-2", 3, 3),
                attack("attack-3", 4, 6)
        );

        service.processMove(room, room.getPlayers().get(0),
                syncRequest(room, Map.of("attackEvents", events)));

        assertThat(game.getGarbageQueues().get(1))
                .extracting(entry -> entry.get("lines"))
                .containsExactly(1, 3, 6);
        assertThat(game.getComboCounts().get(0)).isEqualTo(3);

        service.processMove(room, room.getPlayers().get(0),
                syncRequest(room, Map.of("attackEvents", events)));

        assertThat(game.getGarbageQueues().get(1)).hasSize(3);
    }

    @Test
    void acknowledgedGarbageIsRemovedFromTargetQueue() {
        Room room = playingRoom(2);
        TetrisGame game = (TetrisGame) room.getGameData();
        service.processMove(room, room.getPlayers().get(0),
                syncRequest(room, Map.of("attackEvents", List.of(attack("attack-1", 4, 4)))));
        String attackId = (String) game.getGarbageQueues().get(1).get(0).get("attackId");

        service.processMove(room, room.getPlayers().get(1),
                syncRequest(room, Map.of("ackAttackIds", List.of(attackId), "attackEvents", List.of())));

        assertThat(game.getGarbageQueues().get(1)).isEmpty();
    }

    @Test
    void serverCapsClaimedAttackAndCalculatesSpecialBonuses() {
        Room room = playingRoom(2);
        TetrisGame game = (TetrisGame) room.getGameData();
        Map<String, Object> specialAttack = Map.of(
                "attackKey", "special-attack",
                "lastCleared", 2,
                "attackLines", 99,
                "tspin", true,
                "b2b", true,
                "perfectClear", true
        );

        service.processMove(room, room.getPlayers().get(0),
                syncRequest(room, Map.of("attackEvents", List.of(specialAttack))));

        assertThat(game.getGarbageQueues().get(1).get(0).get("lines")).isEqualTo(11);
    }

    @Test
    void lastSurvivingPlayerWins() {
        Room room = playingRoom(3);

        service.processMove(room, room.getPlayers().get(1),
                syncRequest(room, Map.of("gameOver", true)));
        assertThat(room.getStatus()).isEqualTo(StudyStatus.PLAYING);

        service.processMove(room, room.getPlayers().get(2),
                syncRequest(room, Map.of("gameOver", true)));

        TetrisGame game = (TetrisGame) room.getGameData();
        assertThat(room.getStatus()).isEqualTo(StudyStatus.FINISHED);
        assertThat(game.getWinner()).isZero();
        assertThat(recordService.recordsFor(List.of("player-0")).get("player-0"))
                .containsEntry("wins", 1)
                .containsEntry("matches", 1);

        service.processMove(room, room.getPlayers().get(2),
                syncRequest(room, Map.of("gameOver", true)));

        assertThat(recordService.recordsFor(List.of("player-0")).get("player-0"))
                .containsEntry("wins", 1)
                .containsEntry("matches", 1);
    }

    @Test
    void abortedMatchDoesNotSelectWinnerOrSaveRecords() {
        Room room = playingRoom(2);
        TetrisGame game = (TetrisGame) room.getGameData();
        game.setAborted(true);
        game.setAbortReason("player left");

        service.processMove(room, room.getPlayers().get(1),
                syncRequest(room, Map.of("gameOver", true)));

        assertThat(game.getWinner()).isEqualTo(-1);
        assertThat(recordService.recordsFor(List.of("player-0")).get("player-0"))
                .containsEntry("matches", 0);
    }

    @Test
    void staleGameOverSyncFromPreviousInstanceIsIgnoredAfterRestart() {
        Room room = playingRoom(2);
        String previousInstanceId = ((TetrisGame) room.getGameData()).getInstanceId();
        TetrisGame restartedGame = new TetrisGame(2);
        room.setGameData(restartedGame);

        service.processMove(
                room,
                room.getPlayers().get(1),
                request("TETRIS_SYNC", Map.of(
                        "instanceId", previousInstanceId,
                        "gameOver", true
                ))
        );

        assertThat(room.getStatus()).isEqualTo(StudyStatus.PLAYING);
        assertThat(restartedGame.getPlayerStates().get(1).isGameOver()).isFalse();
        assertThat(restartedGame.getWinner()).isEqualTo(-1);
        assertThat(recordService.recordsFor(List.of("player-0")).get("player-0"))
                .containsEntry("matches", 0);
    }

    @Test
    void onlyHostCanPauseTheWholeGame() {
        Room room = playingRoom(2);

        assertThatThrownBy(() -> service.processMove(
                room,
                room.getPlayers().get(1),
                request("TETRIS_PAUSE", Map.of("paused", true))
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Only the host can pause TETRIS.");

        service.processMove(
                room,
                room.getPlayers().get(0),
                request("TETRIS_PAUSE", Map.of("paused", true))
        );

        assertThat(((TetrisGame) room.getGameData()).isPaused()).isTrue();
    }

    private Room playingRoom(int playerCount) {
        Room room = new Room("tetris-test", StudyType.TETRIS);
        for (int index = 0; index < playerCount; index += 1) {
            room.getPlayers().add(new Player("session-" + index, "player-" + index, index));
        }
        room.setGameData(new TetrisGame(playerCount));
        room.setStatus(StudyStatus.PLAYING);
        return room;
    }

    private StudyMoveRequest syncRequest(Room room, Map<String, Object> payload) {
        Map<String, Object> syncPayload = new java.util.HashMap<>(payload);
        syncPayload.put("instanceId", ((TetrisGame) room.getGameData()).getInstanceId());
        return request("TETRIS_SYNC", syncPayload);
    }

    private StudyMoveRequest request(String moveType, Map<String, Object> payload) {
        StudyMoveRequest request = new StudyMoveRequest();
        request.setMoveType(moveType);
        request.setSessionId("test-session");
        request.setPayload(payload);
        return request;
    }

    private Map<String, Object> attack(String key, int cleared, int claimedLines) {
        return Map.of(
                "attackKey", key,
                "lastCleared", cleared,
                "attackLines", claimedLines,
                "tspin", false,
                "b2b", false,
                "perfectClear", false
        );
    }
}
