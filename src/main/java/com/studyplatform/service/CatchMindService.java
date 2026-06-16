package com.studyplatform.service;

import com.studyplatform.dto.request.StudyMoveRequest;
import com.studyplatform.dto.response.StudyStateResponse;
import com.studyplatform.model.Player;
import com.studyplatform.model.Room;
import com.studyplatform.model.StudyStatus;
import com.studyplatform.model.StudyType;
import com.studyplatform.model.catchmind.CatchMindGame;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class CatchMindService {
    private static final int MAX_STROKES = 500;
    private static final int MAX_GUESSES = 8;

    public synchronized StudyStateResponse processMove(Room room, Player player, StudyMoveRequest request) {
        CatchMindGame game = getGame(room);
        String moveType = request.getMoveType();

        switch (moveType) {
            case "CATCHMIND_SET_WORD" -> setWord(game, player, request.getData());
            case "CATCHMIND_DRAW" -> draw(game, player, request.getPayload());
            case "CATCHMIND_CLEAR" -> clear(game, player);
            case "CATCHMIND_GUESS" -> guess(room, game, player, request.getData());
            case "CATCHMIND_NEXT" -> nextRound(room, game, player);
            default -> throw new IllegalArgumentException("Unknown CATCHMIND move.");
        }

        return buildInitialState(room);
    }

    public StudyStateResponse buildInitialState(Room room) {
        CatchMindGame game = getGame(room);
        Map<String, Object> gameData = new HashMap<>();
        gameData.put("numPlayers", game.getNumPlayers());
        gameData.put("round", game.getRound());
        gameData.put("maxRounds", game.getMaxRounds());
        gameData.put("currentTurn", game.getCurrentTurn());
        gameData.put("maskedWord", game.getMaskedWord());
        gameData.put("wordLength", game.getSecretWord() == null ? 0 : game.getSecretWord().length());
        gameData.put("scores", game.getScores());
        gameData.put("strokes", game.getStrokesSnapshot());
        gameData.put("recentGuesses", game.getRecentGuesses());
        gameData.put("roundSolved", game.isRoundSolved());
        gameData.put("solvedBy", game.getSolvedBy());
        gameData.put("revealedWord", game.isRoundSolved() ? game.getRevealedWord() : "");
        gameData.put("wordReady", game.getSecretWord() != null && !game.getSecretWord().isBlank());

        String[] names = room.getPlayers().stream().map(Player::getNickname).toArray(String[]::new);
        return StudyStateResponse.builder()
                .roomId(room.getRoomId())
                .studyType(StudyType.CATCHMIND)
                .status(room.getStatus())
                .message(buildMessage(room, game))
                .currentTurn(game.getCurrentTurn())
                .winner(game.getWinner())
                .gameData(gameData)
                .playerNames(names)
                .build();
    }

    public StudyStateResponse buildSecretState(Room room) {
        CatchMindGame game = getGame(room);
        Map<String, Object> gameData = new HashMap<>();
        gameData.put("secretWord", game.getSecretWord());
        gameData.put("round", game.getRound());
        gameData.put("currentTurn", game.getCurrentTurn());
        return StudyStateResponse.builder()
                .roomId(room.getRoomId())
                .studyType(StudyType.CATCHMIND)
                .status(room.getStatus())
                .currentTurn(game.getCurrentTurn())
                .winner(game.getWinner())
                .gameData(gameData)
                .build();
    }

    private CatchMindGame getGame(Room room) {
        CatchMindGame game = (CatchMindGame) room.getGameData();
        if (game == null) {
            game = new CatchMindGame(room.getPlayers().size());
            room.setGameData(game);
        }
        return game;
    }

    @SuppressWarnings("unchecked")
    private void draw(CatchMindGame game, Player player, Object payload) {
        if (player.getPlayerIndex() != game.getCurrentTurn()) {
            throw new IllegalStateException("Only the drawer can draw.");
        }
        if (game.getSecretWord() == null || game.getSecretWord().isBlank()) {
            throw new IllegalStateException("Set a word before drawing.");
        }
        if (game.isRoundSolved()) return;
        if (!(payload instanceof Map<?, ?> map)) return;
        if (game.getStrokes().size() >= MAX_STROKES) return;
        game.getStrokes().add((Map<String, Object>) map);
    }

    private void clear(CatchMindGame game, Player player) {
        if (player.getPlayerIndex() != game.getCurrentTurn()) {
            throw new IllegalStateException("Only the drawer can clear.");
        }
        game.getStrokes().clear();
    }

    private void setWord(CatchMindGame game, Player player, String rawWord) {
        if (player.getPlayerIndex() != game.getCurrentTurn()) {
            throw new IllegalStateException("Only the drawer can set the word.");
        }
        if (game.isRoundSolved()) {
            throw new IllegalStateException("Round is already solved.");
        }
        String word = rawWord == null ? "" : rawWord.trim();
        if (word.length() < 2) {
            throw new IllegalArgumentException("Word must be at least 2 characters.");
        }
        if (word.length() > 30) {
            throw new IllegalArgumentException("Word is too long.");
        }
        game.setSecretWord(word);
        game.setRevealedWord("");
        game.setSolvedBy(-1);
        game.getStrokes().clear();
        game.getRecentGuesses().clear();
    }

    private void guess(Room room, CatchMindGame game, Player player, String rawGuess) {
        if (player.getPlayerIndex() == game.getCurrentTurn()) {
            throw new IllegalStateException("Drawer cannot guess.");
        }
        if (game.isRoundSolved() || rawGuess == null) return;
        if (game.getSecretWord() == null || game.getSecretWord().isBlank()) {
            throw new IllegalStateException("The word is not set yet.");
        }
        String guess = rawGuess.trim();
        if (guess.isEmpty()) return;
        addGuess(game, player.getNickname() + ": " + guess);

        if (normalize(guess).equals(normalize(game.getSecretWord()))) {
            game.setRoundSolved(true);
            game.setSolvedBy(player.getPlayerIndex());
            game.setRevealedWord(game.getSecretWord());
            game.getScores()[player.getPlayerIndex()] += 10;
            game.getScores()[game.getCurrentTurn()] += 5;
            addGuess(game, player.getNickname() + " solved it.");
        }
    }

    private void nextRound(Room room, CatchMindGame game, Player player) {
        boolean host = player.getPlayerIndex() == 0;
        boolean drawer = player.getPlayerIndex() == game.getCurrentTurn();
        if (!host && !drawer) {
            throw new IllegalStateException("Only the host or drawer can skip.");
        }
        game.nextRound();
        if (game.getWinner() >= 0) {
            room.setStatus(StudyStatus.FINISHED);
        }
    }

    private void addGuess(CatchMindGame game, String text) {
        game.getRecentGuesses().add(text);
        while (game.getRecentGuesses().size() > MAX_GUESSES) {
            game.getRecentGuesses().remove(0);
        }
    }

    private String normalize(String value) {
        return value == null ? "" : value.replaceAll("\\s+", "").toLowerCase();
    }

    private String buildMessage(Room room, CatchMindGame game) {
        if (room.getStatus() == StudyStatus.FINISHED) return "CATCHMIND finished.";
        String drawer = room.getPlayers().isEmpty()
                ? "drawer"
                : room.getPlayers().get(game.getCurrentTurn()).getNickname();
        return "Round " + game.getRound() + "/" + game.getMaxRounds() + ". " + drawer + " is drawing.";
    }
}
