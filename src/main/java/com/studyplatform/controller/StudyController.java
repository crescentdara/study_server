package com.studyplatform.controller;

import com.studyplatform.dto.request.StudyMoveRequest;
import com.studyplatform.dto.response.ChatMessage;
import com.studyplatform.dto.response.StudyStateResponse;
import com.studyplatform.model.*;
import com.studyplatform.service.BaseballService;
import com.studyplatform.service.BingoService;
import com.studyplatform.service.OmokService;
import com.studyplatform.service.RoomService;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

/**
 * 게임 WebSocket(STOMP) 컨트롤러
 *
 * 처리하는 moveType 목록:
 *   START_GAME  - 방장이 게임 시작
 *   RESTART     - 방장이 재시작 (FINISHED → SETUP)
 *   SET_SECRET  - 야구 비밀 숫자 설정
 *   GUESS       - 야구 추측
 *   SET_BOARD   - 빙고 보드 주제 설정
 *   CALL_TOPIC  - 빙고 주제 호출
 */
@Controller
public class StudyController {

    private final SimpMessagingTemplate msg;
    private final RoomService roomService;
    private final BaseballService baseballService;
    private final BingoService bingoService;
    private final OmokService omokService;

    public StudyController(SimpMessagingTemplate msg, RoomService roomService,
                           BaseballService baseballService, BingoService bingoService,
                           OmokService omokService) {
        this.msg             = msg;
        this.roomService     = roomService;
        this.baseballService = baseballService;
        this.bingoService    = bingoService;
        this.omokService     = omokService;
    }

    /** 방 입장 시 현재 상태 동기화 */
    @MessageMapping("/study/{roomId}/enter")
    public void enterRoom(@DestinationVariable String roomId,
                          @Payload StudyMoveRequest request) {
        Room room = roomService.getRoom(roomId);
        if (room == null) return;

        StudyStateResponse state;
        if (room.getStatus() == StudyStatus.WAITING) {
            // 대기 중: 현재 입장 인원 정보를 브로드캐스트
            // 새 플레이어 입장 시 기존 플레이어들 화면도 업데이트됨
            String[] names = room.getPlayers().stream().map(Player::getNickname).toArray(String[]::new);
            int count = names.length;
            int max   = room.getMaxPlayers();
            String hostName = names[0];
            state = StudyStateResponse.builder()
                    .roomId(roomId).studyType(room.getStudyType()).status(room.getStatus())
                    .message(count + "/" + max + " players. Waiting for " + hostName + " to start...")
                    .currentTurn(0).winner(-1).playerNames(names).build();
        } else if (room.getStudyType() == StudyType.BASEBALL) {
            state = baseballService.buildInitialState(room);
        } else if (room.getStudyType() == StudyType.BINGO) {
            state = bingoService.buildInitialState(room);
        } else {
            state = omokService.buildInitialState(room);
        }
        broadcast(roomId, state);
    }

    /**
     * 게임 액션 처리
     * START_GAME / RESTART는 방장 전용으로 여기서 직접 처리하고,
     * 나머지는 각 게임 서비스로 위임합니다.
     */
    @MessageMapping("/study/{roomId}/move")
    public void processMove(@DestinationVariable String roomId,
                            @Payload StudyMoveRequest request) {
        Room room = roomService.getRoom(roomId);
        if (room == null) { broadcastError(roomId, "Room not found."); return; }

        Player player = room.getPlayerBySession(request.getSessionId());
        if (player == null) { broadcastError(roomId, "Not a member of this room."); return; }

        String moveType = request.getMoveType();

        // ── 게임 시작 (방장 전용) ──────────────────────────────────────
        if ("START_GAME".equals(moveType)) {
            if (player.getPlayerIndex() != 0) {
                broadcastError(roomId, "Only the host can start the game."); return;
            }
            try {
                roomService.startGame(room); // WAITING → SETUP, 게임 데이터 초기화
            } catch (RuntimeException e) {
                broadcastError(roomId, e.getMessage()); return;
            }
            // 초기 게임 상태 브로드캐스트
            StudyStateResponse state = buildInitialState(room);
            broadcast(roomId, state);
            return;
        }

        // ── 재시작 (방장 전용) ────────────────────────────────────────
        if ("RESTART".equals(moveType)) {
            if (player.getPlayerIndex() != 0) {
                broadcastError(roomId, "Only the host can restart."); return;
            }
            try {
                roomService.restartGame(room); // FINISHED → SETUP, 게임 데이터 초기화
            } catch (RuntimeException e) {
                broadcastError(roomId, e.getMessage()); return;
            }
            StudyStateResponse state = buildInitialState(room);
            broadcast(roomId, state);
            return;
        }

        // ── 일반 게임 액션 (SET_SECRET / GUESS / SET_BOARD / CALL_TOPIC) ──
        if (room.getStatus() == StudyStatus.FINISHED) { broadcastError(roomId, "Game already finished."); return; }
        if (room.getStatus() == StudyStatus.WAITING)  { broadcastError(roomId, "Game has not started yet."); return; }

        try {
            StudyStateResponse response = switch (room.getStudyType()) {
                case BASEBALL -> baseballService.processMove(room, player, request);
                case BINGO -> bingoService.processMove(room, player, request);
                case OMOK -> omokService.processMove(room, player, request);
            };
            broadcast(roomId, response);
        } catch (IllegalArgumentException | IllegalStateException e) {
            broadcastError(roomId, room, e.getMessage());
        }
    }

    /** 채팅 메시지 처리 → /topic/chat/{roomId} 브로드캐스트 */
    @MessageMapping("/study/{roomId}/chat")
    public void chat(@DestinationVariable String roomId,
                     @Payload StudyMoveRequest request) {
        Room room = roomService.getRoom(roomId);
        if (room == null) return;

        Player player = room.getPlayerBySession(request.getSessionId());
        String nickname = player != null ? player.getNickname() : "unknown";
        String text = request.getData();
        if (text == null || text.trim().isEmpty()) return;

        msg.convertAndSend("/topic/chat/" + roomId,
                new ChatMessage(nickname, text.trim(), System.currentTimeMillis()));
    }

    private void broadcast(String roomId, StudyStateResponse response) {
        msg.convertAndSend("/topic/study/" + roomId, response);
    }

    private StudyStateResponse buildInitialState(Room room) {
        return switch (room.getStudyType()) {
            case BASEBALL -> baseballService.buildInitialState(room);
            case BINGO -> bingoService.buildInitialState(room);
            case OMOK -> omokService.buildInitialState(room);
        };
    }

    private void broadcastError(String roomId, String text) {
        msg.convertAndSend("/topic/study/" + roomId,
                StudyStateResponse.builder().roomId(roomId)
                        .message("ERROR: " + text).currentTurn(-1).winner(-1).build());
    }

    private void broadcastError(String roomId, Room room, String text) {
        StudyStateResponse state = buildInitialState(room);
        state.setMessage("ERROR: " + text);
        msg.convertAndSend("/topic/study/" + roomId, state);
    }
}
