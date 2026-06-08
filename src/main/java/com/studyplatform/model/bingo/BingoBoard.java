package com.studyplatform.model.bingo;

import lombok.Data;

/**
 * 빙고 보드 (size × size) — 텍스트 주제 기반
 *
 * 플레이어가 각 셀에 직접 주제(텍스트)를 입력합니다.
 * 호출된 주제와 대소문자 무관 일치 시 해당 셀이 마킹됩니다.
 */
@Data
public class BingoBoard {

    private int size;

    /** 각 셀의 주제 텍스트 [행][열] — SETUP 단계에서 플레이어가 입력 */
    private String[][] topics;

    /** 마킹 여부 [행][열] */
    private boolean[][] marked;

    /** 현재 완성된 빙고 줄 수 */
    private int bingoCount = 0;

    /** 이 보드의 주제 설정 완료 여부 */
    private boolean boardSet = false;

    public BingoBoard(int size) {
        this.size = size;
        this.topics = new String[size][size];
        this.marked  = new boolean[size][size];
        // 빈 문자열로 초기화
        for (int r = 0; r < size; r++)
            for (int c = 0; c < size; c++)
                topics[r][c] = "";
    }

    /**
     * 플레이어가 설정한 주제 배열로 보드를 초기화합니다.
     */
    public void setTopics(String[][] topics) {
        this.topics   = topics;
        this.boardSet = true;
    }

    /**
     * 호출된 주제와 일치하는 셀을 마킹합니다. (대소문자 무관)
     * @return 일치하는 셀이 있으면 true
     */
    public boolean markTopic(String topic) {
        boolean found = false;
        for (int r = 0; r < size; r++) {
            for (int c = 0; c < size; c++) {
                if (topics[r][c] != null && topics[r][c].trim().equalsIgnoreCase(topic.trim())) {
                    marked[r][c] = true;
                    found = true;
                }
            }
        }
        if (found) updateBingoCount();
        return found;
    }

    /** 가로·세로·대각선 빙고 줄 수 계산 */
    private void updateBingoCount() {
        int count = 0;

        for (int r = 0; r < size; r++) {          // 가로
            boolean ok = true;
            for (int c = 0; c < size; c++) if (!marked[r][c]) { ok = false; break; }
            if (ok) count++;
        }
        for (int c = 0; c < size; c++) {          // 세로
            boolean ok = true;
            for (int r = 0; r < size; r++) if (!marked[r][c]) { ok = false; break; }
            if (ok) count++;
        }
        boolean d1 = true, d2 = true;             // 대각선
        for (int i = 0; i < size; i++) {
            if (!marked[i][i])         d1 = false;
            if (!marked[i][size-1-i])  d2 = false;
        }
        if (d1) count++;
        if (d2) count++;

        this.bingoCount = count;
    }
}
