package com.studyplatform.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 채팅 메시지 DTO
 * /topic/chat/{roomId} 로 브로드캐스트됩니다.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessage {
    /** 보낸 사람 닉네임 */
    private String nickname;
    /** 채팅 내용 */
    private String text;
    /** 서버 수신 시각 (Unix timestamp ms) */
    private long timestamp;
    /**
     * 플레이어가 선택한 이모지
     * 클라이언트가 보낸 값을 그대로 포함해 브로드캐스트합니다.
     * 이렇게 하면 수신 측도 발신자의 이모지를 볼 수 있습니다.
     */
    private String emoji;
}
