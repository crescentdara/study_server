package com.studyplatform.service;

import com.studyplatform.model.*;
import com.studyplatform.model.baseball.BaseballGame;
import com.studyplatform.model.bingo.BingoGame;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 방(Room) 관리 서비스
 */
@Service
public class RoomService {

    private final Map<String, Room> rooms = new ConcurrentHashMap<>();

    /** 방 생성: 방장(playerIndex=0) 등록 */
    public Room createRoom(String roomName, StudyType studyType,
                           String nickname, String sessionId,
                           int maxPlayers, int digits, int boardSize) {
        Room room = new Room(roomName, studyType);
        room.setMaxPlayers(Math.max(2, Math.min(6, maxPlayers)));
        room.setDigits(digits);
        room.setBoardSize(boardSize);
        room.getPlayers().add(new Player(sessionId, nickname, 0));
        rooms.put(room.getRoomId(), room);
        return room;
    }

    /**
     * 방 입장
     *
     * 중복 입장 방지:
     *   같은 sessionId가 이미 방에 있으면 추가하지 않고 그대로 반환합니다.
     *   (새로고침 후 재접속, 뒤로가기 후 재입장 등의 상황 처리)
     */
    public Room joinRoom(String roomId, String nickname, String sessionId) {
        Room room = rooms.get(roomId);
        if (room == null)  throw new RuntimeException("Room not found.");
        if (room.getStatus() != StudyStatus.WAITING) throw new RuntimeException("Game already started.");

        // 같은 세션이 이미 방에 있으면 중복 추가 방지
        boolean alreadyIn = room.getPlayers().stream()
                .anyMatch(p -> p.getSessionId().equals(sessionId));
        if (alreadyIn) return room;

        if (room.isFull()) throw new RuntimeException("Room is full.");

        int nextIndex = room.getPlayers().size();
        room.getPlayers().add(new Player(sessionId, nickname, nextIndex));
        return room;
    }

    /**
     * 플레이어 퇴장 처리
     *
     * - sessionId로 플레이어를 찾아 목록에서 제거합니다.
     * - 나머지 플레이어의 playerIndex를 0부터 재정렬합니다.
     *   (방장 자리가 비더라도 다음 플레이어가 0번 인덱스를 가짐)
     * - 인원이 0명이 되면 방을 삭제합니다.
     * - 게임 진행 중 인원이 1명 이하가 되면 FINISHED로 전환합니다.
     *
     * @param roomId    퇴장할 방의 ID
     * @param sessionId 퇴장하는 플레이어의 세션 ID
     * @return 처리 후 방 상태 (방이 삭제됐으면 null)
     */
    public Room leaveRoom(String roomId, String sessionId) {
        Room room = rooms.get(roomId);
        if (room == null) return null;

        // 해당 세션의 플레이어 제거
        room.getPlayers().removeIf(p -> p.getSessionId().equals(sessionId));

        // 인원이 0명이면 방 삭제
        if (room.getPlayers().isEmpty()) {
            rooms.remove(roomId);
            return null;
        }

        // playerIndex 재정렬 (0, 1, 2, ... 순서로 재부여)
        // 방장이 나가도 다음 플레이어가 index=0이 되므로 현재 방장이 바뀝니다.
        for (int i = 0; i < room.getPlayers().size(); i++) {
            room.getPlayers().get(i).setPlayerIndex(i);
        }

        // 게임 중에 인원이 부족해지면 종료 처리
        if (room.getPlayers().size() < 2
                && room.getStatus() != StudyStatus.WAITING
                && room.getStatus() != StudyStatus.FINISHED) {
            room.setStatus(StudyStatus.FINISHED);
        }

        return room;
    }

    /** 게임 시작 (방장 전용, WAITING 상태에서만 가능) */
    public void startGame(Room room) {
        if (room.getPlayers().size() < 2)
            throw new RuntimeException("Need at least 2 players to start.");
        if (room.getStatus() != StudyStatus.WAITING)
            throw new RuntimeException("Game already started.");
        initGameData(room);
        room.setStatus(StudyStatus.SETUP);
    }

    /** 재시작 (방장 전용, FINISHED 상태에서만 가능) */
    public void restartGame(Room room) {
        if (room.getStatus() != StudyStatus.FINISHED)
            throw new RuntimeException("Game is not finished yet.");
        initGameData(room);
        room.setStatus(StudyStatus.SETUP);
    }

    private void initGameData(Room room) {
        int n = room.getPlayers().size();
        switch (room.getStudyType()) {
            case BASEBALL -> room.setGameData(new BaseballGame(room.getDigits(), n));
            case BINGO    -> room.setGameData(new BingoGame(room.getBoardSize(), n));
        }
    }

    public Room getRoom(String roomId)    { return rooms.get(roomId); }
    public List<Room> getWaitingRooms()   { return rooms.values().stream().filter(r -> r.getStatus() == StudyStatus.WAITING).toList(); }
    public List<Room> getAllRooms()       { return new ArrayList<>(rooms.values()); }
    public void removeRoom(String roomId) { rooms.remove(roomId); }
}
