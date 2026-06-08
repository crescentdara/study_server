package com.studyplatform.model;

/**
 * 게임 종류를 나타내는 열거형(Enum)
 *
 * Enum은 미리 정해진 상수 목록을 타입 안전하게 표현할 때 사용합니다.
 * String으로 "BASEBALL", "BINGO"를 쓰면 오타가 날 수 있지만,
 * Enum은 컴파일 타임에 오류를 잡아줍니다.
 */
public enum StudyType {
    BASEBALL,  // 숫자야구
    BINGO      // 빙고
}
