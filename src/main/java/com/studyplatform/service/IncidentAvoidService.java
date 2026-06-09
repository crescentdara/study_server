package com.studyplatform.service;

import com.studyplatform.dto.request.StudyMoveRequest;
import com.studyplatform.dto.response.StudyStateResponse;
import com.studyplatform.model.Player;
import com.studyplatform.model.Room;
import com.studyplatform.model.StudyStatus;
import com.studyplatform.model.StudyType;
import com.studyplatform.model.incident.IncidentAvoidGame;
import com.studyplatform.model.incident.IncidentAvoidPlayerState;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class IncidentAvoidService {

    public StudyStateResponse processMove(Room room, Player player, StudyMoveRequest request) {
        if (!"INCIDENT_SYNC".equals(request.getMoveType())) {
            throw new IllegalArgumentException("Unknown INCIDENT_AVOID move.");
        }
        IncidentAvoidGame game = (IncidentAvoidGame) room.getGameData();
        if (game == null) {
            game = new IncidentAvoidGame(room.getPlayers().size());
            room.setGameData(game);
        }
        syncState(game, player.getPlayerIndex(), request.getPayload());
        updateWinner(room, game, player.getPlayerIndex());
        return buildInitialState(room);
    }

    public StudyStateResponse buildInitialState(Room room) {
        IncidentAvoidGame game = (IncidentAvoidGame) room.getGameData();
        if (game == null) {
            game = new IncidentAvoidGame(room.getPlayers().size());
            room.setGameData(game);
        }
        Map<String, Object> gameData = new HashMap<>();
        gameData.put("mode", "local");
        gameData.put("width", game.getWidth());
        gameData.put("height", game.getHeight());
        gameData.put("numPlayers", game.getNumPlayers());
        gameData.put("playerStates", game.getPlayerStates());

        String[] names = room.getPlayers().stream().map(Player::getNickname).toArray(String[]::new);
        return StudyStateResponse.builder()
                .roomId(room.getRoomId())
                .studyType(StudyType.INCIDENT_AVOID)
                .status(room.getStatus())
                .message(room.getStatus() == StudyStatus.FINISHED ? "INCIDENT_AVOID finished." : "INCIDENT_AVOID synced.")
                .currentTurn(0)
                .winner(game.getWinner())
                .gameData(gameData)
                .playerNames(names)
                .build();
    }

    @SuppressWarnings("unchecked")
    private void syncState(IncidentAvoidGame game, int playerIndex, Object payload) {
        if (!(payload instanceof Map<?, ?> map)) return;
        IncidentAvoidPlayerState state = game.getPlayerStates()
                .computeIfAbsent(playerIndex, ignored -> new IncidentAvoidPlayerState());
        state.setX(toDouble(map.get("x"), state.getX()));
        state.setScore(toInt(map.get("score"), state.getScore()));
        state.setSurvivedMs(toLong(map.get("survivedMs"), state.getSurvivedMs()));
        state.setRunning(toBoolean(map.get("running"), state.isRunning()));
        state.setGameOver(toBoolean(map.get("gameOver"), state.isGameOver()));
        Object incidents = map.get("incidents");
        if (incidents instanceof List<?> rows) {
            state.setIncidents((List<List<Double>>) rows);
        }
        state.setUpdatedAt(System.currentTimeMillis());
    }

    private void updateWinner(Room room, IncidentAvoidGame game, int playerIndex) {
        IncidentAvoidPlayerState state = game.getPlayerStates().get(playerIndex);
        if (state == null || !state.isGameOver() || game.getWinner() != -1) return;
        List<Integer> alive = game.getPlayerStates().entrySet().stream()
                .filter(entry -> !entry.getValue().isGameOver())
                .map(Map.Entry::getKey)
                .toList();
        if (alive.size() == 1) {
            game.setWinner(alive.get(0));
            room.setStatus(StudyStatus.FINISHED);
        } else if (alive.isEmpty()) {
            room.setStatus(StudyStatus.FINISHED);
        }
    }

    private int toInt(Object value, int fallback) {
        return value instanceof Number number ? number.intValue() : fallback;
    }

    private long toLong(Object value, long fallback) {
        return value instanceof Number number ? number.longValue() : fallback;
    }

    private double toDouble(Object value, double fallback) {
        return value instanceof Number number ? number.doubleValue() : fallback;
    }

    private boolean toBoolean(Object value, boolean fallback) {
        return value instanceof Boolean bool ? bool : fallback;
    }
}
