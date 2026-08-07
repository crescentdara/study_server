package com.studyplatform.model.applebox;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 사과게임(APPLE_BOX) 게임 상태
 *
 * ─── 규칙 ─────────────────────────────────────────────────────────────────
 * 10행 × 17열 = 170칸에 1~9가 채워진다. 플레이어는 사각 범위를 드래그해서
 * 그 안 숫자의 합이 정확히 10이면 해당 칸을 없앤다. 없어진 칸은 내려오지 않는다.
 * 제한 시간 안에 더 많은 칸을 없앤 사람이 이긴다.
 *
 * ─── 멀티플레이 방식 ───────────────────────────────────────────────────────
 * 모든 플레이어가 '같은 보드'(board)를 받고, 각자 없앤 칸은 플레이어별로
 * 따로 기록한다(AppleBoxPlayerState.cleared). 즉 서로 방해하지 않고 동일한
 * 문제를 풀어 점수로 경쟁한다.
 *
 * 보드 전체 합은 10의 배수가 되도록 보정하므로 이론상 전량 정리가 가능하다.
 */
@Data
public class AppleBoxGame {
    public static final int ROWS = 10;
    public static final int COLS = 17;
    public static final int CELL_COUNT = ROWS * COLS;
    public static final int TARGET = 10;

    private final int numPlayers;
    private final int durationSeconds;
    private final int[] board = new int[CELL_COUNT];
    private final Map<Integer, AppleBoxPlayerState> playerStates = new ConcurrentHashMap<>();
    private final List<Integer> finalRanking = new CopyOnWriteArrayList<>();
    private final String instanceId = UUID.randomUUID().toString();
    /** 방장이 시작한 시각(epoch ms). 남은 시간은 이 값을 기준으로 서버가 판정한다. */
    private final long startedAt = System.currentTimeMillis();
    private int winner = -1;
    private boolean recordSaved = false;

    public AppleBoxGame(int numPlayers) {
        this(numPlayers, 120);
    }

    public AppleBoxGame(int numPlayers, int durationSeconds) {
        this.numPlayers = Math.max(1, numPlayers);
        this.durationSeconds = Math.max(30, durationSeconds);
        fillBoard();
        for (int index = 0; index < this.numPlayers; index += 1) {
            playerStates.put(index, new AppleBoxPlayerState());
        }
    }

    /** 1~9 무작위로 채운 뒤, 전체 합이 10의 배수가 되도록 몇 칸을 올려 보정한다. */
    private void fillBoard() {
        java.util.Random random = new java.util.Random();
        int total = 0;
        for (int index = 0; index < CELL_COUNT; index += 1) {
            board[index] = 1 + random.nextInt(9);
            total += board[index];
        }
        int deficit = (TARGET - (total % TARGET)) % TARGET;
        for (int guard = 0; deficit > 0 && guard < 5000; guard += 1) {
            int index = random.nextInt(CELL_COUNT);
            int room = 9 - board[index];
            if (room <= 0) continue;
            int add = Math.min(room, deficit);
            board[index] += add;
            deficit -= add;
        }
    }

    /**
     * 사각 범위 정리 시도 — 범위 안에 남아 있는 칸들의 합이 정확히 TARGET이면 지운다.
     *
     * 방 대전(AppleBoxService)과 혼자 하기(AppleSoloService)가 같은 판정을 쓰도록
     * 규칙 자체는 여기에 둔다. 제한 시간 같은 정책은 각 서비스가 판단한다.
     *
     * @return 지운 칸 수. 조건이 맞지 않으면 0 (빗나간 드래그는 정상적인 플레이다)
     */
    public int tryClear(AppleBoxPlayerState state, int r1, int c1, int r2, int c2) {
        int top = Math.min(r1, r2), bottom = Math.max(r1, r2);
        int left = Math.min(c1, c2), right = Math.max(c1, c2);
        if (top < 0 || left < 0 || bottom >= ROWS || right >= COLS) return 0;

        List<Integer> targets = new ArrayList<>();
        int sum = 0;
        for (int row = top; row <= bottom; row += 1) {
            for (int column = left; column <= right; column += 1) {
                int index = row * COLS + column;
                if (state.getCleared().contains(index)) continue;
                targets.add(index);
                sum += board[index];
                if (sum > TARGET) return 0; // 초과하면 더 볼 필요 없음
            }
        }
        if (targets.isEmpty() || sum != TARGET) return 0;

        state.getCleared().addAll(targets);
        state.setScore(state.getCleared().size());
        state.setUpdatedAt(System.currentTimeMillis());
        if (state.getCleared().size() >= CELL_COUNT) {
            state.setFinished(true);
        }
        return targets.size();
    }

    /** 시작 후 경과 초 */
    public long elapsedSeconds() {
        return (System.currentTimeMillis() - startedAt) / 1000L;
    }

    /** 남은 초 (0 이하로는 내려가지 않음) */
    public int remainingSeconds() {
        return (int) Math.max(0, durationSeconds - elapsedSeconds());
    }

    public boolean timeUp() {
        return remainingSeconds() <= 0;
    }
}
