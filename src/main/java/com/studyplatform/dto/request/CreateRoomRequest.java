package com.studyplatform.dto.request;

import com.studyplatform.model.StudyType;
import lombok.Data;

/**
 * 방 생성 요청 DTO
 */
@Data
public class CreateRoomRequest {
    private String roomName;
    private StudyType studyType;
    private String nickname;
    private String sessionId;
    private int maxPlayers = 2;   // 최대 인원 (2~6)
    private int digits = 3;       // 숫자야구 자릿수 (3·4·5)
    private int boardSize = 5;    // 빙고 보드 크기 (3·4·5)
}
