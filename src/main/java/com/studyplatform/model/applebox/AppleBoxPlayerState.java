package com.studyplatform.model.applebox;

import lombok.Data;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 사과게임 플레이어별 상태
 *
 * cleared 는 이 플레이어가 없앤 칸의 인덱스(row * COLS + col) 집합이다.
 * 보드 자체는 모든 플레이어가 공유하므로, 누가 어디까지 지웠는지는 여기에만 남는다.
 */
@Data
public class AppleBoxPlayerState {
    private final Set<Integer> cleared = ConcurrentHashMap.newKeySet();
    private int score;
    /** 제한 시간을 다 쓰거나 전량 정리해서 더 진행하지 않는 상태 */
    private boolean finished;
    private long updatedAt = System.currentTimeMillis();
}
