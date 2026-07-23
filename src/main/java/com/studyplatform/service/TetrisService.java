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
    private static final int MAX_PROCESSED_ATTACK_KEYS = 500;
    private static final int MAX_ATTACK_LOG = 20;
    private static final int MAX_DISTRACT_EVENTS = 40;
    private static final int MAX_ATTACK_EVENTS_PER_SYNC = 64;
    private final TetrisRecordService recordService;

    public TetrisService(TetrisRecordService recordService) {
        this.recordService = recordService;
    }

    public StudyStateResponse processMove(Room room, Player player, StudyMoveRequest request) {
        if (!"TETRIS_SYNC".equals(request.getMoveType()) && !"TETRIS_PAUSE".equals(request.getMoveType()) && !"TETRIS_DISTRACT".equals(request.getMoveType())) {
            throw new IllegalArgumentException("Unknown TETRIS move.");
        }
        TetrisGame game = (TetrisGame) room.getGameData();
        if (game == null) {
            game = new TetrisGame(room.getPlayers().size());
            room.setGameData(game);
        }
        if ("TETRIS_PAUSE".equals(request.getMoveType())) {
            if (player.getPlayerIndex() != 0) {
                throw new IllegalArgumentException("Only the host can pause TETRIS.");
            }
            game.setPaused(readPaused(request.getPayload(), !game.isPaused()));
            return buildInitialState(room);
        }
        if ("TETRIS_DISTRACT".equals(request.getMoveType())) {
            distract(game, player.getPlayerIndex(), request.getPayload());
            return buildInitialState(room);
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
        gameData.put("instanceId", game.getInstanceId());
        gameData.put("playerStates", game.getPlayerStates());
        gameData.put("garbageQueues", game.getGarbageQueues());
        gameData.put("comboCounts", game.getComboCounts());
        gameData.put("lastAttackers", game.getLastAttackers());
        gameData.put("attackLog", game.getAttackLog());
        gameData.put("distractEvents", game.getDistractEvents());
        gameData.put("paused", game.isPaused());
        gameData.put("aborted", game.isAborted());
        gameData.put("abortReason", game.getAbortReason());
        gameData.put("previousAbortReason", game.getPreviousAbortReason());
        gameData.put("finalRanking", game.getFinalRanking());

        String[] names = room.getPlayers().stream().map(Player::getNickname).toArray(String[]::new);
        gameData.put("records", recordService.recordsFor(List.of(names)));
        return StudyStateResponse.builder()
                .roomId(room.getRoomId())
                .studyType(StudyType.TETRIS)
                .status(room.getStatus())
                .message(game.isAborted()
                        ? "TETRIS_ABORTED: " + game.getAbortReason()
                        : room.getStatus() == StudyStatus.FINISHED
                        ? "TETRIS queue monitor finished."
                        : "TETRIS queue monitor synced.")
                .currentTurn(0)
                .winner(game.getWinner())
                .gameData(gameData)
                .playerNames(names)
                .build();
    }

    @SuppressWarnings("unchecked")
    private void syncState(TetrisGame game, int playerIndex, Object payload) {
        if (!(payload instanceof Map<?, ?> map)) return;
        if (!(map.get("instanceId") instanceof String instanceId)
                || !game.getInstanceId().equals(instanceId)) {
            return;
        }

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

        ackAttacks(game, playerIndex, map);
        handleAttacks(game, playerIndex, map);
    }

    private void handleAttacks(TetrisGame game, int playerIndex, Map<?, ?> map) {
        Object attackEvents = map.get("attackEvents");
        if (attackEvents instanceof List<?> events) {
            for (Object event : events.stream().limit(MAX_ATTACK_EVENTS_PER_SYNC).toList()) {
                if (event instanceof Map<?, ?> attack) {
                    handleAttack(game, playerIndex, attack);
                }
            }
            return;
        }
        handleAttack(game, playerIndex, map);
    }

    private void ackAttacks(TetrisGame game, int playerIndex, Map<?, ?> map) {
        Object ackAttackIds = map.get("ackAttackIds");
        if (!(ackAttackIds instanceof List<?> ids) || ids.isEmpty()) return;
        List<Map<String, Object>> queue = game.getGarbageQueues().get(playerIndex);
        if (queue == null || queue.isEmpty()) return;
        queue.removeIf(attack -> ids.contains(attack.get("attackId")));
    }

    private void handleAttack(TetrisGame game, int playerIndex, Map<?, ?> map) {
        TetrisPlayerState attackerState = game.getPlayerStates().get(playerIndex);
        if (attackerState == null || attackerState.isGameOver()) return;
        int lastCleared = toInt(map.get("lastCleared"), 0);
        String attackKey = map.get("attackKey") instanceof String value ? value : "";
        if (attackKey.isBlank() || !game.getProcessedAttackKeys().add(attackKey)) return;
        rememberAttackKey(game, attackKey);

        if (lastCleared <= 0) {
            game.getComboCounts().put(playerIndex, 0);
            return;
        }

        int nextCombo = game.getComboCounts().getOrDefault(playerIndex, 0) + 1;
        game.getComboCounts().put(playerIndex, nextCombo);
        boolean tspin = toBoolean(map.get("tspin"), false);
        boolean b2b = toBoolean(map.get("b2b"), false);
        boolean perfectClear = toBoolean(map.get("perfectClear"), false);
        int maxAttackLines = attackLines(lastCleared, nextCombo, tspin, b2b, perfectClear);
        int claimedAttackLines = toInt(map.get("attackLines"), maxAttackLines);
        int attackLines = Math.max(0, Math.min(maxAttackLines, Math.min(12, claimedAttackLines)));
        if (attackLines <= 0) return;

        List<Integer> aliveTargets = game.getPlayerStates().entrySet().stream()
                .filter(entry -> entry.getKey() != playerIndex)
                .filter(entry -> !entry.getValue().isGameOver())
                .map(Map.Entry::getKey)
                .sorted()
                .toList();
        if (aliveTargets.isEmpty()) return;

        int target = selectTarget(game, playerIndex, aliveTargets);
        String targetAttackId = attackKey + ":" + target;
        game.getGarbageQueues()
                .computeIfAbsent(target, ignored -> new java.util.concurrent.CopyOnWriteArrayList<>())
                .add(Map.of(
                        "attackId", targetAttackId,
                        "from", playerIndex,
                        "lines", attackLines,
                        "combo", nextCombo,
                        "cleared", lastCleared
                ));
        game.getLastAttackers().put(target, playerIndex);
        Map<String, Object> log = new HashMap<>();
        log.put("attackId", targetAttackId);
        log.put("from", playerIndex);
        log.put("to", target);
        log.put("lines", attackLines);
        log.put("combo", nextCombo);
        log.put("cleared", lastCleared);
        log.put("tspin", tspin);
        log.put("b2b", b2b);
        log.put("perfectClear", perfectClear);
        log.put("timestamp", System.currentTimeMillis());
        rememberAttackLog(game, log);
    }

    private int selectTarget(TetrisGame game, int playerIndex, List<Integer> aliveTargets) {
        int lastAttacker = game.getLastAttackers().getOrDefault(playerIndex, -1);
        if (aliveTargets.contains(lastAttacker)) {
            return lastAttacker;
        }

        int cursor = game.getTargetCursors().getOrDefault(playerIndex, 0);
        int target = aliveTargets.get(Math.floorMod(cursor, aliveTargets.size()));
        game.getTargetCursors().put(playerIndex, cursor + 1);
        return target;
    }

    private void distract(TetrisGame game, int from, Object payload) {
        if (!(payload instanceof Map<?, ?> map)) return;
        int target = toInt(map.get("target"), -1);
        if (target < 0 || target == from) return;
        TetrisPlayerState targetState = game.getPlayerStates().get(target);
        if (targetState == null || targetState.isGameOver()) return;
        Map<String, Object> event = new HashMap<>();
        long now = System.currentTimeMillis();
        event.put("eventId", "shake:" + from + ":" + target + ":" + now + ":" + game.getDistractEvents().size());
        event.put("type", "shake");
        event.put("from", from);
        event.put("target", target);
        event.put("timestamp", now);
        game.getDistractEvents().add(event);
        while (game.getDistractEvents().size() > MAX_DISTRACT_EVENTS) {
            game.getDistractEvents().remove(0);
        }
    }

    private void rememberAttackKey(TetrisGame game, String attackKey) {
        game.getProcessedAttackOrder().add(attackKey);
        while (game.getProcessedAttackOrder().size() > MAX_PROCESSED_ATTACK_KEYS) {
            String removed = game.getProcessedAttackOrder().remove(0);
            game.getProcessedAttackKeys().remove(removed);
        }
    }

    private void rememberAttackLog(TetrisGame game, Map<String, Object> log) {
        game.getAttackLog().add(log);
        while (game.getAttackLog().size() > MAX_ATTACK_LOG) {
            game.getAttackLog().remove(0);
        }
    }

    private int attackLines(int cleared, int combo, boolean tspin, boolean b2b, boolean perfectClear) {
        int base = tspin
                ? switch (cleared) {
                    case 1 -> 2;
                    case 2 -> 4;
                    case 3, 4 -> 6;
                    default -> 0;
                }
                : switch (cleared) {
                    case 1 -> 0;
                    case 2 -> 1;
                    case 3 -> 2;
                    case 4 -> 4;
                    default -> 0;
                };
        int b2bBonus = b2b && (tspin || cleared >= 4) ? 1 : 0;
        int comboBonus = Math.min(4, Math.max(0, combo - 1));
        int perfectBonus = perfectClear ? 6 : 0;
        return base + b2bBonus + comboBonus + perfectBonus;
    }

    private void updateWinner(Room room, TetrisGame game, int playerIndex) {
        TetrisPlayerState state = game.getPlayerStates().get(playerIndex);
        if (state == null || !state.isGameOver() || game.getWinner() != -1 || game.isAborted()) return;
        if (!game.getEliminationOrder().contains(playerIndex)) {
            game.getEliminationOrder().add(playerIndex);
        }

        List<Integer> alive = game.getPlayerStates().entrySet().stream()
                .filter(entry -> !entry.getValue().isGameOver())
                .map(Map.Entry::getKey)
                .sorted()
                .toList();
        if (alive.size() == 1) {
            int winner = alive.get(0);
            game.setWinner(winner);
            game.getFinalRanking().clear();
            game.getFinalRanking().add(winner);
            for (int index = game.getEliminationOrder().size() - 1; index >= 0; index -= 1) {
                game.getFinalRanking().add(game.getEliminationOrder().get(index));
            }
            room.setStatus(StudyStatus.FINISHED);
            saveRecord(room, game);
        } else if (alive.isEmpty()) {
            room.setStatus(StudyStatus.FINISHED);
        }
    }

    private void saveRecord(Room room, TetrisGame game) {
        if (game.isRecordSaved() || game.isAborted() || game.getFinalRanking().size() < 2) return;
        List<String> ranking = game.getFinalRanking().stream()
                .map(index -> room.getPlayers().stream()
                        .filter(player -> player.getPlayerIndex() == index)
                        .findFirst()
                        .map(Player::getNickname)
                        .orElse(""))
                .toList();
        if (ranking.stream().anyMatch(String::isBlank)) return;
        recordService.recordCompletedMatch(game.getInstanceId(), ranking);
        game.setRecordSaved(true);
    }

    private int toInt(Object value, int fallback) {
        if (value instanceof Number number) return number.intValue();
        return fallback;
    }

    private boolean toBoolean(Object value, boolean fallback) {
        if (value instanceof Boolean bool) return bool;
        return fallback;
    }

    private boolean readPaused(Object payload, boolean fallback) {
        if (payload instanceof Map<?, ?> map) {
            return toBoolean(map.get("paused"), fallback);
        }
        return fallback;
    }
}
