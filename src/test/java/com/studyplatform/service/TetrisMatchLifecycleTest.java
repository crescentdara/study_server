package com.studyplatform.service;

import com.studyplatform.dto.response.RoomResponse;
import com.studyplatform.model.Room;
import com.studyplatform.model.StudyStatus;
import com.studyplatform.model.StudyType;
import com.studyplatform.model.tetris.TetrisGame;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TetrisMatchLifecycleTest {
    @Test
    void tetrisAllowsSoloPracticeAndCapsRoomAtThree() {
        RoomService rooms = new RoomService();
        Room practice = rooms.createRoom("practice", StudyType.TETRIS, "Host", "host", 9, 0, 20, "");

        assertThat(practice.getMaxPlayers()).isEqualTo(3);
        assertThat(RoomResponse.from(practice).getMaxPlayers()).isEqualTo(3);
        rooms.startGame(practice);
        assertThat(practice.getStatus()).isEqualTo(StudyStatus.PLAYING);
        assertThat(((TetrisGame) practice.getGameData()).getNumPlayers()).isEqualTo(1);

        Room room = rooms.createRoom("ranked", StudyType.TETRIS, "Host", "ranked-host", 2, 0, 20, "");
        rooms.joinRoom(room.getRoomId(), "Guest 1", "guest-1");
        rooms.joinRoom(room.getRoomId(), "Guest 2", "guest-2");
        assertThatThrownBy(() -> rooms.joinRoom(room.getRoomId(), "Guest 3", "guest-3"))
                .hasMessage("Room is full.");
    }

    /** 서바이벌은 혼자도, 여럿이 겨루기도 된다 — 방을 만들 때 고른 정원(1~3)을 지킨다. */
    @Test
    void survivalRoomKeepsChosenCapacity() {
        RoomService rooms = new RoomService();

        Room solo = rooms.createRoom("solo", StudyType.TETRIS, "Host", "host", 1, 0, 20, "SURVIVAL");
        assertThat(solo.getMode()).isEqualTo("SURVIVAL");
        assertThat(solo.getMaxPlayers()).isEqualTo(1);
        assertThat(RoomResponse.from(solo).getMode()).isEqualTo("SURVIVAL");
        assertThat(RoomService.isSurvival(solo)).isTrue();
        assertThatThrownBy(() -> rooms.joinRoom(solo.getRoomId(), "Guest", "guest"))
                .hasMessage("Room is full.");
        rooms.startGame(solo);
        assertThat(solo.getStatus()).isEqualTo(StudyStatus.PLAYING);
        assertThat(((TetrisGame) solo.getGameData()).getNumPlayers()).isEqualTo(1);

        // 여럿이 겨루는 서바이벌 — 입장하면서 정원이 3으로 늘어나지 않아야 한다
        Room duo = rooms.createRoom("duo", StudyType.TETRIS, "Host", "duo-host", 2, 0, 20, "SURVIVAL");
        assertThat(duo.getMaxPlayers()).isEqualTo(2);
        rooms.joinRoom(duo.getRoomId(), "Guest", "duo-guest");
        assertThat(duo.getMaxPlayers()).isEqualTo(2);
        assertThatThrownBy(() -> rooms.joinRoom(duo.getRoomId(), "Third", "duo-third"))
                .hasMessage("Room is full.");
    }

    /** 모든 참가자는 같은 구멍 순서를 받아야 공정한 경기가 된다. */
    @Test
    void survivalGameSharesOneGarbageHoleSequence() {
        RoomService rooms = new RoomService();
        Room room = rooms.createRoom("survival", StudyType.TETRIS, "Host", "host", 2, 0, 20, "SURVIVAL");
        rooms.joinRoom(room.getRoomId(), "Guest", "guest");
        rooms.startGame(room);

        TetrisGame game = (TetrisGame) room.getGameData();
        assertThat(game.getGarbageHoles()).hasSize(400);
        // 같은 열이 연달아 나오면 세로 통로가 생긴다
        for (int index = 1; index < game.getGarbageHoles().size(); index += 1) {
            assertThat(game.getGarbageHoles().get(index))
                    .isNotEqualTo(game.getGarbageHoles().get(index - 1));
        }
        assertThat(game.survivalElapsedMs()).isLessThan(5_000);
    }

    @Test
    void leavingDuringMatchMarksItAborted() {
        RoomService rooms = new RoomService();
        Room room = roomWithTwoPlayers(rooms);
        rooms.startGame(room);

        Room updated = rooms.leaveRoom(room.getRoomId(), "guest-session");

        TetrisGame game = (TetrisGame) updated.getGameData();
        assertThat(updated.getStatus()).isEqualTo(StudyStatus.FINISHED);
        assertThat(game.isAborted()).isTrue();
        assertThat(game.getAbortReason()).contains("Guest");
        assertThat(game.isRecordSaved()).isFalse();
    }

    @Test
    void restartingUnfinishedMatchCarriesAbortNoticeIntoNewGame() {
        RoomService rooms = new RoomService();
        Room room = roomWithTwoPlayers(rooms);
        rooms.startGame(room);
        String previousInstance = ((TetrisGame) room.getGameData()).getInstanceId();

        rooms.restartGame(room);

        TetrisGame nextGame = (TetrisGame) room.getGameData();
        assertThat(room.getStatus()).isEqualTo(StudyStatus.PLAYING);
        assertThat(nextGame.getInstanceId()).isNotEqualTo(previousInstance);
        assertThat(nextGame.getPreviousAbortReason()).contains("restarted");
    }

    private Room roomWithTwoPlayers(RoomService rooms) {
        Room room = rooms.createRoom(
                "records-test",
                StudyType.TETRIS,
                "Host",
                "host-session",
                4,
                0,
                20,
                ""
        );
        rooms.joinRoom(room.getRoomId(), "Guest", "guest-session");
        return room;
    }
}
