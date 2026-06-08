package com.studyplatform.dto.response;

import com.studyplatform.model.StudyStatus;
import com.studyplatform.model.StudyType;
import lombok.Builder;
import lombok.Data;

/**
 * 게임 상태 응답 DTO — WebSocket STOMP 브로드캐스트 전용
 *
 * 서버가 게임 상태가 변경될 때마다 이 객체를 JSON으로 변환해
 * /topic/study/{roomId} 채널을 구독한 모든 클라이언트에게 전송합니다.
 *
 * ─── @Builder 패턴 ─────────────────────────────────────────────────────────
 * 필드가 많은 객체를 생성할 때 new StudyStateResponse(a,b,c,d,...) 처럼 쓰면
 * 어떤 인자가 뭔지 알기 어렵습니다.
 * @Builder를 쓰면 아래처럼 명시적으로 생성 가능:
 *
 *   StudyStateResponse.builder()
 *       .roomId("abc123")
 *       .status(StudyStatus.PLAYING)
 *       .winner(-1)
 *       .build();
 *
 * ─── gameData 필드 ─────────────────────────────────────────────────────────
 * 게임 타입에 따라 내용이 달라집니다:
 *   BASEBALL → Map<String, Object> { digits, currentTurn, guessHistories, secretSet, ... }
 *   BINGO    → Map<String, Object> { size, boards, calledTopics, bingoCounts, ... }
 * Object 타입으로 선언해 두 가지 구조를 모두 담을 수 있게 했습니다.
 * Jackson은 실제 런타임 타입을 보고 JSON으로 직렬화합니다.
 */
@Data
@Builder
public class StudyStateResponse {

    /** 방 고유 ID */
    private String roomId;

    /** 게임 종류 (BASEBALL / BINGO) */
    private StudyType studyType;

    /** 현재 방/게임 상태 (WAITING / SETUP / PLAYING / FINISHED) */
    private StudyStatus status;

    /**
     * 현재 상황을 설명하는 사람이 읽기 쉬운 메시지
     * 예) "Alice의 턴입니다.", "ERROR: 이미 사용된 주제입니다."
     * 클라이언트는 "ERROR:"로 시작하면 에러로 처리합니다.
     */
    private String message;

    /**
     * 현재 턴인 플레이어의 인덱스 (0부터 시작)
     * 에러 응답이거나 WAITING 상태면 -1
     */
    private int currentTurn;

    /**
     * 승자 플레이어의 인덱스
     * 게임 진행 중이거나 아직 결정되지 않은 경우 -1
     */
    private int winner;

    /**
     * 게임별 세부 데이터
     * 야구: 추측 기록, 비밀숫자 설정 현황 등
     * 빙고: 보드 상태, 호출된 주제 목록 등
     */
    private Object gameData;

    /**
     * 방에 입장한 플레이어 닉네임 배열
     * playerNames[0] = 방장, playerNames[1] = 두 번째 플레이어, ...
     * 클라이언트는 이 배열의 인덱스로 currentTurn, winner를 해석합니다.
     */
    private String[] playerNames;
}
