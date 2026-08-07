package com.studyplatform.service;

import com.studyplatform.dto.request.StudyMoveRequest;
import com.studyplatform.dto.response.StudyStateResponse;
import com.studyplatform.model.Player;
import com.studyplatform.model.Room;
import com.studyplatform.model.StudyStatus;
import com.studyplatform.model.StudyType;
import com.studyplatform.model.applebox.AppleBoxGame;
import com.studyplatform.model.applebox.AppleBoxPlayerState;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 사과게임(APPLE_BOX) 서비스
 *
 * ─── 권한 분리 ─────────────────────────────────────────────────────────────
 * 보드와 남은 시간, 점수는 서버가 가진다. 클라이언트는 드래그한 사각 범위만
 * APPLE_CLEAR로 보내고, 서버가 다시 합이 10인지 검증한 뒤 점수를 올린다.
 * 그래서 클라이언트를 조작해도 없앤 칸 수를 부풀릴 수 없다.
 *
 * ─── moveType ─────────────────────────────────────────────────────────────
 *   APPLE_CLEAR  — payload { r1, c1, r2, c2 } 사각 범위 정리 시도
 *   APPLE_FINISH — 내 제한 시간이 끝났음을 알림 (모두 끝나면 순위 확정)
 */
@Service
public class AppleBoxService {
    /** 통신 지연을 감안해 제한 시간이 지난 직후 이만큼은 정리를 더 받아준다. */
    private static final long CLEAR_GRACE_SECONDS = 2;
    private final AppleBoxRecordService recordService;

    public AppleBoxService(AppleBoxRecordService recordService) {
        this.recordService = recordService;
    }

    public StudyStateResponse processMove(Room room, Player player, StudyMoveRequest request) {
        String moveType = request.getMoveType();
        if (!"APPLE_CLEAR".equals(moveType) && !"APPLE_FINISH".equals(moveType)) {
            throw new IllegalArgumentException("Unknown APPLE_BOX move.");
        }
        // 시작 전(WAITING/SETUP)이나 종료 후에 들어온 동작은 무시한다
        if (room.getStatus() != StudyStatus.PLAYING) return buildInitialState(room);

        AppleBoxGame game = game(room);
        AppleBoxPlayerState state = game.getPlayerStates()
                .computeIfAbsent(player.getPlayerIndex(), ignored -> new AppleBoxPlayerState());

        if ("APPLE_FINISH".equals(moveType)) {
            state.setFinished(true);
            state.setUpdatedAt(System.currentTimeMillis());
        } else {
            applyClear(game, state, request.getPayload());
        }

        settle(room, game);
        return buildInitialState(room);
    }

    /**
     * 사각 범위 정리 시도.
     *
     * 합이 10이 아니거나 범위가 잘못됐으면 조용히 무시한다 — 빗나간 드래그는
     * 정상적인 플레이 과정이므로 에러로 알릴 대상이 아니다.
     */
    private void applyClear(AppleBoxGame game, AppleBoxPlayerState state, Object payload) {
        if (state.isFinished()) return;
        if (game.elapsedSeconds() > game.getDurationSeconds() + CLEAR_GRACE_SECONDS) return;
        if (!(payload instanceof Map<?, ?> map)) return;

        game.tryClear(state,
                toInt(map.get("r1"), -1), toInt(map.get("c1"), -1),
                toInt(map.get("r2"), -1), toInt(map.get("c2"), -1));
    }

    /** 제한 시간이 끝났거나 모두 종료했으면 순위를 확정하고 기록을 남긴다. */
    private void settle(Room room, AppleBoxGame game) {
        // 게임이 실제로 진행 중일 때만 정산한다.
        // 이 검사가 없으면 방에서 대기하는 동안에도 제한 시간이 흘러, 방장이 시작을
        // 누르기 전에 방이 FINISHED가 되고 0점짜리 기록까지 남는다.
        if (room.getStatus() != StudyStatus.PLAYING) return;

        boolean timeUp = game.timeUp();
        if (timeUp) {
            game.getPlayerStates().values().forEach(state -> state.setFinished(true));
        }
        boolean everyoneDone = !game.getPlayerStates().isEmpty()
                && game.getPlayerStates().values().stream().allMatch(AppleBoxPlayerState::isFinished);
        if (!timeUp && !everyoneDone) return;

        List<Integer> ranking = game.getPlayerStates().entrySet().stream()
                .sorted(Comparator
                        .comparingInt((Map.Entry<Integer, AppleBoxPlayerState> entry) -> -entry.getValue().getScore())
                        .thenComparingLong(entry -> entry.getValue().getUpdatedAt())
                        .thenComparingInt(Map.Entry::getKey))
                .map(Map.Entry::getKey)
                .toList();

        game.getFinalRanking().clear();
        game.getFinalRanking().addAll(ranking);
        game.setWinner(ranking.isEmpty() ? -1 : ranking.get(0));
        room.setStatus(StudyStatus.FINISHED);
        saveRecord(room, game);
    }

    private void saveRecord(Room room, AppleBoxGame game) {
        if (game.isRecordSaved()) return;
        Map<String, Integer> scores = new LinkedHashMap<>();
        for (Player player : room.getPlayers()) {
            AppleBoxPlayerState state = game.getPlayerStates().get(player.getPlayerIndex());
            if (state == null) continue;
            String nickname = player.getNickname() == null ? "" : player.getNickname().trim();
            if (nickname.isBlank()) continue;
            scores.put(nickname, state.getScore());
        }
        if (scores.isEmpty()) return;
        recordService.recordScores(game.getInstanceId(), scores);
        game.setRecordSaved(true);
    }

    public StudyStateResponse buildInitialState(Room room) {
        AppleBoxGame game = game(room);
        // 아무도 움직이지 않아도 시간이 다 되면 끝나야 하므로 조회 시에도 정산한다.
        settle(room, game);

        Map<Integer, Map<String, Object>> playerStates = new LinkedHashMap<>();
        game.getPlayerStates().forEach((index, state) -> {
            Map<String, Object> value = new HashMap<>();
            value.put("score", state.getScore());
            value.put("finished", state.isFinished());
            value.put("cleared", new ArrayList<>(state.getCleared()));
            playerStates.put(index, value);
        });

        String[] names = room.getPlayers().stream().map(Player::getNickname).toArray(String[]::new);

        Map<String, Object> gameData = new HashMap<>();
        gameData.put("rows", AppleBoxGame.ROWS);
        gameData.put("cols", AppleBoxGame.COLS);
        gameData.put("target", AppleBoxGame.TARGET);
        gameData.put("board", game.getBoard());
        gameData.put("numPlayers", game.getNumPlayers());
        gameData.put("instanceId", game.getInstanceId());
        gameData.put("durationSeconds", game.getDurationSeconds());
        // 시작 전에는 시계가 흐르지 않게 제한 시간 전체를 보낸다
        boolean beforeStart = room.getStatus() == StudyStatus.WAITING || room.getStatus() == StudyStatus.SETUP;
        gameData.put("remainingSeconds", beforeStart ? game.getDurationSeconds() : game.remainingSeconds());
        gameData.put("playerStates", playerStates);
        gameData.put("finalRanking", game.getFinalRanking());
        gameData.put("records", recordService.recordsFor(List.of(names)));
        gameData.put("leaderboard", recordService.leaderboard(10));
        gameData.put("weeklyLeaderboard", recordService.weeklyLeaderboard(10));
        gameData.put("weekStart", recordService.currentWeekStart());

        return StudyStateResponse.builder()
                .roomId(room.getRoomId())
                .studyType(StudyType.APPLE_BOX)
                .status(room.getStatus())
                .message(room.getStatus() == StudyStatus.FINISHED
                        ? "APPLE_BOX 대조 작업이 끝났습니다."
                        : "APPLE_BOX 대조 작업 진행 중입니다.")
                .currentTurn(0)
                .winner(game.getWinner())
                .gameData(gameData)
                .playerNames(names)
                .build();
    }

    private AppleBoxGame game(Room room) {
        if (room.getGameData() instanceof AppleBoxGame game) return game;
        AppleBoxGame game = new AppleBoxGame(room.getPlayers().size());
        room.setGameData(game);
        return game;
    }

    private int toInt(Object value, int fallback) {
        if (value instanceof Number number) return number.intValue();
        return fallback;
    }
}
