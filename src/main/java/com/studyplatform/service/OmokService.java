package com.studyplatform.service;

import com.studyplatform.dto.request.StudyMoveRequest;
import com.studyplatform.dto.response.StudyStateResponse;
import com.studyplatform.model.Player;
import com.studyplatform.model.Room;
import com.studyplatform.model.StudyStatus;
import com.studyplatform.model.StudyType;
import com.studyplatform.model.omok.OmokGame;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class OmokService {
    public StudyStateResponse processMove(Room room, Player player, StudyMoveRequest request) {
        if (!"PLACE_STONE".equals(request.getMoveType())) {
            throw new IllegalArgumentException("Unknown action: " + request.getMoveType());
        }

        OmokGame game = (OmokGame) room.getGameData();
        int[] cell = readCell(request.getPayload(), request.getData());
        game.placeStone(player.getPlayerIndex(), cell[0], cell[1]);

        String message;
        if (game.getWinner() >= 0) {
            room.setStatus(StudyStatus.FINISHED);
            message = room.getPlayers().get(game.getWinner()).getNickname() + " wins OMOK!";
        } else if (game.isDraw()) {
            room.setStatus(StudyStatus.FINISHED);
            message = "OMOK draw.";
        } else {
            message = player.getNickname() + " placed: R" + (cell[0] + 1) + " C" + (cell[1] + 1);
        }

        return buildResponse(room, game, message);
    }

    public StudyStateResponse buildInitialState(Room room) {
        OmokGame game = (OmokGame) room.getGameData();
        String first = room.getPlayers().get(0).getNickname();
        return buildResponse(room, game, "OMOK started. " + first + " goes first. P1 3-3 is forbidden.");
    }

    public StudyStateResponse buildResponse(Room room, OmokGame game, String message) {
        String[] playerNames = room.getPlayers().stream()
                .map(Player::getNickname)
                .toArray(String[]::new);

        Map<String, Object> gameData = new HashMap<>();
        gameData.put("size", game.getSize());
        gameData.put("numPlayers", game.getNumPlayers());
        gameData.put("board", game.getBoard());
        gameData.put("currentTurn", game.getCurrentTurn());
        gameData.put("winner", game.getWinner());
        gameData.put("moveCount", game.getMoveCount());
        gameData.put("lastRow", game.getLastRow());
        gameData.put("lastCol", game.getLastCol());
        gameData.put("winPath", game.getWinPath());

        return StudyStateResponse.builder()
                .roomId(room.getRoomId())
                .studyType(StudyType.OMOK)
                .status(room.getStatus())
                .message(message)
                .currentTurn(game.getCurrentTurn())
                .winner(game.getWinner())
                .gameData(gameData)
                .playerNames(playerNames)
                .build();
    }

    @SuppressWarnings("unchecked")
    private int[] readCell(Object payload, String data) {
        if (payload instanceof Map<?, ?> raw) {
            Object row = raw.get("row");
            Object col = raw.get("col");
            if (row instanceof Number r && col instanceof Number c) {
                return new int[] { r.intValue(), c.intValue() };
            }
        }

        if (data != null && data.contains(",")) {
            String[] parts = data.split(",", 2);
            try {
                return new int[] { Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim()) };
            } catch (NumberFormatException ignored) {
                // Fall through to the validation error below.
            }
        }

        throw new IllegalArgumentException("Cell coordinates are required.");
    }
}
