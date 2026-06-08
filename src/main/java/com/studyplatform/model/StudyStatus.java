package com.studyplatform.model;

/**
 * 방/게임의 현재 상태를 나타내는 열거형
 *
 * 상태 전환 흐름:
 *   WAITING → SETUP(숫자야구만) → PLAYING → FINISHED
 *   WAITING → PLAYING(빙고는 바로 시작) → FINISHED
 */
public enum StudyStatus {
    WAITING,   // 대기 중: 방이 생성된 후 상대방을 기다리는 상태
    SETUP,     // 준비 중: 숫자야구에서 비밀 숫자를 설정하는 단계
    PLAYING,   // 게임 진행 중
    FINISHED   // 게임 종료 (승자 결정)
}
