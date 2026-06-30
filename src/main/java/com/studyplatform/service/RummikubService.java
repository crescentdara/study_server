package com.studyplatform.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.studyplatform.dto.request.StudyMoveRequest;
import com.studyplatform.dto.response.StudyStateResponse;
import com.studyplatform.model.Player;
import com.studyplatform.model.Room;
import com.studyplatform.model.StudyStatus;
import com.studyplatform.model.StudyType;
import com.studyplatform.model.rummikub.RummikubGame;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class RummikubService {

    private final ObjectMapper objectMapper;

    public RummikubService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public StudyStateResponse processMove(Room room, Player player, StudyMoveRequest request) {
        RummikubGame game = (RummikubGame) room.getGameData();
        String moveType = request.getMoveType();

        if ("RUMMY_DRAW".equals(moveType)) {
            return handleDraw(room, game, player);
        }
        if ("RUMMY_PLACE".equals(moveType)) {
            return handlePlace(room, game, player, request.getPayload());
        }
        throw new IllegalArgumentException("Unknown RUMMIKUB move: " + moveType);
    }

    private StudyStateResponse handleDraw(Room room, RummikubGame game, Player player) {
        String error = game.drawTile(player.getPlayerIndex());
        if (error != null) {
            return buildState(room, game, "ERROR: " + error);
        }
        if (game.getWinner() >= 0) {
            room.setStatus(StudyStatus.FINISHED);
        }
        return buildState(room, game, player.getNickname() + " drew a tile.");
    }

    private StudyStateResponse handlePlace(Room room, RummikubGame game, Player player, Object payload) {
        List<List<Integer>> newTable;
        try {
            newTable = objectMapper.convertValue(payload, new TypeReference<List<List<Integer>>>() {});
        } catch (Exception e) {
            return buildState(room, game, "ERROR: Invalid table format: " + e.getMessage());
        }

        if (newTable == null) {
            return buildState(room, game, "ERROR: Table payload is null");
        }

        String error = game.placeOnTable(player.getPlayerIndex(), newTable);
        if (error != null) {
            return buildState(room, game, "ERROR: " + error);
        }

        if (game.getWinner() >= 0) {
            room.setStatus(StudyStatus.FINISHED);
            String[] names = room.getPlayers().stream().map(Player::getNickname).toArray(String[]::new);
            return StudyStateResponse.builder()
                    .roomId(room.getRoomId())
                    .studyType(StudyType.RUMMIKUB)
                    .status(StudyStatus.FINISHED)
                    .message(names[game.getWinner()] + " wins!")
                    .currentTurn(game.getCurrentTurn())
                    .winner(game.getWinner())
                    .playerNames(names)
                    .gameData(buildPublicGameData(game))
                    .build();
        }

        return buildState(room, game, player.getNickname() + " played tiles.");
    }

    public StudyStateResponse buildInitialState(Room room) {
        RummikubGame game = (RummikubGame) room.getGameData();
        if (game == null) {
            game = new RummikubGame(room.getPlayers().size());
            room.setGameData(game);
        }
        return buildState(room, game, "");
    }

    private StudyStateResponse buildState(Room room, RummikubGame game, String message) {
        String[] names = room.getPlayers().stream().map(Player::getNickname).toArray(String[]::new);
        return StudyStateResponse.builder()
                .roomId(room.getRoomId())
                .studyType(StudyType.RUMMIKUB)
                .status(room.getStatus())
                .message(message)
                .currentTurn(game.getCurrentTurn())
                .winner(game.getWinner())
                .gameData(buildPublicGameData(game))
                .playerNames(names)
                .build();
    }

    public StudyStateResponse buildPlayerState(Room room, Player player) {
        RummikubGame game = (RummikubGame) room.getGameData();
        if (game == null) return null;
        String[] names = room.getPlayers().stream().map(Player::getNickname).toArray(String[]::new);
        return StudyStateResponse.builder()
                .roomId(room.getRoomId())
                .studyType(StudyType.RUMMIKUB)
                .status(room.getStatus())
                .message("")
                .currentTurn(game.getCurrentTurn())
                .winner(game.getWinner())
                .gameData(buildPlayerGameData(game, player.getPlayerIndex()))
                .playerNames(names)
                .build();
    }

    public Map<String, Object> buildPublicGameData(RummikubGame game) {
        Map<String, Object> data = buildCommonGameData(game);
        List<List<Integer>> hiddenHands = game.getHands().stream()
                .map(hand -> new ArrayList<Integer>())
                .collect(Collectors.toList());
        data.put("hands", hiddenHands);
        data.put("handCounts", game.getHands().stream().map(List::size).collect(Collectors.toList()));
        return data;
    }

    public Map<String, Object> buildPlayerGameData(RummikubGame game, int playerIndex) {
        Map<String, Object> data = buildCommonGameData(game);
        List<List<Integer>> hands = new ArrayList<>();
        for (int i = 0; i < game.getHands().size(); i++) {
            hands.add(i == playerIndex ? new ArrayList<>(game.getHands().get(i)) : new ArrayList<>());
        }
        data.put("hands", hands);
        data.put("handCounts", game.getHands().stream().map(List::size).collect(Collectors.toList()));
        return data;
    }

    private Map<String, Object> buildCommonGameData(RummikubGame game) {
        Map<String, Object> data = new HashMap<>();
        data.put("table",            game.getTable());
        data.put("poolSize",         game.getPool().size());
        data.put("initialMeld",      game.getInitialMeld());
        data.put("numPlayers",       game.getNumPlayers());
        data.put("currentTurn",      game.getCurrentTurn());
        data.put("winner",           game.getWinner());
        data.put("hasDrawnThisTurn", game.isHasDrawnThisTurn());
        return data;
    }
}
