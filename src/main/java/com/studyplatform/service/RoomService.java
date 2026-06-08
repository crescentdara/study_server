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
 *
 * ─── 게임 시작 방식 ────────────────────────────────────────────────────────
 * 이전: 최대 인원이 채워지면 자동으로 게임 시작
 * 현재: 방장(playerIndex=0)이 Start 버튼을 눌러야 시작
 *       최대 인원이 안 차도 2명 이상이면 시작 가능
 *
 * ─── 방 생명주기 ─────────────────────────────────────────────────────────
 * createRoom → WAITING
 *   ↓ (플레이어 입장 반복)
 * joinRoom    → WAITING 유지 (자동시작 없음)
 *   ↓ (방장이 Start 버튼)
 * startGame   → SETUP (비밀숫자/보드 입력)
 *   ↓ (모두 준비 완료)
 *              → PLAYING
 *   ↓
 *              → FINISHED
 *   ↓ (방장이 Restart 버튼)
 * restartGame → SETUP (같은 인원, 같은 방으로 재시작)
 */
@Service
public class RoomService {

    private final Map<String, Room> rooms = new ConcurrentHashMap<>();

    /** 방 생성: 방장(playerIndex=0) 등록, 게임 데이터는 아직 초기화하지 않음 */
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
     * 자동 시작 없음 — 방장이 startGame()을 호출해야 시작됩니다.
     */
    public Room joinRoom(String roomId, String nickname, String sessionId) {
        Room room = rooms.get(roomId);
        if (room == null)  throw new RuntimeException("Room not found.");
        if (room.isFull()) throw new RuntimeException("Room is full.");
        if (room.getStatus() != StudyStatus.WAITING) throw new RuntimeException("Game already started.");

        int nextIndex = room.getPlayers().size();
        room.getPlayers().add(new Player(sessionId, nickname, nextIndex));
        // 상태는 WAITING 유지 — 방장이 직접 시작해야 함
        return room;
    }

    /**
     * 게임 시작 (방장 전용)
     *
     * 현재 입장한 인원 수(n)로 게임 데이터를 초기화합니다.
     * maxPlayers를 다 채우지 않아도 시작 가능합니다.
     *
     * @throws RuntimeException 2명 미만이거나 이미 시작된 경우
     */
    public void startGame(Room room) {
        if (room.getPlayers().size() < 2)
            throw new RuntimeException("Need at least 2 players to start.");
        if (room.getStatus() != StudyStatus.WAITING)
            throw new RuntimeException("Game already started.");

        initGameData(room);
        room.setStatus(StudyStatus.SETUP);
    }

    /**
     * 게임 재시작 (방장 전용, FINISHED 상태에서만 가능)
     *
     * 현재 방에 있는 플레이어들을 유지하고 게임 데이터만 초기화합니다.
     * 방을 나가거나 새로 만들 필요 없이 같은 방에서 다시 플레이 가능합니다.
     */
    public void restartGame(Room room) {
        if (room.getStatus() != StudyStatus.FINISHED)
            throw new RuntimeException("Game is not finished yet.");

        initGameData(room);         // 새 게임 데이터 생성
        room.setStatus(StudyStatus.SETUP); // FINISHED → SETUP 으로 리셋
    }

    /** 게임 타입에 맞는 게임 데이터 객체 생성 */
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
