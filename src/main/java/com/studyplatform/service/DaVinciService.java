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
import java.util.ArrayList;
import java.util.List;
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
        game.nextMessageEventId();

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
                        .gameData(buildPublicGameData(game)).playerNames(names).build();
            }
            return buildState(room, game, message);

        } else if ("DAVINCI_PASS".equals(moveType)) {
            String error = game.pass(playerIndex);
            return error != null
                ? buildState(room, game, "ERROR: " + error)
                : buildState(room, game, "");

        } else if ("DAVINCI_FINISHER".equals(moveType)) {
            if (request.getPayload() == null) return buildState(room, game, "ERROR: Missing payload");
            Map<String, Object> payload;
            try {
                payload = objectMapper.convertValue(
                        request.getPayload(), new TypeReference<Map<String, Object>>() {});
            } catch (Exception e) {
                return buildState(room, game, "ERROR: Invalid payload");
            }
            String style = String.valueOf(payload.getOrDefault("style", ""));
            int tauntId;
            try {
                tauntId = Integer.parseInt(String.valueOf(payload.getOrDefault("tauntId", "-1")));
            } catch (NumberFormatException exception) {
                tauntId = -1;
            }
            String error = game.executeFinisher(playerIndex, style, tauntId);
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
                .gameData(buildPublicGameData(game)).playerNames(names).build();
    }

    public StudyStateResponse buildPlayerState(Room room, Player player) {
        if (!(room.getGameData() instanceof DaVinciGame game) || player == null) return null;
        int playerIndex = room.getPlayers().indexOf(player);
        if (playerIndex < 0) return null;
        String[] names = room.getPlayers().stream().map(Player::getNickname).toArray(String[]::new);
        return StudyStateResponse.builder()
                .roomId(room.getRoomId()).studyType(StudyType.DAVINCI_CODE)
                .status(room.getStatus()).message("")
                .currentTurn(game.getCurrentTurn()).winner(game.getWinner())
                .gameData(buildPlayerGameData(game, playerIndex)).playerNames(names).build();
    }

    public Map<String, Object> buildPublicGameData(DaVinciGame game) {
        return buildGameData(game, -1);
    }

    public Map<String, Object> buildPlayerGameData(DaVinciGame game, int playerIndex) {
        return buildGameData(game, playerIndex);
    }

    private Map<String, Object> buildGameData(DaVinciGame g, int viewerIndex) {
        Map<String, Object> data = new HashMap<>();
        data.put("gameId",                  g.getGameId());
        data.put("messageEventId",          g.getMessageEventId());
        data.put("eliminationEventId",      g.getEliminationEventId());
        data.put("executionEventId",        g.getExecutionEventId());
        data.put("lastEliminatedPlayer",    g.getLastEliminatedPlayer());
        data.put("lastEliminatorPlayer",    g.getLastEliminatorPlayer());
        data.put("finisherPending",         g.isFinisherPending());
        data.put("executionStyle",          g.getExecutionStyle());
        data.put("executionTaunt",          g.getExecutionTaunt());
        data.put("numPlayers",              g.getNumPlayers());
        data.put("currentTurn",             g.getCurrentTurn());
        data.put("winner",                  g.getWinner());
        data.put("poolSize",                g.getPool().size());
        List<List<Integer>> visibleTiles = new ArrayList<>();
        for (int playerIndex = 0; playerIndex < g.getPlayerTiles().size(); playerIndex++) {
            List<Integer> row = new ArrayList<>();
            for (int tileIndex = 0; tileIndex < g.getPlayerTiles().get(playerIndex).size(); tileIndex++) {
                int tileId = g.getPlayerTiles().get(playerIndex).get(tileIndex);
                boolean visible = playerIndex == viewerIndex || g.getRevealed().get(playerIndex).get(tileIndex);
                row.add(visible ? tileId : hiddenTile(tileId));
            }
            visibleTiles.add(row);
        }
        data.put("playerTiles",             visibleTiles);
        data.put("revealed",                g.getRevealed());
        boolean viewerIsCurrent = viewerIndex == g.getCurrentTurn();
        data.put("pendingTileId",           g.getPendingTileId() == null ? -1
                : viewerIsCurrent ? g.getPendingTileId() : hiddenTile(g.getPendingTileId()));
        data.put("drawnTileId",             g.getDrawnTileId() == null ? -1
                : viewerIsCurrent ? g.getDrawnTileId() : hiddenTile(g.getDrawnTileId()));
        data.put("drawnRevealed",           g.isDrawnRevealed());
        data.put("correctGuessesThisTurn",  g.getCorrectGuessesThisTurn());
        return data;
    }

    private int hiddenTile(int tileId) {
        return "black".equals(DaVinciGame.tileColor(tileId))
                ? DaVinciGame.HIDDEN_BLACK
                : DaVinciGame.HIDDEN_WHITE;
    }
}
