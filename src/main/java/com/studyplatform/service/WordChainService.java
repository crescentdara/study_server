package com.studyplatform.service;

import com.studyplatform.dto.request.StudyMoveRequest;
import com.studyplatform.dto.response.StudyStateResponse;
import com.studyplatform.model.Player;
import com.studyplatform.model.Room;
import com.studyplatform.model.StudyStatus;
import com.studyplatform.model.StudyType;
import com.studyplatform.model.wordchain.WordChainGame;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class WordChainService {

    public StudyStateResponse processMove(Room room, Player player, StudyMoveRequest request) {
        WordChainGame game = (WordChainGame) room.getGameData();
        String moveType = request.getMoveType();

        if ("WORD_CHAIN_SUBMIT".equals(moveType)) {
            return handleSubmit(room, game, player, request.getData());
        }
        if ("WORD_CHAIN_TIMEOUT".equals(moveType)) {
            return handleTimeout(room, game, player);
        }
        throw new IllegalArgumentException("Unknown WORD_CHAIN move: " + moveType);
    }

    private StudyStateResponse handleSubmit(Room room, WordChainGame game, Player player, String word) {
        if (player.getPlayerIndex() != game.getCurrentTurn()) {
            return buildState(room, game, "ERROR: 당신의 차례가 아닙니다.");
        }
        if (word == null || word.trim().isEmpty()) {
            return buildState(room, game, "ERROR: 단어를 입력하세요.");
        }
        word = word.trim();
        if (word.length() < 2) {
            return buildState(room, game, "ERROR: 두 글자 이상 입력하세요.");
        }
        if (!game.isValidStart(word)) {
            return buildState(room, game, "ERROR: 올바른 글자로 시작하는 단어를 입력하세요.");
        }
        if (game.getUsedWords().contains(word)) {
            return buildState(room, game, "ERROR: 이미 사용된 단어입니다.");
        }

        game.getUsedWords().add(word);
        game.setLastWord(word);

        int next = game.nextAliveIndex(game.getCurrentTurn());
        game.setCurrentTurn(next);
        return buildState(room, game, word + " ✓");
    }

    private StudyStateResponse handleTimeout(Room room, WordChainGame game, Player player) {
        int idx = player.getPlayerIndex();
        // 현재 턴인 플레이어만 타임아웃 처리 허용
        if (idx != game.getCurrentTurn()) {
            return buildState(room, game, "");
        }
        game.getEliminated().set(idx, true);

        if (game.aliveCount() == 1) {
            // 승자 결정
            int winnerIdx = 0;
            for (int i = 0; i < game.getNumPlayers(); i++) {
                if (!game.getEliminated().get(i)) { winnerIdx = i; break; }
            }
            game.setWinner(winnerIdx);
            room.setStatus(StudyStatus.FINISHED);
            String[] names = room.getPlayers().stream().map(Player::getNickname).toArray(String[]::new);
            return StudyStateResponse.builder()
                    .roomId(room.getRoomId()).studyType(StudyType.WORD_CHAIN)
                    .status(StudyStatus.FINISHED)
                    .message(names[winnerIdx] + " 승리!")
                    .currentTurn(winnerIdx).winner(winnerIdx)
                    .gameData(buildGameData(game))
                    .playerNames(names).build();
        }

        int next = game.nextAliveIndex(idx);
        game.setCurrentTurn(next);
        return buildState(room, game, room.getPlayers().get(idx).getNickname() + " 시간 초과!");
    }

    public StudyStateResponse buildInitialState(Room room) {
        WordChainGame game = (WordChainGame) room.getGameData();
        if (game == null) {
            game = new WordChainGame(room.getPlayers().size(), room.getDigits());
            room.setGameData(game);
        }
        return buildState(room, game, "");
    }

    private StudyStateResponse buildState(Room room, WordChainGame game, String message) {
        String[] names = room.getPlayers().stream().map(Player::getNickname).toArray(String[]::new);
        return StudyStateResponse.builder()
                .roomId(room.getRoomId()).studyType(StudyType.WORD_CHAIN)
                .status(room.getStatus())
                .message(message)
                .currentTurn(game.getCurrentTurn())
                .winner(game.getWinner())
                .gameData(buildGameData(game))
                .playerNames(names).build();
    }

    private Map<String, Object> buildGameData(WordChainGame game) {
        Map<String, Object> data = new HashMap<>();
        data.put("lastWord",    game.getLastWord());
        data.put("usedWords",   game.getUsedWords());
        data.put("eliminated",  game.getEliminated());
        data.put("timeLimit",   game.getTimeLimit());
        data.put("numPlayers",  game.getNumPlayers());
        data.put("currentTurn", game.getCurrentTurn());
        data.put("winner",      game.getWinner());
        return data;
    }
}
