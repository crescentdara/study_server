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
 * ─── REST vs WebSocket 컨트롤러 차이 ─────────────────────────────────────────
 * @RestController (RoomController):
 *   - HTTP 요청/응답 (클라이언트가 요청해야만 서버가 응답)
 *   - @GetMapping, @PostMapping 사용
 *
 * @Controller (이 클래스):
 *   - STOMP 메시지 처리 (양방향 실시간 통신)
 *   - @MessageMapping 사용
 *   - 서버가 먼저 메시지를 보낼 수 있음 (브로드캐스트)
 *
 * ─── 메시지 채널 구조 ─────────────────────────────────────────────────────────
 * 클라이언트 → 서버 (send/publish):
 *   /app/study/{roomId}/enter  - 방 입장, 현재 상태 요청
 *   /app/study/{roomId}/move   - 게임 액션 (비밀숫자 설정, 추측, 주제 호출)
 *   /app/study/{roomId}/chat   - 채팅 메시지 전송
 *
 * 서버 → 클라이언트 (broadcast/subscribe):
 *   /topic/study/{roomId}      - 게임 상태 업데이트
 *   /topic/chat/{roomId}       - 채팅 메시지
 *
 * ─── SimpMessagingTemplate ───────────────────────────────────────────────────
 * Spring이 제공하는 STOMP 메시지 전송 도우미입니다.
 * convertAndSend(destination, payload):
 *   - payload(Java 객체)를 JSON으로 자동 변환
 *   - 해당 destination을 구독한 모든 클라이언트에게 전송
 */
@Controller
public class StudyController {

    // STOMP 메시지를 특정 채널로 전송하는 Spring 내장 도구
    private final SimpMessagingTemplate msg;
    private final RoomService roomService;
    private final BaseballService baseballService;
    private final BingoService bingoService;

    /**
     * 생성자 주입 (Constructor Injection)
     *
     * @Autowired 없이도 Spring이 생성자가 하나면 자동으로 빈을 주입합니다.
     * 필드 주입(@Autowired)보다 생성자 주입이 권장되는 이유:
     *   - final 키워드로 불변성 보장 가능
     *   - 테스트 시 Mock 객체 주입이 쉬움
     *   - 순환 의존성 문제를 컴파일 타임에 감지 가능
     */
    public StudyController(SimpMessagingTemplate msg, RoomService roomService,
                           BaseballService baseballService, BingoService bingoService) {
        this.msg             = msg;
        this.roomService     = roomService;
        this.baseballService = baseballService;
        this.bingoService    = bingoService;
    }

    /**
     * 방 입장 시 현재 게임 상태 동기화
     *
     * 플레이어가 방에 WebSocket으로 연결할 때 호출됩니다.
     * 현재 방 상태(WAITING/SETUP/PLAYING)에 맞는 초기 데이터를
     * 브로드캐스트하여 모든 플레이어의 화면을 업데이트합니다.
     *
     * @MessageMapping: /app 접두사가 제거된 경로와 매핑
     *   클라이언트가 /app/study/{roomId}/enter 로 메시지 전송 →
     *   이 메서드가 실행됨
     *
     * @DestinationVariable: URL 경로의 {roomId} 값 추출
     *   REST의 @PathVariable과 동일한 역할
     *
     * @Payload: STOMP 메시지 body(JSON)를 StudyMoveRequest 객체로 자동 역직렬화
     */
    @MessageMapping("/study/{roomId}/enter")
    public void enterRoom(@DestinationVariable String roomId,
                          @Payload StudyMoveRequest request) {
        Room room = roomService.getRoom(roomId);
        if (room == null) return; // 방이 없으면 무시

        StudyStateResponse state;

        if (room.getStatus() == StudyStatus.WAITING) {
            // 아직 인원이 다 모이지 않은 경우 → 현황 메시지만 전송
            String[] names = room.getPlayers().stream()
                    .map(Player::getNickname).toArray(String[]::new);
            state = StudyStateResponse.builder()
                    .roomId(roomId)
                    .studyType(room.getStudyType())
                    .status(room.getStatus())
                    .message(room.getPlayers().size() + "/" + room.getMaxPlayers() + " players joined.")
                    .currentTurn(0)
                    .winner(-1)
                    .playerNames(names)
                    .build();
        } else if (room.getStudyType() == StudyType.BASEBALL) {
            // 야구: 현재 게임 상태(비밀숫자 설정 현황, 추측 기록 등)를 포함한 응답 생성
            state = baseballService.buildInitialState(room);
        } else {
            // 빙고: 보드 설정 현황 또는 진행 중인 게임 상태를 포함한 응답 생성
            state = bingoService.buildInitialState(room);
        }

        // /topic/study/{roomId}를 구독한 모든 클라이언트에게 브로드캐스트
        broadcast(roomId, state);
    }

    /**
     * 게임 액션 처리 (핵심 메서드)
     *
     * 게임 중 발생하는 모든 액션을 처리합니다:
     *   야구: SET_SECRET(비밀숫자 설정), GUESS(추측)
     *   빙고: SET_BOARD(보드 주제 설정), CALL_TOPIC(주제 호출)
     *
     * 처리 후 결과를 방의 모든 구독자에게 브로드캐스트하여
     * 화면이 실시간으로 업데이트되게 합니다.
     */
    @MessageMapping("/study/{roomId}/move")
    public void processMove(@DestinationVariable String roomId,
                            @Payload StudyMoveRequest request) {
        Room room = roomService.getRoom(roomId);
        // 방이 없으면 에러 브로드캐스트 (null 체크 후 조기 반환)
        if (room == null) { broadcastError(roomId, "Room not found."); return; }

        // 세션 ID로 이 방의 플레이어인지 확인
        Player player = room.getPlayerBySession(request.getSessionId());
        if (player == null) { broadcastError(roomId, "Not a member of this room."); return; }

        // 종료된 게임이나 대기 중인 방에서는 액션 불가
        if (room.getStatus() == StudyStatus.FINISHED) { broadcastError(roomId, "Game already finished."); return; }
        if (room.getStatus() == StudyStatus.WAITING)  { broadcastError(roomId, "Waiting for more players."); return; }

        try {
            // 게임 타입에 따라 다른 서비스가 처리 (전략 패턴 유사)
            StudyStateResponse response = room.getStudyType() == StudyType.BASEBALL
                    ? baseballService.processMove(room, player, request)
                    : bingoService.processMove(room, player, request);

            // 처리 성공 → 업데이트된 게임 상태를 모두에게 전송
            broadcast(roomId, response);

        } catch (IllegalArgumentException | IllegalStateException e) {
            // 잘못된 입력이나 순서 위반 → 에러 메시지 전송
            // (try-catch로 서버가 죽지 않고 에러를 클라이언트에 전달)
            broadcastError(roomId, e.getMessage());
        }
    }

    /**
     * 채팅 메시지 처리
     *
     * 게임 채널(/topic/study/)과 채팅 채널(/topic/chat/)을 분리한 이유:
     *   - 게임 상태 업데이트와 채팅을 클라이언트에서 별도로 처리 가능
     *   - 채팅만 필터링하거나 게임 메시지만 필터링하기 용이
     *
     * 처리 흐름:
     *   클라이언트 → /app/study/{roomId}/chat
     *   → 이 메서드 실행
     *   → /topic/chat/{roomId} 로 브로드캐스트
     *   → 해당 채널 구독자 전원 수신
     */
    @MessageMapping("/study/{roomId}/chat")
    public void chat(@DestinationVariable String roomId,
                     @Payload StudyMoveRequest request) {
        Room room = roomService.getRoom(roomId);
        if (room == null) return;

        // 세션 ID로 닉네임 조회 (방 멤버가 아니면 "unknown" 으로 표시)
        Player player = room.getPlayerBySession(request.getSessionId());
        String nickname = player != null ? player.getNickname() : "unknown";

        String text = request.getData();
        if (text == null || text.trim().isEmpty()) return; // 빈 메시지 무시

        // ChatMessage 생성: 닉네임, 메시지 내용, 서버 수신 시각
        ChatMessage chatMsg = new ChatMessage(nickname, text.trim(), System.currentTimeMillis());

        // 채팅 전용 채널로 브로드캐스트 (/topic/chat/{roomId})
        msg.convertAndSend("/topic/chat/" + roomId, chatMsg);
    }

    /**
     * 게임 상태를 방의 모든 구독자에게 브로드캐스트
     *
     * convertAndSend: Java 객체(StudyStateResponse) → JSON 자동 변환 → 전송
     * Spring의 Jackson 라이브러리가 직렬화를 담당합니다.
     */
    private void broadcast(String roomId, StudyStateResponse response) {
        msg.convertAndSend("/topic/study/" + roomId, response);
    }

    /**
     * 에러 메시지 브로드캐스트
     *
     * 정상 게임 상태 대신 에러 내용을 담은 응답을 전송합니다.
     * 클라이언트는 message가 "ERROR:"로 시작하면 에러로 처리합니다.
     * currentTurn=-1, winner=-1 로 설정해 게임 상태가 없음을 표시합니다.
     */
    private void broadcastError(String roomId, String text) {
        StudyStateResponse err = StudyStateResponse.builder()
                .roomId(roomId)
                .message("ERROR: " + text)
                .currentTurn(-1)
                .winner(-1)
                .build();
        msg.convertAndSend("/topic/study/" + roomId, err);
    }
}
