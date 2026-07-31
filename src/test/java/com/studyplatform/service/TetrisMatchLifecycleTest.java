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
        Room practice = rooms.createRoom("practice", StudyType.TETRIS, "Host", "host", 9, 0, 20);

        assertThat(practice.getMaxPlayers()).isEqualTo(3);
        assertThat(RoomResponse.from(practice).getMaxPlayers()).isEqualTo(3);
        rooms.startGame(practice);
        assertThat(practice.getStatus()).isEqualTo(StudyStatus.PLAYING);
        assertThat(((TetrisGame) practice.getGameData()).getNumPlayers()).isEqualTo(1);

        Room room = rooms.createRoom("ranked", StudyType.TETRIS, "Host", "ranked-host", 2, 0, 20);
        rooms.joinRoom(room.getRoomId(), "Guest 1", "guest-1");
        rooms.joinRoom(room.getRoomId(), "Guest 2", "guest-2");
        assertThatThrownBy(() -> rooms.joinRoom(room.getRoomId(), "Guest 3", "guest-3"))
                .hasMessage("Room is full.");
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
                20
        );
        rooms.joinRoom(room.getRoomId(), "Guest", "guest-session");
        return room;
    }
}
