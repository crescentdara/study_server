package com.studyplatform.model.oldmaid;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 도둑잡기(Old Maid) 게임 모델
 *
 * ─── 게임 단계 ────────────────────────────────────────────────────────────────
 * 1. 배분(dealing=true)
 *    - 덱(53장)이 가운데 놓인 상태
 *    - 플레이어들이 차례로 덱에서 1장씩 직접 뽑음
 *    - 덱이 비면 dealing=false, 플레이 단계로 전환
 *
 * 2. 플레이(dealing=false)
 *    - 자기 패에서 쌍(같은 숫자)을 직접 골라 버림 → discardPair()
 *    - 내 턴에 다음 플레이어 패에서 1장 뽑음 → drawCard()
 *    - 패가 빈 플레이어는 safe (더 이상 참여 안 함)
 *    - 마지막까지 조커를 쥔 1명이 패자
 *
 * ─── 카드 표현 ────────────────────────────────────────────────────────────────
 * int[2] = [rank, suit]
 *   rank : 1~13 (A~K), 0 = 조커
 *   suit : 0=♠ 1=♥ 2=♦ 3=♣  -1 = 조커
 */
public class OldMaidGame {

    private final int numPlayers;

    /**
     * 중앙 덱 — 배분 단계에서 플레이어들이 여기서 1장씩 뽑습니다.
     * dealing=false가 되면 빈 리스트 유지 (게임 중 사용 안 함).
     */
    private final List<int[]> deck;

    /** 각 플레이어의 손패 */
    private final List<List<int[]>> hands;

    /** true: 배분 단계 / false: 플레이 단계 */
    private boolean dealing;

    /** 카드를 모두 버린 플레이어 (safe=true → 게임에서 제외됨) */
    private final boolean[] safe;

    /** 현재 턴 플레이어 인덱스 */
    private int currentTurn;

    /** 패자 인덱스 (-1: 미결정) */
    private int loser;

    /**
     * 마지막으로 셔플한 플레이어 (-1: 없음)
     * 프론트에서 이 값이 바뀌면 해당 플레이어 행에 애니메이션을 적용합니다.
     */
    private int lastShuffle = -1;

    // ──────────────────────────────────────────────────────────────
    // 생성자
    // ──────────────────────────────────────────────────────────────
    public OldMaidGame(int numPlayers) {
        this.numPlayers = numPlayers;
        this.loser      = -1;
        this.safe       = new boolean[numPlayers];
        this.dealing    = true;
        this.deck       = new ArrayList<>();
        this.hands      = new ArrayList<>();

        for (int i = 0; i < numPlayers; i++) hands.add(new ArrayList<>());

        // 덱 생성: 52장 + 조커 1장 = 53장
        for (int rank = 1; rank <= 13; rank++)
            for (int suit = 0; suit < 4; suit++)
                deck.add(new int[]{rank, suit});
        deck.add(new int[]{0, -1}); // 조커

        Collections.shuffle(deck);
        this.currentTurn = 0;
    }

    // ──────────────────────────────────────────────────────────────
    // 배분 단계
    // ──────────────────────────────────────────────────────────────

    /**
     * 덱에서 카드 1장을 자신의 패로 가져온다 (배분 단계 전용).
     *
     * 턴이 맞는 플레이어만 호출 가능하며, 한 번 호출할 때 1장씩 뽑습니다.
     * 덱이 비면 dealing=false로 전환됩니다.
     *
     * @param playerIndex 뽑는 플레이어
     * @return null: 성공 / 에러 메시지: 실패
     */
    public String dealCard(int playerIndex) {
        if (!dealing)              return "Dealing phase is already over.";
        if (playerIndex != currentTurn) return "Not your turn.";
        if (deck.isEmpty())        return "No cards left in deck.";

        // 덱 맨 뒤에서 1장 가져오기
        int[] card = deck.remove(deck.size() - 1);
        hands.get(playerIndex).add(card);

        // 라운드 로빈으로 다음 플레이어에게 턴 이동
        currentTurn = (currentTurn + 1) % numPlayers;

        // 덱이 비었으면 플레이 단계로 전환
        if (deck.isEmpty()) {
            dealing = false;
            // currentTurn은 배분이 끝난 시점의 값 그대로 유지
        }

        return null;
    }

    // ──────────────────────────────────────────────────────────────
    // 플레이 단계 — 쌍 버리기
    // ──────────────────────────────────────────────────────────────

    /**
     * 손패에서 카드 2장을 골라 직접 버린다.
     *
     * - 같은 rank(숫자)의 카드 두 장이어야 함 (조커는 쌍 없음)
     * - 자기 패에서만 가능, 턴 제한 없음
     *
     * @param playerIndex 버리는 플레이어
     * @param idx1        첫 번째 카드 인덱스
     * @param idx2        두 번째 카드 인덱스
     * @return null: 성공 / 에러 메시지: 실패
     */
    public String discardPair(int playerIndex, int idx1, int idx2) {
        if (dealing)                    return "Still in dealing phase.";
        if (loser != -1)                return "Game already finished.";
        if (playerIndex != currentTurn) return "Not your turn.";
        if (safe[playerIndex])          return "You have no cards.";

        List<int[]> hand = hands.get(playerIndex);
        if (idx1 < 0 || idx1 >= hand.size() ||
            idx2 < 0 || idx2 >= hand.size())  return "Invalid card index.";
        if (idx1 == idx2)                      return "Select two different cards.";

        int[] c1 = hand.get(idx1), c2 = hand.get(idx2);
        if (c1[0] == 0 || c1[0] != c2[0])
            return "Not a pair. Cards must have the same number.";

        // 인덱스 큰 것 먼저 제거 (제거 후 인덱스 밀림 방지)
        int hi = Math.max(idx1, idx2), lo = Math.min(idx1, idx2);
        hand.remove(hi);
        hand.remove(lo);

        if (hand.isEmpty()) safe[playerIndex] = true;
        lastShuffle = -1;
        checkLoser();
        return null;
    }

    // ──────────────────────────────────────────────────────────────
    // 플레이 단계 — 카드 뽑기
    // ──────────────────────────────────────────────────────────────

    /**
     * 현재 턴 플레이어가 다음 active 플레이어의 패에서 카드 1장을 뽑는다.
     *
     * @param playerIndex 뽑는 플레이어 (currentTurn과 일치해야 함)
     * @param cardIdx     다음 플레이어 손패에서 뽑을 위치
     * @return null: 성공 / 에러 메시지: 실패
     */
    public String drawCard(int playerIndex, int cardIdx) {
        if (dealing)               return "Still in dealing phase.";
        if (loser != -1)           return "Game already finished.";
        if (playerIndex != currentTurn) return "Not your turn.";
        if (safe[playerIndex])     return "You are already safe.";

        int from = nextActive(currentTurn);
        List<int[]> fromHand = hands.get(from);

        if (cardIdx < 0 || cardIdx >= fromHand.size()) return "Invalid card index.";

        lastShuffle = -1;
        int[] card = fromHand.remove(cardIdx);
        hands.get(currentTurn).add(card);

        if (fromHand.isEmpty()) safe[from] = true;

        checkLoser();
        // 턴 이동은 endTurn()에서만 처리 — 카드 뽑은 후 바로 넘어가지 않음
        return null;
    }

    // ──────────────────────────────────────────────────────────────
    // 턴 종료
    // ──────────────────────────────────────────────────────────────

    /**
     * 현재 플레이어가 자신의 턴을 종료하고 다음 active 플레이어에게 턴을 넘긴다.
     */
    public String endTurn(int playerIndex) {
        if (dealing)                    return "Still in dealing phase.";
        if (loser != -1)                return "Game already finished.";
        if (playerIndex != currentTurn) return "Not your turn.";
        currentTurn = nextActive(currentTurn);
        return null;
    }

    // ──────────────────────────────────────────────────────────────
    // 셔플 (공개 액션 — 플레이 단계만)
    // ──────────────────────────────────────────────────────────────

    /**
     * 자신의 손패 순서를 무작위로 섞는다.
     * 상대방이 조커 위치를 기억했을 때 대응하는 심리전 도구입니다.
     * 모든 플레이어에게 셔플 애니메이션이 브로드캐스트됩니다.
     */
    public String shuffleHand(int playerIndex) {
        if (dealing)              return "Still in dealing phase.";
        if (loser != -1)          return "Game already finished.";
        if (safe[playerIndex])    return "You have no cards to shuffle.";

        Collections.shuffle(hands.get(playerIndex));
        lastShuffle = playerIndex;
        return null;
    }

    // ──────────────────────────────────────────────────────────────
    // 내부 헬퍼
    // ──────────────────────────────────────────────────────────────

    private void checkLoser() {
        int activeCount = 0, lastActive = -1;
        for (int i = 0; i < numPlayers; i++)
            if (!safe[i]) { activeCount++; lastActive = i; }
        if (activeCount <= 1) loser = lastActive;
    }

    private int nextActive(int startIdx) {
        int next = (startIdx + 1) % numPlayers;
        while (safe[next]) next = (next + 1) % numPlayers;
        return next;
    }

    // ──────────────────────────────────────────────────────────────
    // Getter
    // ──────────────────────────────────────────────────────────────
    public int              getNumPlayers()          { return numPlayers; }
    public List<int[]>      getDeck()                { return deck; }
    public List<List<int[]>> getHands()              { return hands; }
    public boolean          isDealing()              { return dealing; }
    public boolean[]        getSafe()                { return safe; }
    public int              getCurrentTurn()         { return currentTurn; }
    public int              getLoser()               { return loser; }
    public int              getLastShuffle()         { return lastShuffle; }
    public int              getDeckSize()            { return deck.size(); }

    public int[] getHandSizes() {
        int[] s = new int[numPlayers];
        for (int i = 0; i < numPlayers; i++) s[i] = hands.get(i).size();
        return s;
    }

    public int getNextActivePlayer() {
        if (loser != -1 || dealing) return -1;
        return nextActive(currentTurn);
    }
}
