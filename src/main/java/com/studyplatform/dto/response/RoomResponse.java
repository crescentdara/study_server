package com.studyplatform.dto.response;

import com.studyplatform.model.StudyStatus;
import com.studyplatform.model.StudyType;
import com.studyplatform.model.Room;
import lombok.Data;

import java.util.List;

/**
 * 방 정보 응답 DTO
 */
@Data
public class RoomResponse {
    private String roomId;
    private String roomName;
    private StudyType studyType;
    private StudyStatus status;
    private int playerCount;
    private int maxPlayers;
    private List<String> playerNames;
    private int digits;
    private int boardSize;

    public static RoomResponse from(Room room) {
        RoomResponse dto = new RoomResponse();
        dto.setRoomId(room.getRoomId());
        dto.setRoomName(room.getRoomName());
        dto.setStudyType(room.getStudyType());
        dto.setStatus(room.getStatus());
        dto.setPlayerCount(room.getPlayers().size());
        dto.setMaxPlayers(room.getStudyType() == StudyType.TETRIS || room.getStudyType() == StudyType.INCIDENT_AVOID || room.getStudyType() == StudyType.BREAKOUT ? 3 : room.getMaxPlayers());
        dto.setPlayerNames(room.getPlayers().stream().map(p -> p.getNickname()).toList());
        dto.setDigits(room.getDigits());
        dto.setBoardSize(room.getBoardSize());
        return dto;
    }
}
