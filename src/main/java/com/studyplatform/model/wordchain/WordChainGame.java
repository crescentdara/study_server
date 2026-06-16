package com.studyplatform.model.wordchain;

import lombok.Data;

import java.util.*;

@Data
public class WordChainGame {

    private String lastWord = "";
    private final Set<String> usedWords = new HashSet<>();
    private final List<Boolean> eliminated;
    private final int timeLimit;
    private final int numPlayers;
    private int currentTurn = 0;
    private int winner = -1;

    // 두음법칙 매핑
    private static final Map<String, String> DUEUM_MAP = new HashMap<>();
    static {
        String[][] pairs = {
            {"라","나"},{"락","낙"},{"란","난"},{"람","남"},{"랑","낭"},{"래","내"},{"량","양"},
            {"려","여"},{"력","역"},{"련","연"},{"렬","열"},{"렵","엽"},{"령","영"},{"례","예"},
            {"로","노"},{"록","녹"},{"론","논"},{"롱","농"},{"뢰","뇌"},{"료","요"},{"룡","용"},
            {"루","누"},{"류","유"},{"륙","육"},{"륜","윤"},{"률","율"},{"륭","융"},{"릉","능"},
            {"리","이"},{"린","인"},{"림","임"},{"립","입"},
            {"냐","야"},{"녀","여"},{"뇨","요"},{"뉴","유"},{"니","이"}
        };
        for (String[] p : pairs) DUEUM_MAP.put(p[0], p[1]);
    }

    public WordChainGame(int numPlayers, int timeLimit) {
        this.numPlayers = numPlayers;
        this.timeLimit  = timeLimit;
        this.eliminated = new ArrayList<>();
        for (int i = 0; i < numPlayers; i++) eliminated.add(false);
    }

    /** 단어의 첫 글자가 이전 단어의 마지막 글자(두음법칙 포함)와 일치하는지 확인 */
    public boolean isValidStart(String word) {
        if (lastWord.isEmpty()) return true;
        String lastSyl = String.valueOf(lastWord.charAt(lastWord.length() - 1));
        String firstSyl = String.valueOf(word.charAt(0));
        if (firstSyl.equals(lastSyl)) return true;
        // 두음법칙: lastSyl → mapped 값과 firstSyl 비교
        String mapped = DUEUM_MAP.get(lastSyl);
        if (mapped != null && mapped.equals(firstSyl)) return true;
        // 역방향: firstSyl → lastSyl 이 DUEUM_MAP에 있으면 허용
        String revMapped = DUEUM_MAP.get(firstSyl);
        return lastSyl.equals(revMapped);
    }

    /** 현재 턴의 다음 생존 플레이어 인덱스 */
    public int nextAliveIndex(int from) {
        int i = (from + 1) % numPlayers;
        int loops = 0;
        while (eliminated.get(i) && loops < numPlayers) {
            i = (i + 1) % numPlayers;
            loops++;
        }
        return i;
    }

    /** 생존 중인 플레이어 수 */
    public long aliveCount() {
        return eliminated.stream().filter(e -> !e).count();
    }
}
