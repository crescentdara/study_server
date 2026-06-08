package com.studyplatform.dto.request;

import lombok.Data;

/**
 * 게임 액션 / 채팅 요청 DTO
 *
 * moveType 종류:
 *   SET_SECRET  - 숫자야구 비밀 숫자 설정  (data = "123")
 *   GUESS       - 숫자야구 추측            (data = "456")
 *   SET_BOARD   - 빙고 보드 주제 설정      (payload = String[][])
 *   CALL_TOPIC  - 빙고 주제 호출           (data = "주제명")
 *   CHAT        - 채팅 메시지              (data = "메시지 내용")
 */
@Data
public class StudyMoveRequest {
    private String moveType;
    private String data;       // 단순 문자열 데이터
    private String sessionId;
    /**
     * 복잡한 데이터를 위한 추가 필드
     * SET_BOARD 시 String[][] 형태의 2D 배열이 Jackson에 의해
     * List<List<String>> 으로 역직렬화됩니다.
     */
    private Object payload;
}
