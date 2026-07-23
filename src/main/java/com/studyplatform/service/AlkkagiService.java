package com.studyplatform.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.studyplatform.dto.request.StudyMoveRequest;
import com.studyplatform.dto.response.StudyStateResponse;
import com.studyplatform.model.Player;
import com.studyplatform.model.Room;
import com.studyplatform.model.StudyStatus;
import com.studyplatform.model.StudyType;
import com.studyplatform.model.alkkagi.AlkkagiGame;
import com.studyplatform.model.alkkagi.AlkkagiStone;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class AlkkagiService {
    private final ObjectMapper objectMapper;

    public AlkkagiService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public StudyStateResponse processMove(Room room, Player player, StudyMoveRequest request) {
        AlkkagiGame game = (AlkkagiGame) room.getGameData();
        if ("ALKKAGI_AIM".equals(request.getMoveType())) {
            return handleAim(room, game, player, request.getPayload());
        }
        if ("ALKKAGI_RESULT".equals(request.getMoveType()) || "ALKKAGI_SHOT".equals(request.getMoveType())) {
            return handleResult(room, game, player, request.getPayload());
        }
        if ("ALKKAGI_TIMEOUT".equals(request.getMoveType())) {
            return handleTimeout(room, game);
        }
        throw new IllegalArgumentException("Unknown ALKKAGI move: " + request.getMoveType());
    }

    private StudyStateResponse handleAim(Room room, AlkkagiGame game, Player player, Object payload) {
        Map<String, Object> data = objectMapper.convertValue(payload, new TypeReference<Map<String, Object>>() {});
        int stoneId = toInt(data.get("stoneId"), -1);
        double vx = toDouble(data.get("vx"), 0);
        double vy = toDouble(data.get("vy"), 0);
        String error = game.beginShot(player.getPlayerIndex(), stoneId, vx, vy);
        if (error != null) return buildState(room, game, "ERROR: " + error);
        return buildState(room, game, player.getNickname() + " shot.");
    }

    private StudyStateResponse handleResult(Room room, AlkkagiGame game, Player player, Object payload) {
        Map<String, Object> data = objectMapper.convertValue(payload, new TypeReference<Map<String, Object>>() {});
        int shotId = toInt(data.get("shotId"), -1);
        List<AlkkagiStone> stones = objectMapper.convertValue(
                data.get("stones"),
                new TypeReference<List<AlkkagiStone>>() {}
        );
        String error = game.applyShotResult(player.getPlayerIndex(), shotId, stones);
        if (error != null) return buildState(room, game, "ERROR: " + error);
        if (game.getWinner() >= 0) room.setStatus(StudyStatus.FINISHED);
        return buildState(room, game, player.getNickname() + " played a shot.");
    }

    private StudyStateResponse handleTimeout(Room room, AlkkagiGame game) {
        String error = game.timeoutTurn();
        if (error != null) return buildState(room, game, "ERROR: " + error);
        return buildState(room, game, "Turn timed out.");
    }

    public StudyStateResponse buildInitialState(Room room) {
        AlkkagiGame game = (AlkkagiGame) room.getGameData();
        if (game == null) {
            game = new AlkkagiGame(room.getPlayers().size());
            room.setGameData(game);
        }
        return buildState(room, game, "");
    }

    private StudyStateResponse buildState(Room room, AlkkagiGame game, String message) {
        String[] names = room.getPlayers().stream().map(Player::getNickname).toArray(String[]::new);
        return StudyStateResponse.builder()
                .roomId(room.getRoomId())
                .studyType(StudyType.ALKKAGI)
                .status(room.getStatus())
                .message(message)
                .currentTurn(game.getCurrentTurn())
                .winner(game.getWinner())
                .playerNames(names)
                .gameData(buildGameData(game))
                .build();
    }

    private Map<String, Object> buildGameData(AlkkagiGame game) {
        Map<String, Object> data = new HashMap<>();
        data.put("numPlayers", game.getNumPlayers());
        data.put("currentTurn", game.getCurrentTurn());
        data.put("winner", game.getWinner());
        data.put("shotCount", game.getShotCount());
        data.put("turnStartedAt", game.getTurnStartedAt());
        data.put("turnTimeLimitMs", game.getTurnTimeLimitMs());
        data.put("shotLog", game.getShotLog());
        data.put("mapType", game.getMapType());
        data.put("mapSeed", game.getMapSeed());
        data.put("mapPhase", game.getMapPhase());
        data.put("stones", game.getStones());
        data.put("activeShot", game.getActiveShot());
        data.put("activeShotStartedAt", game.getActiveShotStartedAt());
        data.put("shotResultTimeoutMs", AlkkagiGame.SHOT_RESULT_TIMEOUT_MS);
        return data;
    }

    private int toInt(Object value, int fallback) {
        if (value instanceof Number n) return n.intValue();
        if (value instanceof String s) {
            try { return Integer.parseInt(s); } catch (NumberFormatException ignored) { return fallback; }
        }
        return fallback;
    }

    private double toDouble(Object value, double fallback) {
        if (value instanceof Number n) return n.doubleValue();
        if (value instanceof String s) {
            try { return Double.parseDouble(s); } catch (NumberFormatException ignored) { return fallback; }
        }
        return fallback;
    }
}
