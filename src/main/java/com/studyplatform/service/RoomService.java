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
 * ─── @Service ─────────────────────────────────────────────────────────────────
 * Spring이 이 클래스를 싱글톤 빈(Bean)으로 자동 등록합니다.
 * 싱글톤: 앱 전체에 인스턴스가 단 하나 — 모든 요청이 같은 객체를 공유합니다.
 * 덕분에 rooms 맵이 서버 실행 내내 유지되고, 모든 사용자의 요청이 같은 데이터를 봅니다.
 *
 * ─── 데이터 저장소 ─────────────────────────────────────────────────────────────
 * DB 없이 메모리(Map)에 방 데이터를 저장합니다.
 * 장점: 구현이 단순, 속도 빠름
 * 단점: 서버 재시작 시 모든 방 데이터 사라짐
 *
 * ─── ConcurrentHashMap vs HashMap ─────────────────────────────────────────────
 * WebSocket은 여러 클라이언트가 동시에(멀티스레드로) 요청을 보냅니다.
 * 일반 HashMap은 동시 접근 시 데이터 손상(Race Condition)이 발생할 수 있습니다.
 * ConcurrentHashMap은 내부적으로 락(Lock)을 사용해 스레드 안전성을 보장합니다.
 */
@Service
public class RoomService {

    // 방 ID(String) → 방 객체(Room) 매핑
    // key: roomId (8자리 고유 문자열), value: Room 객체
    private final Map<String, Room> rooms = new ConcurrentHashMap<>();

    /**
     * 방 생성
     *
     * 방장(host)을 playerIndex=0으로 등록하고, 게임 데이터는 아직 초기화하지 않습니다.
     * 게임 데이터는 모든 인원이 모였을 때(joinRoom) 초기화합니다.
     *
     * @param roomName   방 이름
     * @param studyType  게임 종류 (BASEBALL / BINGO)
     * @param nickname   방장 닉네임
     * @param sessionId  방장의 클라이언트 세션 ID
     * @param maxPlayers 최대 입장 인원 (2~6, 범위 초과 시 자동 보정)
     * @param digits     숫자야구 자릿수 (3·4·5)
     * @param boardSize  빙고 보드 크기 (3·4·5)
     */
    public Room createRoom(String roomName, StudyType studyType,
                           String nickname, String sessionId,
                           int maxPlayers, int digits, int boardSize) {
        Room room = new Room(roomName, studyType);

        // Math.max/min으로 2~6 범위를 벗어나는 입력을 보정
        room.setMaxPlayers(Math.max(2, Math.min(6, maxPlayers)));
        room.setDigits(digits);
        room.setBoardSize(boardSize);

        // 방장을 0번째 플레이어로 추가
        Player host = new Player(sessionId, nickname, 0);
        room.getPlayers().add(host);

        // 메모리 맵에 저장 (roomId를 키로 사용)
        rooms.put(room.getRoomId(), room);
        return room;
    }

    /**
     * 방 입장 (두 번째 이후 플레이어)
     *
     * 입장 후 방이 꽉 찼다면 게임 데이터를 초기화하고
     * 상태를 SETUP(준비 단계)으로 전환합니다.
     *
     * @param roomId    입장할 방의 ID
     * @param nickname  입장하는 플레이어 닉네임
     * @param sessionId 입장하는 플레이어의 세션 ID
     * @return 업데이트된 방 객체
     * @throws RuntimeException 방 없음 / 만석 / 이미 시작된 경우
     */
    public Room joinRoom(String roomId, String nickname, String sessionId) {
        Room room = rooms.get(roomId);

        // 방 존재 여부, 만석 여부, 이미 시작 여부 검사
        if (room == null)  throw new RuntimeException("Room not found.");
        if (room.isFull()) throw new RuntimeException("Room is full.");
        if (room.getStatus() != StudyStatus.WAITING) throw new RuntimeException("Game already started.");

        // 현재 인원 수 = 다음 플레이어의 인덱스
        // 예) 이미 2명이면 nextIndex=2 → playerIndex=2
        int nextIndex = room.getPlayers().size();
        room.getPlayers().add(new Player(sessionId, nickname, nextIndex));

        // 최대 인원이 됐을 때 게임 초기화
        if (room.isFull()) {
            initGameData(room);  // 게임 타입에 맞는 객체 생성
            // 야구·빙고 모두 SETUP 단계:
            //   야구 → 각자 비밀 숫자 설정
            //   빙고 → 각자 보드 주제 입력
            room.setStatus(StudyStatus.SETUP);
        }
        return room;
    }

    /**
     * 게임 타입에 맞는 게임 데이터 객체를 생성해 방에 저장
     *
     * switch 표현식(Java 14+): case 마다 return/break 없이 화살표(->)로 간결하게 표현
     *
     * @param room 게임 데이터를 초기화할 방
     */
    private void initGameData(Room room) {
        int n = room.getPlayers().size(); // 실제 입장한 인원 수
        switch (room.getStudyType()) {
            // 숫자야구: 자릿수(digits)와 인원 수(n)로 초기화
            case BASEBALL -> room.setGameData(new BaseballGame(room.getDigits(), n));
            // 빙고: 보드 크기(boardSize)와 인원 수(n)로 초기화
            case BINGO    -> room.setGameData(new BingoGame(room.getBoardSize(), n));
        }
    }

    /** 방 ID로 단일 방 조회 (없으면 null 반환) */
    public Room getRoom(String roomId)    { return rooms.get(roomId); }

    /**
     * WAITING 상태인 방 목록 반환 (로비에서 입장 가능한 방들)
     *
     * Stream API:
     *   .values()  → Map의 모든 값(Room 객체들)을 스트림으로
     *   .filter()  → 조건에 맞는 요소만 통과
     *   .toList()  → 스트림을 List로 변환 (Java 16+)
     */
    public List<Room> getWaitingRooms()  {
        return rooms.values().stream()
                .filter(r -> r.getStatus() == StudyStatus.WAITING)
                .toList();
    }

    /** 모든 방 목록 반환 */
    public List<Room> getAllRooms()      { return new ArrayList<>(rooms.values()); }

    /** 방 삭제 (게임 종료 후 정리 등에 사용) */
    public void removeRoom(String roomId){ rooms.remove(roomId); }
}
