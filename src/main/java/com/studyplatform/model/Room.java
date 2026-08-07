package com.studyplatform.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 게임 방 모델
 */
@Data
public class Room {
    private String roomId;
    private String roomName;
    private StudyType studyType;
    private StudyStatus status;
    private List<Player> players;
    private Object gameData;

    private int maxPlayers = 2;  // 최대 인원 (2~6명)
    private int digits = 3;      // 숫자야구 자릿수
    private int boardSize = 5;   // 빙고 보드 크기
    /**
     * 게임 모드. 지금은 테트리스만 쓴다 — "SURVIVAL"이면 혼자서 밀려 올라오는
     * 쓰레기 줄을 버티는 판이고, 비어 있으면 평소의 대전이다.
     */
    private String mode = "";

    public Room(String roomName, StudyType studyType) {
        this.roomId = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        this.roomName = roomName;
        this.studyType = studyType;
        this.status = StudyStatus.WAITING;
        this.players = new ArrayList<>();
    }

    /** 최대 인원이 찼는지 확인 */
    public boolean isFull() {
        return players.size() >= maxPlayers;
    }

    public Player getPlayerBySession(String sessionId) {
        return players.stream()
                .filter(p -> p.getSessionId().equals(sessionId))
                .findFirst().orElse(null);
    }
}
