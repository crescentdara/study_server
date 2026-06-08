package com.studyplatform.controller;

import com.studyplatform.dto.request.StudyMoveRequest;
import com.studyplatform.dto.response.ChatMessage;
import com.studyplatform.dto.response.StudyStateResponse;
import com.studyplatform.model.*;
import com.studyplatform.service.BaseballService;
import com.studyplatform.service.BingoService;
import com.studyplatform.service.RoomService;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

/**
 * 게임 WebSocket(STOMP) 컨트롤러
 *
 * moveType 처리 목록:
 *   START_GAME  - 방장: 게임 시작
 *   RESTART     - 방장: 재시작
 *   LEAVE       - 방 나가기 (방장이면 방 폐쇄, 아니면 인원 감소)
 *   SET_SECRET  - 야구: 비밀 숫자 설정
 *   GUESS       - 야구: 추측
 *   SET_BOARD   - 빙고: 보드 주제 설정
 *   CALL_TOPIC  - 빙고: 주제 호출
 *   (채팅은 /chat 별도 엔드포인트)
 */
@Controller
public class StudyController {

    private final SimpMessagingTemplate msg;
    private final RoomService roomService;
    private final BaseballService baseballService;
    private final BingoService bingoService;

    public StudyController(SimpMessagingTemplate msg, RoomService roomService,
                           BaseballService baseballService, BingoService bingoService) {
        this.msg             = msg;
        this.roomService     = roomService;
        this.baseballService = baseballService;
        this.bingoService    = bingoService;
    }

    /** 방 입장 시 현재 상태 동기화 */
    @MessageMapping("/study/{roomId}/enter")
    public void enterRoom(@DestinationVariable String roomId,
                          @Payload StudyMoveRequest request) {
        Room room = roomService.getRoom(roomId);
        if (room == null) return;

        StudyStateResponse state;
        if (room.getStatus() == StudyStatus.WAITING) {
            String[] names = room.getPlayers().stream().map(Player::getNickname).toArray(String[]::new);
            state = StudyStateResponse.builder()
                    .roomId(roomId).studyType(room.getStudyType()).status(room.getStatus())
                    .message(names.length + "/" + room.getMaxPlayers() + " players. Waiting for " + names[0] + " to start...")
                    .currentTurn(0).winner(-1).playerNames(names).build();
        } else if (room.getStudyType() == StudyType.BASEBALL) {
            state = baseballService.buildInitialState(room);
        } else {
            state = bingoService.buildInitialState(room);
        }
        broadcast(roomId, state);
    }

    /** 게임 액션 처리 */
    @MessageMapping("/study/{roomId}/move")
    public void processMove(@DestinationVariable String roomId,
                            @Payload StudyMoveRequest request) {
        Room room = roomService.getRoom(roomId);
        if (room == null) { broadcastError(roomId, "Room not found."); return; }

        Player player = room.getPlayerBySession(request.getSessionId());
        if (player == null) { broadcastError(roomId, "Not a member of this room."); return; }

        String moveType = request.getMoveType();

        // ── 방 나가기 ────────────────────────────────────────────────────────
        if ("LEAVE".equals(moveType)) {
            boolean wasHost = (player.getPlayerIndex() == 0);
            Room remaining = roomService.leaveRoom(roomId, request.getSessionId());

            if (wasHost || remaining == null) {
                // 방장이 나갔거나 방에 아무도 없으면 → 방 완전 폐쇄
                // 남아있는 플레이어들에게 방이 닫혔음을 알림
                roomService.removeRoom(roomId); // 혹시 남아있으면 삭제
                StudyStateResponse closed = StudyStateResponse.builder()
                        .roomId(roomId)
                        .message("ROOM_CLOSED: The host has left. Room is closed.")
                        .status(StudyStatus.FINISHED)
                        .currentTurn(-1).winner(-1).build();
                broadcast(roomId, closed);
            } else {
                // 일반 플레이어가 나감 → 남은 인원에게 업데이트 브로드캐스트
                String[] names = remaining.getPlayers().stream().map(Player::getNickname).toArray(String[]::new);
                StudyStateResponse updated = StudyStateResponse.builder()
                        .roomId(roomId).studyType(remaining.getStudyType())
                        .status(remaining.getStatus())
                        .message(player.getNickname() + " has left. (" + names.length + " players remaining)")
                        .currentTurn(remaining.getStatus() == StudyStatus.PLAYING ? 0 : -1)
                        .winner(-1).playerNames(names).build();
                broadcast(roomId, updated);
            }
            return;
        }

        // ── 게임 시작 (방장 전용) ─────────────────────────────────────────
        if ("START_GAME".equals(moveType)) {
            if (player.getPlayerIndex() != 0) { broadcastError(roomId, "Only the host can start."); return; }
            try {
                roomService.startGame(room);
            } catch (RuntimeException e) { broadcastError(roomId, e.getMessage()); return; }
            broadcast(roomId, room.getStudyType() == StudyType.BASEBALL
                    ? baseballService.buildInitialState(room)
                    : bingoService.buildInitialState(room));
            return;
        }

        // ── 재시작 (방장 전용) ───────────────────────────────────────────
        if ("RESTART".equals(moveType)) {
            if (player.getPlayerIndex() != 0) { broadcastError(roomId, "Only the host can restart."); return; }
            try {
                roomService.restartGame(room);
            } catch (RuntimeException e) { broadcastError(roomId, e.getMessage()); return; }
            broadcast(roomId, room.getStudyType() == StudyType.BASEBALL
                    ? baseballService.buildInitialState(room)
                    : bingoService.buildInitialState(room));
            return;
        }

        // ── 일반 게임 액션 ────────────────────────────────────────────────
        if (room.getStatus() == StudyStatus.FINISHED) { broadcastError(roomId, "Game already finished."); return; }
        if (room.getStatus() == StudyStatus.WAITING)  { broadcastError(roomId, "Game has not started yet."); return; }

        try {
            StudyStateResponse response = room.getStudyType() == StudyType.BASEBALL
                    ? baseballService.processMove(room, player, request)
                    : bingoService.processMove(room, player, request);
            broadcast(roomId, response);
        } catch (IllegalArgumentException | IllegalStateException e) {
            broadcastError(roomId, e.getMessage());
        }
    }

    /**
     * 채팅 메시지 처리
     * 클라이언트가 보낸 이모지(request.getEmoji())를 ChatMessage에 포함해
     * 모든 구독자가 발신자의 이모지를 볼 수 있게 합니다.
     */
    @MessageMapping("/study/{roomId}/chat")
    public void chat(@DestinationVariable String roomId,
                     @Payload StudyMoveRequest request) {
        Room room = roomService.getRoom(roomId);
        if (room == null) return;

        Player player = room.getPlayerBySession(request.getSessionId());
        String nickname = player != null ? player.getNickname() : "unknown";
        String text     = request.getData();
        String emoji    = request.getEmoji() != null ? request.getEmoji() : "";

        if (text == null || text.trim().isEmpty()) return;

        // 이모지를 포함한 ChatMessage 생성 후 브로드캐스트
        msg.convertAndSend("/topic/chat/" + roomId,
                new ChatMessage(nickname, text.trim(), System.currentTimeMillis(), emoji));
    }

    private void broadcast(String roomId, StudyStateResponse response) {
        msg.convertAndSend("/topic/study/" + roomId, response);
    }

    private void broadcastError(String roomId, String text) {
        msg.convertAndSend("/topic/study/" + roomId,
                StudyStateResponse.builder().roomId(roomId)
                        .message("ERROR: " + text).currentTurn(-1).winner(-1).build());
    }
}
