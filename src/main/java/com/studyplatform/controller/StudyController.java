package com.studyplatform.controller;

import com.studyplatform.dto.request.StudyMoveRequest;
import com.studyplatform.dto.response.ChatMessage;
import com.studyplatform.dto.response.StudyStateResponse;
import com.studyplatform.model.*;
import com.studyplatform.service.BaseballService;
import com.studyplatform.service.BingoService;
import com.studyplatform.service.BreakoutService;
import com.studyplatform.service.CatchMindService;
import com.studyplatform.service.WordChainService;
import com.studyplatform.service.RummikubService;
import com.studyplatform.service.DaVinciService;
import com.studyplatform.service.RushHourService;
import com.studyplatform.service.UbongoService;
import com.studyplatform.service.IncidentAvoidService;
import com.studyplatform.service.OmokService;
import com.studyplatform.service.OldMaidService;
import com.studyplatform.service.RoomService;
import com.studyplatform.service.SessionRegistry;
import com.studyplatform.service.TetrisService;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
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
    private final SessionRegistry sessionRegistry;
    private final BaseballService baseballService;
    private final BingoService bingoService;
    private final OmokService omokService;
    private final OldMaidService oldMaidService;
    private final TetrisService tetrisService;
    private final IncidentAvoidService incidentAvoidService;
    private final BreakoutService breakoutService;
    private final CatchMindService catchMindService;
    private final WordChainService wordChainService;
    private final RummikubService rummikubService;
    private final DaVinciService daVinciService;
    private final RushHourService rushHourService;
    private final UbongoService   ubongoService;

    public StudyController(SimpMessagingTemplate msg, RoomService roomService,
                           SessionRegistry sessionRegistry,
                           BaseballService baseballService, BingoService bingoService,
                           OmokService omokService, OldMaidService oldMaidService,
                           TetrisService tetrisService, IncidentAvoidService incidentAvoidService,
                           BreakoutService breakoutService, CatchMindService catchMindService,
                           WordChainService wordChainService, RummikubService rummikubService,
                           DaVinciService daVinciService, RushHourService rushHourService,
                           UbongoService ubongoService) {
        this.msg             = msg;
        this.roomService     = roomService;
        this.sessionRegistry = sessionRegistry;
        this.baseballService = baseballService;
        this.bingoService    = bingoService;
        this.omokService     = omokService;
        this.oldMaidService  = oldMaidService;
        this.tetrisService   = tetrisService;
        this.incidentAvoidService = incidentAvoidService;
        this.breakoutService = breakoutService;
        this.catchMindService = catchMindService;
        this.wordChainService = wordChainService;
        this.rummikubService  = rummikubService;
        this.daVinciService   = daVinciService;
        this.rushHourService  = rushHourService;
        this.ubongoService    = ubongoService;
    }

    /** 방 입장 시 현재 상태 동기화 */
    @MessageMapping("/study/{roomId}/enter")
    public void enterRoom(@DestinationVariable String roomId,
                          @Payload StudyMoveRequest request,
                          SimpMessageHeaderAccessor headerAccessor) {
        Room room = roomService.getRoom(roomId);
        if (room == null) return;

        // WebSocket 세션 ID → 앱 세션 ID 매핑 등록 (연결 끊김 감지에 사용)
        String wsSessionId = headerAccessor.getSessionId();
        if (wsSessionId != null && request.getSessionId() != null) {
            sessionRegistry.register(wsSessionId, roomId, request.getSessionId());
        }

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
        } else if (room.getStudyType() == StudyType.OMOK) {
            state = omokService.buildInitialState(room);
        } else if (room.getStudyType() == StudyType.OLDMAID) {
            state = oldMaidService.buildInitialState(room);
        } else if (room.getStudyType() == StudyType.TETRIS) {
            state = tetrisService.buildInitialState(room);
        } else if (room.getStudyType() == StudyType.BREAKOUT) {
            state = breakoutService.buildInitialState(room);
        } else if (room.getStudyType() == StudyType.CATCHMIND) {
            state = catchMindService.buildInitialState(room);
        } else if (room.getStudyType() == StudyType.WORD_CHAIN) {
            state = wordChainService.buildInitialState(room);
        } else if (room.getStudyType() == StudyType.RUMMIKUB) {
            state = rummikubService.buildInitialState(room);
        } else if (room.getStudyType() == StudyType.DAVINCI_CODE) {
            state = daVinciService.buildInitialState(room);
        } else if (room.getStudyType() == StudyType.RUSH_HOUR) {
            state = rushHourService.buildInitialState(room);
        } else if (room.getStudyType() == StudyType.UBONGO) {
            state = ubongoService.buildInitialState(room);
        } else {
            state = incidentAvoidService.buildInitialState(room);
        }
        broadcast(roomId, state);
        sendCatchMindSecret(room);
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

        // ── 퇴장 처리 ────────────────────────────────────────────────
        if ("LEAVE".equals(moveType)) {
            Room updated = roomService.leaveRoom(roomId, request.getSessionId());

            if (updated == null) {
                // 방장이 나감 → 방 삭제, 나머지 플레이어에게 알림
                // 'ROOM_CLOSED:' 접두어를 클라이언트가 감지해 로비로 이동
                broadcast(roomId, StudyStateResponse.builder()
                        .roomId(roomId)
                        .message("ROOM_CLOSED: host left")
                        .currentTurn(-1).winner(-1)
                        .build());
            } else {
                // 일반 플레이어 퇴장 → 남은 인원 정보 브로드캐스트
                String[] names = updated.getPlayers().stream()
                        .map(Player::getNickname).toArray(String[]::new);
                broadcast(roomId, StudyStateResponse.builder()
                        .roomId(roomId)
                        .studyType(updated.getStudyType())
                        .status(updated.getStatus())
                        .message(player.getNickname() + " has left. ("
                                + names.length + "/" + updated.getMaxPlayers() + " players)")
                        .currentTurn(0).winner(-1)
                        .playerNames(names)
                        .build());
            }
            return;
        }

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
            sendCatchMindSecret(room);
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
            sendCatchMindSecret(room);
            return;
        }

        // ── 일반 게임 액션 (SET_SECRET / GUESS / SET_BOARD / CALL_TOPIC) ──
        // Ubongo: players continue independently even after someone wins
        boolean isUbongoMove = "UBONGO_PLACE".equals(moveType) || "UBONGO_REMOVE".equals(moveType);
        if (room.getStatus() == StudyStatus.FINISHED && !isUbongoMove) { broadcastError(roomId, "Game already finished."); return; }
        if (room.getStatus() == StudyStatus.WAITING)  { broadcastError(roomId, "Game has not started yet."); return; }

        try {
            StudyStateResponse response = switch (room.getStudyType()) {
                case BASEBALL -> baseballService.processMove(room, player, request);
                case BINGO    -> bingoService.processMove(room, player, request);
                case OMOK     -> omokService.processMove(room, player, request);
                case OLDMAID  -> oldMaidService.processMove(room, player, request);
                case TETRIS   -> tetrisService.processMove(room, player, request);
                case INCIDENT_AVOID -> incidentAvoidService.processMove(room, player, request);
                case BREAKOUT -> breakoutService.processMove(room, player, request);
                case CATCHMIND   -> catchMindService.processMove(room, player, request);
                case WORD_CHAIN  -> wordChainService.processMove(room, player, request);
                case RUMMIKUB    -> rummikubService.processMove(room, player, request);
                case DAVINCI_CODE -> daVinciService.processMove(room, player, request);
                case RUSH_HOUR   -> rushHourService.processMove(room, player, request);
                case UBONGO      -> ubongoService.processMove(room, player, request);
            };
            broadcast(roomId, response);
            sendCatchMindSecret(room);
        } catch (IllegalArgumentException | IllegalStateException e) {
            broadcastError(roomId, room, e.getMessage());
        }
    }

    /** 로비 채팅 → /topic/lobby/chat 브로드캐스트 */
    @MessageMapping("/study/lobby/chat")
    public void lobbyChat(@Payload StudyMoveRequest request) {
        String nickname = request.getNickname() != null ? request.getNickname() : "unknown";
        String emoji    = request.getEmoji() != null ? request.getEmoji() : "";
        ChatMessage chatMessage = buildChatMessage(nickname, emoji, request);
        if (chatMessage == null) return;
        msg.convertAndSend("/topic/lobby/chat", chatMessage);
    }

    /** 방 채팅 메시지 처리 → /topic/chat/{roomId} 브로드캐스트 */
    @MessageMapping("/study/{roomId}/chat")
    public void chat(@DestinationVariable String roomId,
                     @Payload StudyMoveRequest request) {
        Room room = roomService.getRoom(roomId);
        if (room == null) return;

        Player player = room.getPlayerBySession(request.getSessionId());
        String nickname = player != null ? player.getNickname() : "unknown";
        String emoji = request.getEmoji() == null ? "" : request.getEmoji();
        ChatMessage chatMessage = buildChatMessage(nickname, emoji, request);
        if (chatMessage == null) return;
        msg.convertAndSend("/topic/chat/" + roomId, chatMessage);
    }

    private ChatMessage buildChatMessage(String nickname, String emoji, StudyMoveRequest request) {
        String type = request.getType() == null ? "TEXT" : request.getType().trim().toUpperCase();
        String text = request.getData();

        if ("IMAGE".equals(type)) {
            String imageUrl = request.getImageUrl();
            if (imageUrl == null || !imageUrl.startsWith("/uploads/chat/")) return null;

            ChatMessage message = new ChatMessage(nickname, text == null ? "" : text.trim(),
                    System.currentTimeMillis(), emoji);
            message.setType("IMAGE");
            message.setImageUrl(imageUrl);
            message.setFileName(request.getFileName());
            message.setFileSize(request.getFileSize());
            return message;
        }

        if (text == null || text.trim().isEmpty()) return null;
        ChatMessage msg = new ChatMessage(nickname, text.trim(), System.currentTimeMillis(), emoji);

        // Detect @mention: message starts with @nickname (followed by space or end)
        String trimmed = text.trim();
        if (trimmed.startsWith("@")) {
            int spaceIdx = trimmed.indexOf(' ');
            String mentioned = spaceIdx > 0 ? trimmed.substring(1, spaceIdx) : trimmed.substring(1);
            if (!mentioned.isEmpty()) msg.setMentionedNickname(mentioned);
            if (spaceIdx > 0) {
                String rest = trimmed.substring(spaceIdx + 1).trim();
                if (rest.startsWith("/voice")) {
                    String voiceText = rest.substring("/voice".length()).trim();
                    if (!voiceText.isEmpty()) {
                        msg.setVoiceRequested(true);
                        msg.setVoiceText(voiceText.length() > 80 ? voiceText.substring(0, 80) : voiceText);
                    }
                }
            }
        }

        return msg;
    }

    private void broadcast(String roomId, StudyStateResponse response) {
        msg.convertAndSend("/topic/study/" + roomId, response);
    }

    private StudyStateResponse buildInitialState(Room room) {
        return switch (room.getStudyType()) {
            case BASEBALL -> baseballService.buildInitialState(room);
            case BINGO    -> bingoService.buildInitialState(room);
            case OMOK     -> omokService.buildInitialState(room);
            case OLDMAID  -> oldMaidService.buildInitialState(room);
            case TETRIS   -> tetrisService.buildInitialState(room);
            case INCIDENT_AVOID -> incidentAvoidService.buildInitialState(room);
            case BREAKOUT -> breakoutService.buildInitialState(room);
            case CATCHMIND   -> catchMindService.buildInitialState(room);
            case WORD_CHAIN  -> wordChainService.buildInitialState(room);
            case RUMMIKUB    -> rummikubService.buildInitialState(room);
            case DAVINCI_CODE -> daVinciService.buildInitialState(room);
            case RUSH_HOUR   -> rushHourService.buildInitialState(room);
            case UBONGO      -> ubongoService.buildInitialState(room);
        };
    }

    private void sendCatchMindSecret(Room room) {
        if (room == null || room.getStudyType() != StudyType.CATCHMIND || room.getStatus() == StudyStatus.WAITING) {
            return;
        }
        StudyStateResponse secret = catchMindService.buildSecretState(room);
        int drawerIndex = secret.getCurrentTurn();
        if (drawerIndex < 0 || drawerIndex >= room.getPlayers().size()) return;
        String sessionId = room.getPlayers().get(drawerIndex).getSessionId();
        msg.convertAndSend("/topic/study/" + room.getRoomId() + "/secret/" + sessionId, secret);
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
