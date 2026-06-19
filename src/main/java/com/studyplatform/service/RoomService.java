package com.studyplatform.service;

import com.studyplatform.model.*;
import com.studyplatform.model.baseball.BaseballGame;
import com.studyplatform.model.bingo.BingoGame;
import com.studyplatform.model.breakout.BreakoutGame;
import com.studyplatform.model.catchmind.CatchMindGame;
import com.studyplatform.model.incident.IncidentAvoidGame;
import com.studyplatform.model.omok.OmokGame;
import com.studyplatform.model.oldmaid.OldMaidGame;
import com.studyplatform.model.tetris.TetrisGame;
import com.studyplatform.model.wordchain.WordChainGame;
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
        room.setMaxPlayers(studyType == StudyType.TETRIS || studyType == StudyType.INCIDENT_AVOID || studyType == StudyType.BREAKOUT ? 3 : studyType == StudyType.OMOK ? 2 : Math.max(2, Math.min(6, maxPlayers)));
        // TETRIS=1명, OMOK=2명 고정, OLDMAID=2~7명, 나머지=2~6명
        room.setMaxPlayers(studyType == StudyType.TETRIS || studyType == StudyType.INCIDENT_AVOID || studyType == StudyType.BREAKOUT ? 3
                : studyType == StudyType.OMOK    ? 2
                : studyType == StudyType.OLDMAID    ? Math.max(2, Math.min(7, maxPlayers))
                : studyType == StudyType.WORD_CHAIN ? Math.max(2, Math.min(6, maxPlayers))
                : Math.max(2, Math.min(6, maxPlayers)));
        room.setDigits(digits);
        room.setBoardSize(studyType == StudyType.OMOK ? 19 : studyType == StudyType.TETRIS || studyType == StudyType.INCIDENT_AVOID || studyType == StudyType.BREAKOUT ? 20 : boardSize);
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
        normalizeRoomConfig(room);
        if (room.isFull()) throw new RuntimeException("Room is full.");
        if (room.getStatus() != StudyStatus.WAITING) throw new RuntimeException("Game already started.");

        // 같은 세션이 이미 입장해 있으면 중복 추가 방지 (나갔다 들어오는 경우)
        if (room.getPlayerBySession(sessionId) != null)
            throw new RuntimeException("Already in this room.");

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
        if (room.getStudyType() != StudyType.TETRIS && room.getStudyType() != StudyType.INCIDENT_AVOID && room.getStudyType() != StudyType.BREAKOUT && room.getPlayers().size() < 2)
            throw new RuntimeException("Need at least 2 players to start.");
        if (room.getStatus() != StudyStatus.WAITING)
            throw new RuntimeException("Game already started.");

        initGameData(room);
        // OMOK·TETRIS·OLDMAID는 SETUP 없이 바로 PLAYING
        boolean directPlay = room.getStudyType() == StudyType.OMOK
                          || room.getStudyType() == StudyType.TETRIS
                          || room.getStudyType() == StudyType.INCIDENT_AVOID
                          || room.getStudyType() == StudyType.BREAKOUT
                          || room.getStudyType() == StudyType.CATCHMIND
                          || room.getStudyType() == StudyType.OLDMAID
                          || room.getStudyType() == StudyType.WORD_CHAIN;
        room.setStatus(directPlay ? StudyStatus.PLAYING : StudyStatus.SETUP);
    }

    /**
     * 게임 재시작 (방장 전용, FINISHED 상태에서만 가능)
     *
     * 현재 방에 있는 플레이어들을 유지하고 게임 데이터만 초기화합니다.
     * 방을 나가거나 새로 만들 필요 없이 같은 방에서 다시 플레이 가능합니다.
     */
    public void restartGame(Room room) {
        if (room.getStatus() != StudyStatus.FINISHED && room.getStudyType() != StudyType.TETRIS)
            throw new RuntimeException("Game is not finished yet.");
        if (room.getStatus() == StudyStatus.WAITING)
            throw new RuntimeException("Game has not started yet.");

        initGameData(room);         // 새 게임 데이터 생성
        boolean directPlay = room.getStudyType() == StudyType.OMOK
                          || room.getStudyType() == StudyType.TETRIS
                          || room.getStudyType() == StudyType.INCIDENT_AVOID
                          || room.getStudyType() == StudyType.BREAKOUT
                          || room.getStudyType() == StudyType.CATCHMIND
                          || room.getStudyType() == StudyType.OLDMAID
                          || room.getStudyType() == StudyType.WORD_CHAIN;
        room.setStatus(directPlay ? StudyStatus.PLAYING : StudyStatus.SETUP);
    }

    /** 게임 타입에 맞는 게임 데이터 객체 생성 */
    private void initGameData(Room room) {
        int n = room.getPlayers().size();
        switch (room.getStudyType()) {
            case BASEBALL -> room.setGameData(new BaseballGame(room.getDigits(), n));
            case BINGO    -> room.setGameData(new BingoGame(room.getBoardSize(), n));
            case OMOK     -> room.setGameData(new OmokGame(room.getBoardSize(), n));
            case TETRIS   -> room.setGameData(new TetrisGame(n));
            case OLDMAID  -> room.setGameData(new OldMaidGame(n));
            case INCIDENT_AVOID -> room.setGameData(new IncidentAvoidGame(n));
            case BREAKOUT -> room.setGameData(new BreakoutGame(n));
            case CATCHMIND   -> room.setGameData(new CatchMindGame(n));
            case WORD_CHAIN  -> room.setGameData(new WordChainGame(n, room.getDigits() > 0 ? room.getDigits() : 7));
        }
    }

    private void normalizeRoomConfig(Room room) {
        if (room.getStudyType() == StudyType.TETRIS || room.getStudyType() == StudyType.INCIDENT_AVOID || room.getStudyType() == StudyType.BREAKOUT) {
            room.setMaxPlayers(3);
            room.setBoardSize(20);
        } else if (room.getStudyType() == StudyType.OMOK) {
            room.setMaxPlayers(2);
            room.setBoardSize(19);
        }
    }

    /**
     * 플레이어 퇴장 처리
     *
     * ─── 케이스별 동작 ────────────────────────────────────────────────────────
     * 1. 방장(playerIndex=0)이 나가는 경우
     *    → 방을 삭제하고 null 반환 (Controller가 ROOM_CLOSED 브로드캐스트)
     *
     * 2. 일반 플레이어가 나가는 경우
     *    → 플레이어 목록에서 제거 후 playerIndex 재정렬
     *    → 방 객체 반환 (Controller가 업데이트된 인원 수 브로드캐스트)
     *
     * 3. 방에 없는 sessionId인 경우 (이미 나감, 중복 요청)
     *    → 현재 방 상태 그대로 반환 (무시)
     *
     * ─── playerIndex 재정렬 이유 ─────────────────────────────────────────────
     * 예) [A(0), B(1), C(2)] 에서 B가 나가면 → [A(0), C(1)]
     * C의 인덱스를 2→1로 갱신해야 턴 계산 등이 정상 동작합니다.
     *
     * @param roomId    대상 방 ID
     * @param sessionId 퇴장하는 플레이어의 세션 ID
     * @return 방장 퇴장 → null (방 삭제됨) / 그 외 → 업데이트된 Room
     */
    public Room leaveRoom(String roomId, String sessionId) {
        Room room = rooms.get(roomId);
        if (room == null) return null;

        // 해당 세션의 플레이어 탐색
        Player leaving = room.getPlayerBySession(sessionId);
        if (leaving == null) return room; // 이미 나간 플레이어 → 무시

        boolean isHost = (leaving.getPlayerIndex() == 0);

        if (isHost) {
            // 방장 퇴장: 방 전체 삭제
            rooms.remove(roomId);
            return null; // null = 방이 없어졌음을 Controller에 알림
        }

        // 일반 플레이어 퇴장: 목록에서 제거
        room.getPlayers().remove(leaving);

        // playerIndex 재정렬 (0부터 연속 번호 유지)
        for (int i = 0; i < room.getPlayers().size(); i++) {
            room.getPlayers().get(i).setPlayerIndex(i);
        }

        return room;
    }

    public Room getRoom(String roomId) {
        Room room = rooms.get(roomId);
        if (room != null) normalizeRoomConfig(room);
        return room;
    }
    public List<Room> getWaitingRooms() {
        return rooms.values().stream()
                .peek(this::normalizeRoomConfig)
                .filter(r -> r.getStatus() == StudyStatus.WAITING)
                .toList();
    }
    public List<Room> getAllRooms() {
        rooms.values().forEach(this::normalizeRoomConfig);
        return new ArrayList<>(rooms.values());
    }
    public void removeRoom(String roomId) { rooms.remove(roomId); }
}
