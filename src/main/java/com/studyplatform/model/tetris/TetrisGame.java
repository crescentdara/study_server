package com.studyplatform.model.tetris;

import lombok.Data;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Data
public class TetrisGame {
    private final int rows = 20;
    private final int cols = 10;
    private final int numPlayers;
    private final Map<Integer, TetrisPlayerState> playerStates = new ConcurrentHashMap<>();
    private final Map<Integer, List<Map<String, Object>>> garbageQueues = new ConcurrentHashMap<>();
    private final Map<Integer, Integer> comboCounts = new ConcurrentHashMap<>();
    private final Map<Integer, Integer> lastAttackers = new ConcurrentHashMap<>();
    private final Map<Integer, Integer> targetCursors = new ConcurrentHashMap<>();
    private final List<Map<String, Object>> attackLog = new CopyOnWriteArrayList<>();
    private final List<Map<String, Object>> distractEvents = new CopyOnWriteArrayList<>();
    private final List<Integer> eliminationOrder = new CopyOnWriteArrayList<>();
    private final List<Integer> finalRanking = new CopyOnWriteArrayList<>();
    private final Set<Integer> readyPlayers = ConcurrentHashMap.newKeySet();
    private final Set<String> processedAttackKeys = ConcurrentHashMap.newKeySet();
    private final List<String> processedAttackOrder = new CopyOnWriteArrayList<>();
    private final String instanceId = UUID.randomUUID().toString();
    private int winner = -1;
    private boolean paused = false;
    private boolean aborted = false;
    private String abortReason = "";
    private String previousAbortReason = "";
    private boolean recordSaved = false;

    /* ── 서바이벌 전용 ──────────────────────────────────────────────────────
     * 여러 명이 같은 조건에서 버티는 경기가 되려면 '언제 몇 번째 줄이 올라오는지'와
     * '그 줄의 구멍이 어디인지'가 모두에게 같아야 한다. 그래서 시계와 구멍 순서를
     * 서버가 들고 있고, 클라이언트는 그걸 그대로 따라 그린다.
     * ─────────────────────────────────────────────────────────────────── */
    /** 서바이벌 기준 시계의 원점 */
    private final long startedAt = System.currentTimeMillis();
    /** 일시정지 중이면 그 시각, 진행 중이면 0 */
    private long pausedAt = 0;
    /** 지금까지 멈춰 있던 시간 합계 */
    private long pausedTotalMs = 0;
    /** 쓰레기 줄의 구멍 열 순서 — n번째로 올라오는 줄은 garbageHoles[n]을 쓴다 */
    private final List<Integer> garbageHoles = new java.util.ArrayList<>();
    /** 죽은 순간(또는 최후 생존 확정 순간)에 서버가 찍는 참가자별 결과 */
    private final Map<Integer, Map<String, Object>> survivalResults = new ConcurrentHashMap<>();

    private static final int GARBAGE_HOLE_SEQUENCE = 400;

    public TetrisGame(int numPlayers) {
        this.numPlayers = numPlayers;
        for (int i = 0; i < numPlayers; i += 1) {
            playerStates.put(i, new TetrisPlayerState());
            garbageQueues.put(i, new CopyOnWriteArrayList<>());
            comboCounts.put(i, 0);
            lastAttackers.put(i, -1);
            targetCursors.put(i, 0);
        }
        fillGarbageHoles();
    }

    /**
     * 구멍 열 순서를 미리 뽑아 둔다.
     *
     * 같은 열이 연달아 나오지 않게 해서 세로 통로가 생기지 않도록 한다 —
     * 클라이언트의 pickGarbageHole과 같은 규칙이다.
     */
    private void fillGarbageHoles() {
        java.util.Random random = new java.util.Random();
        int previous = -1;
        for (int index = 0; index < GARBAGE_HOLE_SEQUENCE; index += 1) {
            int hole = previous < 0
                    ? random.nextInt(cols)
                    : (previous + 1 + random.nextInt(cols - 1)) % cols;
            garbageHoles.add(hole);
            previous = hole;
        }
    }

    /** 멈춰 있던 시간을 뺀 실제 진행 경과 (모든 참가자가 이 값을 공유한다) */
    public long survivalElapsedMs() {
        long now = System.currentTimeMillis();
        long paused = pausedTotalMs + (pausedAt > 0 ? now - pausedAt : 0);
        return Math.max(0, now - startedAt - paused);
    }

    /** 일시정지 상태를 바꾸면서 멈춘 시간을 누적한다 */
    public void applyPause(boolean nextPaused) {
        long now = System.currentTimeMillis();
        if (nextPaused && pausedAt == 0) {
            pausedAt = now;
        } else if (!nextPaused && pausedAt > 0) {
            pausedTotalMs += now - pausedAt;
            pausedAt = 0;
        }
        this.paused = nextPaused;
    }
}
