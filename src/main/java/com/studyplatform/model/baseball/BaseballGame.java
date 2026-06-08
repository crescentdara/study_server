package com.studyplatform.model.baseball;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 숫자야구 게임 데이터 (N명 지원)
 *
 * ─── 멀티플레이어 규칙 ──────────────────────────────────────────
 * - 각 플레이어가 비밀 숫자 설정
 * - 턴 순서: 0 → 1 → 2 → ... → N-1 → 0 (순환)
 * - 자기 턴에 "오른쪽(다음 순서)" 플레이어의 비밀 숫자를 추측
 *   예) 3명: 플레이어0 → 플레이어1 숫자 추측
 *            플레이어1 → 플레이어2 숫자 추측
 *            플레이어2 → 플레이어0 숫자 추측
 * - digits Strike 먼저 → 승리
 */
@Data
public class BaseballGame {

    private int digits;
    private int numPlayers;

    private String[] secrets;
    private boolean[] secretSet;

    /**
     * 각 플레이어의 추측 기록
     * guessHistories[i] = 플레이어 i 가 한 추측들
     */
    private List<GuessResult>[] guessHistories;

    private int currentTurn = 0;
    private int winner = -1;

    @SuppressWarnings("unchecked")
    public BaseballGame(int digits, int numPlayers) {
        this.digits = digits;
        this.numPlayers = numPlayers;
        this.secrets = new String[numPlayers];
        this.secretSet = new boolean[numPlayers];
        this.guessHistories = new List[numPlayers];
        for (int i = 0; i < numPlayers; i++) {
            guessHistories[i] = new ArrayList<>();
        }
    }

    /** 모든 플레이어가 비밀 숫자를 설정했는지 */
    public boolean allSecretsSet() {
        for (boolean b : secretSet) if (!b) return false;
        return true;
    }

    /** Strike/Ball 계산 */
    public GuessResult calculateResult(String guess, String secret) {
        int strikes = 0, balls = 0;
        for (int i = 0; i < digits; i++) {
            char g = guess.charAt(i);
            if (g == secret.charAt(i)) strikes++;
            else if (secret.indexOf(g) >= 0) balls++;
        }
        return new GuessResult(guess, strikes, balls);
    }

    /**
     * 추측 처리
     *
     * 플레이어 playerIndex 가 (playerIndex + 1) % numPlayers 의 비밀 숫자를 추측
     */
    public GuessResult addGuess(int playerIndex, String guess) {
        // 오른쪽(다음 순서) 플레이어의 비밀 숫자가 정답
        int targetIndex = (playerIndex + 1) % numPlayers;
        GuessResult result = calculateResult(guess, secrets[targetIndex]);
        guessHistories[playerIndex].add(result);

        if (result.getStrikes() == digits) {
            winner = playerIndex;  // 정답 → 승리
        } else {
            currentTurn = (currentTurn + 1) % numPlayers;  // 다음 턴
        }
        return result;
    }
}
