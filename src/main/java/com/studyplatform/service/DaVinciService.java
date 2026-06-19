package com.studyplatform.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.studyplatform.dto.request.StudyMoveRequest;
import com.studyplatform.dto.response.StudyStateResponse;
import com.studyplatform.model.Player;
import com.studyplatform.model.Room;
import com.studyplatform.model.StudyStatus;
import com.studyplatform.model.StudyType;
import com.studyplatform.model.davinci.DaVinciGame;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class DaVinciService {

    private final ObjectMapper objectMapper;

    public DaVinciService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public StudyStateResponse processMove(Room room, Player player, StudyMoveRequest request) {
        if (!(room.getGameData() instanceof DaVinciGame)) {
            room.setGameData(new DaVinciGame(room.getPlayers().size()));
        }
        DaVinciGame game = (DaVinciGame) room.getGameData();
        int playerIndex  = room.getPlayers().indexOf(player);
        String moveType  = request.getMoveType();

        if ("DAVINCI_DRAW".equals(moveType)) {
            String error = game.drawTile(playerIndex);
            return error != null
                ? buildState(room, game, "ERROR: " + error)
                : buildState(room, game, "");

        } else if ("DAVINCI_PLACE".equals(moveType)) {
            int position = 0;
            // Try payload first, then data field as fallback
            if (request.getPayload() != null) {
                try {
                    Map<String, Integer> payload = objectMapper.convertValue(
                            request.getPayload(), new TypeReference<Map<String, Integer>>() {});
                    position = payload.getOrDefault("position", 0);
                } catch (Exception ignored) {}
            }
            if (position == 0 && request.getData() != null && !request.getData().isBlank()) {
                try { position = Integer.parseInt(request.getData().trim()); } catch (Exception ignored) {}
            }
            String error = game.placeTile(playerIndex, position);
            return error != null
                ? buildState(room, game, "ERROR: " + error)
                : buildState(room, game, "");

        } else if ("DAVINCI_GUESS".equals(moveType)) {
            if (request.getPayload() == null) return buildState(room, game, "ERROR: Missing payload");
            Map<String, Integer> payload;
            try {
                payload = objectMapper.convertValue(
                        request.getPayload(), new TypeReference<Map<String, Integer>>() {});
            } catch (Exception e) {
                return buildState(room, game, "ERROR: Invalid payload");
            }
            int targetPlayer  = payload.getOrDefault("targetPlayer",  -1);
            int targetPos     = payload.getOrDefault("targetPosition", -1);
            int guessedNumber = payload.getOrDefault("guessedNumber", -99);

            String result = game.guess(playerIndex, targetPlayer, targetPos, guessedNumber);
            String message = result == null ? "CORRECT" : "WRONG".equals(result) ? "WRONG" : "ERROR: " + result;

            if (game.getWinner() >= 0) {
                String[] names = room.getPlayers().stream().map(Player::getNickname).toArray(String[]::new);
                room.setStatus(StudyStatus.FINISHED);
                return StudyStateResponse.builder()
                        .roomId(room.getRoomId()).studyType(StudyType.DAVINCI_CODE)
                        .status(StudyStatus.FINISHED)
                        .message(names[game.getWinner()] + " 승리!")
                        .currentTurn(game.getCurrentTurn()).winner(game.getWinner())
                        .gameData(buildGameData(game)).playerNames(names).build();
            }
            return buildState(room, game, message);

        } else if ("DAVINCI_PASS".equals(moveType)) {
            String error = game.pass(playerIndex);
            return error != null
                ? buildState(room, game, "ERROR: " + error)
                : buildState(room, game, "");

        } else {
            throw new IllegalArgumentException("Unknown DAVINCI move: " + moveType);
        }
    }

    public StudyStateResponse buildInitialState(Room room) {
        if (!(room.getGameData() instanceof DaVinciGame)) {
            room.setGameData(new DaVinciGame(room.getPlayers().size()));
        }
        return buildState(room, (DaVinciGame) room.getGameData(), "");
    }

    private StudyStateResponse buildState(Room room, DaVinciGame game, String message) {
        String[] names = room.getPlayers().stream().map(Player::getNickname).toArray(String[]::new);
        return StudyStateResponse.builder()
                .roomId(room.getRoomId()).studyType(StudyType.DAVINCI_CODE)
                .status(room.getStatus()).message(message)
                .currentTurn(game.getCurrentTurn()).winner(game.getWinner())
                .gameData(buildGameData(game)).playerNames(names).build();
    }

    private Map<String, Object> buildGameData(DaVinciGame g) {
        Map<String, Object> data = new HashMap<>();
        data.put("numPlayers",              g.getNumPlayers());
        data.put("currentTurn",             g.getCurrentTurn());
        data.put("winner",                  g.getWinner());
        data.put("poolSize",                g.getPool().size());
        data.put("playerTiles",             g.getPlayerTiles());
        data.put("revealed",                g.getRevealed());
        data.put("pendingTileId",           g.getPendingTileId() != null ? g.getPendingTileId() : -1);
        data.put("drawnTileId",             g.getDrawnTileId()   != null ? g.getDrawnTileId()   : -1);
        data.put("drawnRevealed",           g.isDrawnRevealed());
        data.put("correctGuessesThisTurn",  g.getCorrectGuessesThisTurn());
        return data;
    }
}
