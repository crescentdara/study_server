package com.studyplatform.dto.request;

import lombok.Data;

/**
 * 방 입장 요청 DTO
 *
 * 요청 예시 (HTTP POST /api/rooms/{roomId}/join):
 * {
 *   "nickname": "이순신",
 *   "sessionId": "xyz789..."
 * }
 */
@Data
public class JoinRoomRequest {
    private String nickname;   // 입장할 플레이어 닉네임
    private String sessionId;  // 클라이언트 고유 ID
}
