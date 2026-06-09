package com.studyplatform.service;

import com.studyplatform.dto.request.StudyMoveRequest;
import com.studyplatform.dto.response.StudyStateResponse;
import com.studyplatform.model.Player;
import com.studyplatform.model.Room;
import com.studyplatform.model.StudyStatus;
import com.studyplatform.model.StudyType;
import com.studyplatform.model.tetris.TetrisGame;
import com.studyplatform.model.tetris.TetrisPlayerState;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class TetrisService {

    public StudyStateResponse processMove(Room room, Player player, StudyMoveRequest request) {
        if (!"TETRIS_SYNC".equals(request.getMoveType())) {
            throw new IllegalArgumentException("Unknown TETRIS move.");
        }
        TetrisGame game = (TetrisGame) room.getGameData();
        if (game == null) {
            game = new TetrisGame(room.getPlayers().size());
            room.setGameData(game);
        }
        syncState(game, player.getPlayerIndex(), request.getPayload());
        updateWinner(room, game, player.getPlayerIndex());
        return buildInitialState(room);
    }

    public StudyStateResponse buildInitialState(Room room) {
        TetrisGame game = (TetrisGame) room.getGameData();
        if (game == null) {
            game = new TetrisGame(room.getPlayers().size());
            room.setGameData(game);
        }
        Map<String, Object> gameData = new HashMap<>();
        gameData.put("mode", "local");
        gameData.put("rows", game.getRows());
        gameData.put("cols", game.getCols());
        gameData.put("numPlayers", game.getNumPlayers());
        gameData.put("playerStates", game.getPlayerStates());

        String[] names = room.getPlayers().stream().map(Player::getNickname).toArray(String[]::new);
        return StudyStateResponse.builder()
                .roomId(room.getRoomId())
                .studyType(StudyType.TETRIS)
                .status(room.getStatus())
                .message(room.getStatus() == StudyStatus.FINISHED ? "TETRIS queue monitor finished." : "TETRIS queue monitor synced.")
                .currentTurn(0)
                .winner(game.getWinner())
                .gameData(gameData)
                .playerNames(names)
                .build();
    }

    @SuppressWarnings("unchecked")
    private void syncState(TetrisGame game, int playerIndex, Object payload) {
        if (!(payload instanceof Map<?, ?> map)) return;

        TetrisPlayerState state = game.getPlayerStates()
                .computeIfAbsent(playerIndex, ignored -> new TetrisPlayerState());
        Object board = map.get("board");
        if (board instanceof List<?> rows) {
            state.setBoard((List<List<String>>) rows);
        }
        state.setScore(toInt(map.get("score"), state.getScore()));
        state.setLines(toInt(map.get("lines"), state.getLines()));
        state.setCycle(toInt(map.get("cycle"), state.getCycle()));
        state.setRunning(toBoolean(map.get("running"), state.isRunning()));
        state.setGameOver(toBoolean(map.get("gameOver"), state.isGameOver()));
        state.setUpdatedAt(System.currentTimeMillis());
    }

    private void updateWinner(Room room, TetrisGame game, int playerIndex) {
        TetrisPlayerState state = game.getPlayerStates().get(playerIndex);
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
        if (value instanceof Number number) return number.intValue();
        return fallback;
    }

    private boolean toBoolean(Object value, boolean fallback) {
        if (value instanceof Boolean bool) return bool;
        return fallback;
    }
}
