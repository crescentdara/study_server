package com.studyplatform.dto.request;

import lombok.Data;

/**
 * 게임 액션 / 채팅 / 방 나가기 요청 DTO
 *
 * moveType 목록:
 *   START_GAME  - 방장: 게임 시작
 *   RESTART     - 방장: 재시작
 *   LEAVE       - 방 나가기 (방장이면 방 삭제, 아니면 인원 감소)
 *   SET_SECRET  - 야구: 비밀 숫자 설정
 *   GUESS       - 야구: 추측
 *   SET_BOARD   - 빙고: 보드 주제 설정 (payload = List<List<String>>)
 *   CALL_TOPIC  - 빙고: 주제 호출
 *   CHAT        - 채팅 메시지 전송
 */
@Data
public class StudyMoveRequest {
    private String moveType;
    private String data;       // 단순 문자열 데이터
    private String sessionId;
    private Object payload;    // SET_BOARD 시 2D 배열 (Jackson → List<List<String>>)
    /** 채팅 전송 시 발신자의 이모지 (채팅 외 요청에서는 null) */
    private String emoji;
    /** 로비 채팅 전송 시 발신자의 닉네임 */
    private String nickname;
    private String type;
    private String imageUrl;
    private String fileName;
    private Long fileSize;
}
