package com.studyplatform.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.studyplatform.dto.request.StudyMoveRequest;
import com.studyplatform.dto.response.StudyStateResponse;
import com.studyplatform.model.Player;
import com.studyplatform.model.Room;
import com.studyplatform.model.StudyStatus;
import com.studyplatform.model.StudyType;
import com.studyplatform.model.davinci.DaVinciGame;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class DaVinciServiceTest {

    @Test
    void publicStateMasksNumbersWhilePrivateStateContainsOnlyOwnSecrets() {
        Room room = roomWithGame();
        DaVinciGame game = (DaVinciGame) room.getGameData();
        DaVinciService service = new DaVinciService(new ObjectMapper());

        Map<String, Object> publicData = data(service.buildInitialState(room));
        List<List<Integer>> publicTiles = tiles(publicData);
        assertThat(publicTiles.get(0)).containsExactly(-2, -2, -2, -2);
        assertThat(publicTiles.get(1)).containsExactly(-3, -3, -3, -3);

        Map<String, Object> playerZeroData = data(service.buildPlayerState(room, room.getPlayers().get(0)));
        List<List<Integer>> playerZeroTiles = tiles(playerZeroData);
        assertThat(playerZeroTiles.get(0)).containsExactlyElementsOf(game.getPlayerTiles().get(0));
        assertThat(playerZeroTiles.get(1)).containsExactly(-3, -3, -3, -3);

        assertThat(game.drawTile(0)).isNull();
        int actualPending = game.getPendingTileId();
        assertThat(data(service.buildInitialState(room)).get("pendingTileId"))
                .isEqualTo(DaVinciGame.HIDDEN_BLACK);
        assertThat(data(service.buildPlayerState(room, room.getPlayers().get(0))).get("pendingTileId"))
                .isEqualTo(actualPending);
        assertThat(data(service.buildPlayerState(room, room.getPlayers().get(1))).get("pendingTileId"))
                .isEqualTo(DaVinciGame.HIDDEN_BLACK);
    }

    @Test
    void everyMoveGetsANewMessageEventIdEvenWhenMessageRepeats() {
        Room room = roomWithGame();
        DaVinciService service = new DaVinciService(new ObjectMapper());
        Player player = room.getPlayers().get(0);

        StudyMoveRequest draw = new StudyMoveRequest();
        draw.setMoveType("DAVINCI_DRAW");
        StudyStateResponse first = service.processMove(room, player, draw);
        StudyStateResponse repeatedError = service.processMove(room, player, draw);

        assertThat(data(first).get("messageEventId")).isEqualTo(1L);
        assertThat(data(repeatedError).get("messageEventId")).isEqualTo(2L);
        assertThat(repeatedError.getMessage()).startsWith("ERROR:");
    }

    private static Room roomWithGame() {
        Room room = new Room("Da Vinci", StudyType.DAVINCI_CODE);
        room.setStatus(StudyStatus.PLAYING);
        room.getPlayers().add(new Player("s0", "A", 0));
        room.getPlayers().add(new Player("s1", "B", 1));
        room.setGameData(new DaVinciGame(2, deck(0, 1, 2, 3, 12, 13, 14, 15, 4)));
        return room;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> data(StudyStateResponse state) {
        return (Map<String, Object>) state.getGameData();
    }

    @SuppressWarnings("unchecked")
    private static List<List<Integer>> tiles(Map<String, Object> data) {
        return (List<List<Integer>>) data.get("playerTiles");
    }

    private static List<Integer> deck(int... prefix) {
        List<Integer> result = new ArrayList<>();
        Set<Integer> used = new HashSet<>();
        for (int id : prefix) if (used.add(id)) result.add(id);
        for (int id = 0; id < 26; id++) if (used.add(id)) result.add(id);
        return result;
    }
}
