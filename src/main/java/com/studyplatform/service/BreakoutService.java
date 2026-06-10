package com.studyplatform.service;

import com.studyplatform.dto.request.StudyMoveRequest;
import com.studyplatform.dto.response.StudyStateResponse;
import com.studyplatform.model.Player;
import com.studyplatform.model.Room;
import com.studyplatform.model.StudyStatus;
import com.studyplatform.model.StudyType;
import com.studyplatform.model.breakout.BreakoutGame;
import com.studyplatform.model.breakout.BreakoutPlayerState;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class BreakoutService {

    public StudyStateResponse processMove(Room room, Player player, StudyMoveRequest request) {
        if (!"BREAKOUT_SYNC".equals(request.getMoveType())) {
            throw new IllegalArgumentException("Unknown BREAKOUT move.");
        }
        BreakoutGame game = (BreakoutGame) room.getGameData();
        if (game == null) {
            game = new BreakoutGame(room.getPlayers().size());
            room.setGameData(game);
        }
        syncState(game, player.getPlayerIndex(), request.getPayload());
        updateWinner(room, game);
        return buildInitialState(room);
    }

    public StudyStateResponse buildInitialState(Room room) {
        BreakoutGame game = (BreakoutGame) room.getGameData();
        if (game == null) {
            game = new BreakoutGame(room.getPlayers().size());
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
                .studyType(StudyType.BREAKOUT)
                .status(room.getStatus())
                .message(room.getStatus() == StudyStatus.FINISHED ? "BREAKOUT monitor finished." : "BREAKOUT monitor synced.")
                .currentTurn(0)
                .winner(game.getWinner())
                .gameData(gameData)
                .playerNames(names)
                .build();
    }

    @SuppressWarnings("unchecked")
    private void syncState(BreakoutGame game, int playerIndex, Object payload) {
        if (!(payload instanceof Map<?, ?> map)) return;

        BreakoutPlayerState state = game.getPlayerStates()
                .computeIfAbsent(playerIndex, ignored -> new BreakoutPlayerState());
        state.setPaddleX(toDouble(map.get("paddleX"), state.getPaddleX()));
        state.setBallX(toDouble(map.get("ballX"), state.getBallX()));
        state.setBallY(toDouble(map.get("ballY"), state.getBallY()));
        state.setScore(toInt(map.get("score"), state.getScore()));
        state.setBricksLeft(toInt(map.get("bricksLeft"), state.getBricksLeft()));
        state.setRunning(toBoolean(map.get("running"), state.isRunning()));
        state.setGameOver(toBoolean(map.get("gameOver"), state.isGameOver()));
        state.setCleared(toBoolean(map.get("cleared"), state.isCleared()));
        Object bricks = map.get("bricks");
        if (bricks instanceof List<?> list) {
            List<Integer> normalized = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Number number) normalized.add(number.intValue());
            }
            state.setBricks(normalized);
        }
        state.setUpdatedAt(System.currentTimeMillis());
    }

    private void updateWinner(Room room, BreakoutGame game) {
        if (game.getWinner() != -1) return;
        List<Map.Entry<Integer, BreakoutPlayerState>> states = game.getPlayerStates().entrySet().stream().toList();
        for (Map.Entry<Integer, BreakoutPlayerState> entry : states) {
            if (entry.getValue().isCleared()) {
                game.setWinner(entry.getKey());
                room.setStatus(StudyStatus.FINISHED);
                return;
            }
        }
        boolean allDone = states.stream().allMatch(entry -> entry.getValue().isGameOver());
        if (allDone && !states.isEmpty()) {
            int best = states.stream()
                    .max((a, b) -> Integer.compare(a.getValue().getScore(), b.getValue().getScore()))
                    .map(Map.Entry::getKey)
                    .orElse(-1);
            game.setWinner(best);
            room.setStatus(StudyStatus.FINISHED);
        }
    }

    private int toInt(Object value, int fallback) {
        return value instanceof Number number ? number.intValue() : fallback;
    }

    private double toDouble(Object value, double fallback) {
        return value instanceof Number number ? number.doubleValue() : fallback;
    }

    private boolean toBoolean(Object value, boolean fallback) {
        return value instanceof Boolean bool ? bool : fallback;
    }
}
