package com.studyplatform.model.bingo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 빙고 게임 데이터 (N명, 텍스트 주제 기반)
 *
 * ─── 흐름 ──────────────────────────────────────────────────────
 * 1. SETUP 단계: 각 플레이어가 size×size 셀에 주제 텍스트 입력
 * 2. 모두 완료 → PLAYING 단계 자동 전환
 * 3. 번갈아 주제를 호출 → 모든 보드에서 일치하는 셀 마킹
 * 4. 승리 조건(줄 수) 먼저 달성 → 승리
 */
@Data
public class BingoGame {

    private int size;
    private int numPlayers;
    private int winBingoCount;

    private BingoBoard[] boards;
    /** 각 플레이어의 보드 설정 완료 여부 */
    private boolean[] boardsSet;

    private int currentTurn = 0;
    /** 지금까지 호출된 주제 목록 */
    private List<String> calledTopics = new ArrayList<>();
    private int winner = -1;

    public BingoGame(int size, int numPlayers) {
        this.size          = size;
        this.numPlayers    = numPlayers;
        this.winBingoCount = (size == 3) ? 2 : 3;
        this.boards        = new BingoBoard[numPlayers];
        this.boardsSet     = new boolean[numPlayers];
        for (int i = 0; i < numPlayers; i++) {
            boards[i] = new BingoBoard(size);
        }
    }

    /** 모든 플레이어가 보드를 설정했는지 */
    public boolean allBoardsSet() {
        for (boolean b : boardsSet) if (!b) return false;
        return true;
    }

    /**
     * 플레이어 보드 주제 설정
     */
    public void setPlayerBoard(int playerIndex, String[][] topics) {
        boards[playerIndex].setTopics(topics);
        boardsSet[playerIndex] = true;
    }

    /**
     * 주제 호출 처리
     * - 중복 호출 불가
     * - 모든 보드에서 대소문자 무관 매칭 마킹
     */
    public boolean callTopic(int playerIndex, String topic) {
        String t = topic.trim();
        if (t.isEmpty()) return false;
        // 이미 호출된 주제면 거부 (대소문자 무관)
        boolean dup = calledTopics.stream().anyMatch(ct -> ct.equalsIgnoreCase(t));
        if (dup) return false;

        calledTopics.add(t);
        for (BingoBoard board : boards) board.markTopic(t);

        // 호출한 플레이어 먼저 승리 체크, 그 다음 나머지
        if (boards[playerIndex].getBingoCount() >= winBingoCount) {
            winner = playerIndex;
        } else {
            for (int i = 0; i < numPlayers; i++) {
                if (i != playerIndex && boards[i].getBingoCount() >= winBingoCount) {
                    winner = i; break;
                }
            }
        }
        if (winner < 0) currentTurn = (currentTurn + 1) % numPlayers;
        return true;
    }
}
