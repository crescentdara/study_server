package com.studyplatform.service;

import com.studyplatform.model.applebox.AppleBoxGame;
import com.studyplatform.model.applebox.AppleBoxPlayerState;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 사과게임 혼자 하기(APPLE_BOX 솔로)
 *
 * ─── 방을 쓰지 않는 이유 ───────────────────────────────────────────────────
 * 사과게임은 상대를 기다릴 필요가 없다. 버튼을 누르면 그 순간 보드가 만들어지고
 * 바로 시작한다. 그래서 방(Room)·정원(maxPlayers)·시작 버튼 같은 개념이 없고,
 * 판이 곧 세션이다. '시작 전에 시계가 흐르는' 문제도 구조적으로 생기지 않는다.
 *
 * ─── 점수는 개인, 랭킹은 전체 공유 ─────────────────────────────────────────
 * 한 판의 점수는 그 사람만의 것이지만, 끝나면 AppleBoxRecordService에 저장돼
 * 닉네임별 최고 점수 랭킹으로 모두에게 공유된다(테트리스 전적과 같은 방식으로
 * JSON 파일에 남아 서버를 다시 켜도 유지된다).
 *
 * ─── 판정은 서버가 한다 ────────────────────────────────────────────────────
 * 클라이언트는 드래그한 사각 범위만 보내고, 합이 10인지는 서버가 다시 검증한다.
 * 따라서 클라이언트를 조작해도 점수를 부풀릴 수 없다.
 */
@Service
public class AppleSoloService {
    /** 통신 지연을 감안해 제한 시간이 지난 직후 이만큼은 정리를 더 받아준다. */
    private static final long CLEAR_GRACE_SECONDS = 2;
    /** 브라우저를 그냥 닫은 판이 영구히 남지 않도록 정리하는 기준 */
    private static final long SESSION_TTL_MS = 60 * 60 * 1000L;
    private static final int MAX_SESSIONS = 2_000;

    private final AppleBoxRecordService recordService;
    private final Map<String, SoloSession> sessions = new ConcurrentHashMap<>();

    public AppleSoloService(AppleBoxRecordService recordService) {
        this.recordService = recordService;
    }

    /** 새 판 시작 — 누른 순간 보드가 만들어지므로 대기 상태가 없다. */
    public Map<String, Object> start(String nickname) {
        pruneStaleSessions();
        SoloSession session = new SoloSession(displayName(nickname), new AppleBoxGame(1));
        sessions.put(session.game.getInstanceId(), session);
        return snapshot(session);
    }

    /** 사각 범위 정리 시도 — 퍼즈 중에는 시도 자체를 받지 않는다 */
    public Map<String, Object> clear(String instanceId, int r1, int c1, int r2, int c2) {
        SoloSession session = session(instanceId);
        synchronized (session) {
            boolean tooLate = session.game.elapsedSeconds()
                    > session.game.getDurationSeconds() + CLEAR_GRACE_SECONDS;
            if (!session.state.isFinished() && !tooLate) {
                session.game.tryClear(session.state, r1, c1, r2, c2);
            }
            settleIfDone(session);
            return snapshot(session);
        }
    }

    /**
     * 퍼즈 전환 — P키로 화면을 가리는 동안 시계도 진짜로 멈춘다.
     *
     * 끝난 판은 멈출 이유가 없으므로 무시한다. 판이 끝난 뒤에 뒤늦게 도착한 퍼즈
     * 요청 때문에 이미 확정된 기록이 흔들리는 일이 없도록 하기 위함이다.
     */
    /** 제한 시간이 끝났거나 그만두었음을 알린다 — 이 시점에 랭킹에 기록된다. */
    public Map<String, Object> finish(String instanceId) {
        SoloSession session = session(instanceId);
        synchronized (session) {
            session.state.setFinished(true);
            session.state.setUpdatedAt(System.currentTimeMillis());
            settleIfDone(session);
            return snapshot(session);
        }
    }

    public Map<String, Object> state(String instanceId) {
        SoloSession session = session(instanceId);
        synchronized (session) {
            settleIfDone(session);
            return snapshot(session);
        }
    }

    /** 현재 상태 조회 (새로 고침 후 이어보기용) */
    private void settleIfDone(SoloSession session) {
        if (session.game.timeUp()) {
            session.state.setFinished(true);
        }
        if (!session.state.isFinished() || session.recorded) return;
        session.recorded = true;

        // 한 칸도 정리하지 못한 판은 기록되지 않는다(AppleBoxRecordService의 규칙).
        // 보드만 보고 새로 시작하는 판이 대부분 여기에 해당하므로 세션도 바로 비운다.
        if (session.state.getScore() <= 0) {
            sessions.remove(session.game.getInstanceId());
            return;
        }
        // 같은 판이 두 번 집계되지 않도록 instanceId를 키로 쓴다
        recordService.recordScores(
                session.game.getInstanceId(),
                Map.of(session.nickname, session.state.getScore())
        );
    }

    private Map<String, Object> snapshot(SoloSession session) {
        AppleBoxGame game = session.game;
        AppleBoxPlayerState state = session.state;

        Map<String, Object> playerState = new LinkedHashMap<>();
        playerState.put("score", state.getScore());
        playerState.put("finished", state.isFinished());
        playerState.put("cleared", new ArrayList<>(state.getCleared()));

        Map<String, Object> gameData = new LinkedHashMap<>();
        gameData.put("rows", AppleBoxGame.ROWS);
        gameData.put("cols", AppleBoxGame.COLS);
        gameData.put("target", AppleBoxGame.TARGET);
        gameData.put("board", game.getBoard());
        gameData.put("numPlayers", 1);
        gameData.put("instanceId", game.getInstanceId());
        gameData.put("durationSeconds", game.getDurationSeconds());
        gameData.put("remainingSeconds", game.remainingSeconds());
        gameData.put("playerStates", Map.of(0, playerState));
        gameData.put("finalRanking", state.isFinished() ? List.of(0) : List.of());
        gameData.put("leaderboard", recordService.leaderboard(10));
        gameData.put("weeklyLeaderboard", recordService.weeklyLeaderboard(10));
        gameData.put("weekStart", recordService.currentWeekStart());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("instanceId", game.getInstanceId());
        result.put("nickname", session.nickname);
        result.put("finished", state.isFinished());
        result.put("score", state.getScore());
        result.put("gameData", gameData);
        return result;
    }

    private SoloSession session(String instanceId) {
        SoloSession session = instanceId == null ? null : sessions.get(instanceId);
        if (session == null) throw new IllegalArgumentException("Unknown APPLE_BOX solo session.");
        return session;
    }

    /**
     * 오래된 판을 걷어낸다.
     *
     * 끝났는지 여부와 무관하게 TTL이 지난 판은 지운다. 그래도 넘치면 가장 오래된
     * 것부터 버린다 — 시작만 하고 닫은 판이 메모리에 계속 쌓이지 않도록.
     */
    private void pruneStaleSessions() {
        long now = System.currentTimeMillis();
        sessions.entrySet().removeIf(entry -> now - entry.getValue().createdAt > SESSION_TTL_MS);
        if (sessions.size() <= MAX_SESSIONS) return;
        sessions.entrySet().stream()
                .sorted(java.util.Comparator.comparingLong(entry -> entry.getValue().createdAt))
                .limit(sessions.size() - MAX_SESSIONS)
                .map(Map.Entry::getKey)
                .toList()
                .forEach(sessions::remove);
    }

    private String displayName(String nickname) {
        String trimmed = nickname == null ? "" : nickname.trim();
        return trimmed.isBlank() ? "익명" : trimmed;
    }

    private static class SoloSession {
        private final String nickname;
        private final AppleBoxGame game;
        private final AppleBoxPlayerState state = new AppleBoxPlayerState();
        private final long createdAt = System.currentTimeMillis();
        private boolean recorded;

        private SoloSession(String nickname, AppleBoxGame game) {
            this.nickname = nickname;
            this.game = game;
        }
    }
}
