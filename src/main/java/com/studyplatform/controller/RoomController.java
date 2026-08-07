package com.studyplatform.controller;

import com.studyplatform.dto.request.CreateRoomRequest;
import com.studyplatform.dto.request.JoinRoomRequest;
import com.studyplatform.dto.response.RoomResponse;
import com.studyplatform.model.Room;
import com.studyplatform.service.RoomService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 방 관련 REST API 컨트롤러
 *
 * ─── REST API 엔드포인트 목록 ─────────────────────────────────────────────
 * GET  /api/rooms           → 대기 중인 방 목록 조회 (로비 화면용)
 * POST /api/rooms           → 방 생성
 * POST /api/rooms/{id}/join → 방 입장
 * GET  /api/rooms/{id}      → 특정 방 정보 조회
 *
 * ─── @RestController ─────────────────────────────────────────────────────
 * @Controller + @ResponseBody 조합입니다.
 * 메서드의 반환값이 자동으로 JSON으로 변환되어 HTTP 응답 body에 담깁니다.
 * Jackson 라이브러리가 Java 객체 ↔ JSON 변환을 담당합니다.
 *
 * ─── @RequestMapping("/api/rooms") ──────────────────────────────────────
 * 이 클래스의 모든 엔드포인트 URL 앞에 /api/rooms 가 자동으로 붙습니다.
 * @GetMapping = @RequestMapping(method = GET)의 단축 표현
 */
@RestController
@RequestMapping("/api/rooms")
public class RoomController {

    // 생성자 주입: Spring이 RoomService 빈을 자동으로 주입
    private final RoomService roomService;

    public RoomController(RoomService roomService) {
        this.roomService = roomService;
    }

    /**
     * 대기 중인 방 목록 조회
     * GET /api/rooms
     *
     * RoomResponse::from: 메서드 레퍼런스. Room → RoomResponse 변환 함수
     * .toList(): Java 16+에서 Stream을 List로 변환
     */
    @GetMapping
    public List<RoomResponse> getRooms() {
        return roomService.getWaitingRooms().stream()
                .map(RoomResponse::from)  // 도메인 객체 → DTO 변환
                .toList();
    }

    /**
     * 방 생성
     * POST /api/rooms
     *
     * @RequestBody: HTTP 요청 body의 JSON을 CreateRoomRequest 객체로 자동 역직렬화
     * ResponseEntity: HTTP 상태 코드와 응답 body를 함께 반환
     * ResponseEntity.ok(body): 200 OK + body
     */
    @PostMapping
    public ResponseEntity<RoomResponse> createRoom(@RequestBody CreateRoomRequest request) {
        Room room = roomService.createRoom(
                request.getRoomName(),
                request.getStudyType(),
                request.getNickname(),
                request.getSessionId(),
                request.getMaxPlayers(),
                request.getDigits(),
                request.getBoardSize(),
                request.getMode()
        );
        return ResponseEntity.ok(RoomResponse.from(room));
    }

    /**
     * 방 입장
     * POST /api/rooms/{roomId}/join
     *
     * @PathVariable: URL 경로의 {roomId} 값을 메서드 파라미터로 바인딩
     * ResponseEntity<?>: ?는 와일드카드 — 성공 시 RoomResponse, 실패 시 String을 반환
     *
     * try-catch:
     *   성공 → 200 OK + RoomResponse
     *   실패(방 없음, 만석 등) → 400 Bad Request + 에러 메시지 문자열
     */
    @PostMapping("/{roomId}/join")
    public ResponseEntity<?> joinRoom(@PathVariable String roomId,
                                      @RequestBody JoinRoomRequest request) {
        try {
            Room room = roomService.joinRoom(roomId, request.getNickname(), request.getSessionId());
            return ResponseEntity.ok(RoomResponse.from(room));
        } catch (RuntimeException e) {
            // 400 Bad Request: 클라이언트 요청 오류 (방 없음, 만석 등)
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * 특정 방 정보 조회
     * GET /api/rooms/{roomId}
     *
     * ResponseEntity.notFound().build(): 404 Not Found (body 없음)
     */
    @GetMapping("/{roomId}")
    public ResponseEntity<RoomResponse> getRoom(@PathVariable String roomId) {
        Room room = roomService.getRoom(roomId);
        if (room == null) return ResponseEntity.notFound().build(); // 404
        return ResponseEntity.ok(RoomResponse.from(room));          // 200
    }
}
