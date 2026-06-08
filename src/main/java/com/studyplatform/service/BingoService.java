package com.studyplatform.service;

import com.studyplatform.dto.request.StudyMoveRequest;
import com.studyplatform.dto.response.StudyStateResponse;
import com.studyplatform.model.StudyStatus;
import com.studyplatform.model.StudyType;
import com.studyplatform.model.Player;
import com.studyplatform.model.Room;
import com.studyplatform.model.bingo.BingoGame;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 빙고 게임 로직 서비스 (N명, 텍스트 주제 기반)
 *
 * moveType:
 *   SET_BOARD   - 보드 주제 설정 (payload = List<List<String>>)
 *   CALL_TOPIC  - 주제 호출     (data = 주제 문자열)
 */
@Service
public class BingoService {

    @SuppressWarnings("unchecked")
    public StudyStateResponse processMove(Room room, Player player, StudyMoveRequest request) {
        BingoGame game = (BingoGame) room.getGameData();

        return switch (request.getMoveType()) {
            case "SET_BOARD"  -> handleSetBoard(room, game, player, request.getPayload());
            case "CALL_TOPIC" -> handleCallTopic(room, game, player, request.getData());
            default -> throw new IllegalArgumentException("Unknown action: " + request.getMoveType());
        };
    }

    /**
     * 보드 주제 설정
     * payload: List<List<String>> (Jackson이 2D 배열을 이렇게 역직렬화)
     */
    @SuppressWarnings("unchecked")
    private StudyStateResponse handleSetBoard(Room room, BingoGame game,
                                               Player player, Object payload) {
        if (payload == null) throw new IllegalArgumentException("Board topics are required.");

        List<List<String>> raw = (List<List<String>>) payload;
        int size = game.getSize();
        String[][] topics = new String[size][size];
        for (int r = 0; r < size; r++) {
            List<String> row = raw.get(r);
            for (int c = 0; c < size; c++) {
                String t = (row.size() > c && row.get(c) != null) ? row.get(c).trim() : "";
                if (t.isEmpty()) throw new IllegalArgumentException("All cells must have a topic.");
                topics[r][c] = t;
            }
        }

        int idx = player.getPlayerIndex();
        if (game.getBoardsSet()[idx]) throw new IllegalStateException("Board already set.");

        game.setPlayerBoard(idx, topics);

        if (game.allBoardsSet()) {
            room.setStatus(StudyStatus.PLAYING);
            String first = room.getPlayers().get(0).getNickname();
            return buildResponse(room, game, "All boards set! " + first + " goes first.");
        }

        long remaining = 0;
        for (boolean b : game.getBoardsSet()) if (!b) remaining++;
        return buildResponse(room, game,
                player.getNickname() + " set their board. Waiting for " + remaining + " more...");
    }

    /** 주제 호출 처리 */
    private StudyStateResponse handleCallTopic(Room room, BingoGame game,
                                                Player player, String topic) {
        if (game.getCurrentTurn() != player.getPlayerIndex())
            throw new IllegalStateException("Not your turn.");
        if (topic == null || topic.trim().isEmpty())
            throw new IllegalArgumentException("Topic cannot be empty.");

        boolean ok = game.callTopic(player.getPlayerIndex(), topic.trim());
        if (!ok) throw new IllegalArgumentException("\"" + topic + "\" has already been called.");

        String msg;
        if (game.getWinner() >= 0) {
            room.setStatus(StudyStatus.FINISHED);
            msg = "🎉 BINGO! " + room.getPlayers().get(game.getWinner()).getNickname() + " wins!";
        } else {
            String next = room.getPlayers().get(game.getCurrentTurn()).getNickname();
            msg = player.getNickname() + " called \"" + topic.trim() + "\" | Next: " + next;
        }
        return buildResponse(room, game, msg);
    }

    public StudyStateResponse buildInitialState(Room room) {
        BingoGame game = (BingoGame) room.getGameData();
        String msg = room.getStatus() == StudyStatus.SETUP
                ? "Fill in your bingo board topics. (" + game.getSize() + "×" + game.getSize() + " cells)"
                : "Bingo started! " + room.getPlayers().get(0).getNickname() + " goes first.";
        return buildResponse(room, game, msg);
    }

    public StudyStateResponse buildResponse(Room room, BingoGame game, String message) {
        String[] playerNames = room.getPlayers().stream()
                .map(Player::getNickname).toArray(String[]::new);

        int[] bingoCounts = new int[game.getNumPlayers()];
        for (int i = 0; i < game.getNumPlayers(); i++)
            bingoCounts[i] = game.getBoards()[i].getBingoCount();

        Map<String, Object> gameData = new HashMap<>();
        gameData.put("size",         game.getSize());
        gameData.put("numPlayers",   game.getNumPlayers());
        gameData.put("winBingoCount",game.getWinBingoCount());
        gameData.put("boards",       game.getBoards());      // topics + marked
        gameData.put("boardsSet",    game.getBoardsSet());   // 설정 완료 여부
        gameData.put("calledTopics", game.getCalledTopics());
        gameData.put("currentTurn",  game.getCurrentTurn());
        gameData.put("winner",       game.getWinner());
        gameData.put("bingoCounts",  bingoCounts);

        return StudyStateResponse.builder()
                .roomId(room.getRoomId())
                .studyType(StudyType.BINGO)
                .status(room.getStatus())
                .message(message)
                .currentTurn(game.getCurrentTurn())
                .winner(game.getWinner())
                .gameData(gameData)
                .playerNames(playerNames)
                .build();
    }
}
