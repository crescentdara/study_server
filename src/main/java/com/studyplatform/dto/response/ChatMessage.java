package com.studyplatform.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 채팅 메시지 DTO
 *
 * 채팅 채널 (/topic/chat/{roomId}) 로 브로드캐스트됩니다.
 * 게임 상태 채널과 분리해 클라이언트가 채팅과 게임 이벤트를 독립적으로 처리할 수 있습니다.
 *
 * ─── Lombok 어노테이션 ──────────────────────────────────────────────────────
 * @Data            : getter/setter/toString/equals/hashCode 자동 생성
 * @NoArgsConstructor : Jackson이 JSON → 객체 역직렬화 시 기본 생성자를 사용하므로 필요
 * @AllArgsConstructor: StudyController에서 new ChatMessage(nick, text, ts) 형태로 생성
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessage {

    /** 메시지를 보낸 플레이어의 닉네임 */
    private String nickname;

    /** 채팅 내용 (앞뒤 공백 제거된 상태) */
    private String text;

    /**
     * 서버가 메시지를 수신한 시각 (Unix timestamp, 밀리초 단위)
     * System.currentTimeMillis() 로 생성됩니다.
     * 클라이언트에서 new Date(timestamp).toLocaleTimeString() 으로 표시합니다.
     */
    private long timestamp;
}
