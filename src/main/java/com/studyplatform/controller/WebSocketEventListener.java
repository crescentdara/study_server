package com.studyplatform.controller;

import com.studyplatform.dto.response.StudyStateResponse;
import com.studyplatform.model.Player;
import com.studyplatform.model.Room;
import com.studyplatform.service.RoomService;
import com.studyplatform.service.SessionRegistry;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

@Component
public class WebSocketEventListener {

    private final RoomService roomService;
    private final SessionRegistry sessionRegistry;
    private final SimpMessagingTemplate msg;

    public WebSocketEventListener(RoomService roomService,
                                  SessionRegistry sessionRegistry,
                                  SimpMessagingTemplate msg) {
        this.roomService     = roomService;
        this.sessionRegistry = sessionRegistry;
        this.msg             = msg;
    }

    @EventListener
    public void handleDisconnect(SessionDisconnectEvent event) {
        String wsSessionId = event.getSessionId();
        SessionRegistry.SessionInfo info = sessionRegistry.remove(wsSessionId);
        if (info == null) return; // 매핑이 없으면 (로비에만 있던 유저 등) 무시

        String roomId      = info.roomId();
        String appSession  = info.appSessionId();

        Room room = roomService.getRoom(roomId);
        if (room == null) return;

        Player leaving = room.getPlayerBySession(appSession);
        if (leaving == null) return;

        String nickname  = leaving.getNickname();
        boolean isHost   = (leaving.getPlayerIndex() == 0);

        Room updated = roomService.leaveRoom(roomId, appSession);

        if (updated == null) {
            // 방장이 나감 → 방 삭제, 나머지에게 알림
            msg.convertAndSend("/topic/study/" + roomId,
                    StudyStateResponse.builder()
                            .roomId(roomId)
                            .message("ROOM_CLOSED: host left")
                            .currentTurn(-1).winner(-1)
                            .build());
        } else {
            // 일반 플레이어 나감 → 남은 인원 브로드캐스트
            String[] names = updated.getPlayers().stream()
                    .map(Player::getNickname).toArray(String[]::new);
            msg.convertAndSend("/topic/study/" + roomId,
                    StudyStateResponse.builder()
                            .roomId(roomId)
                            .studyType(updated.getStudyType())
                            .status(updated.getStatus())
                            .message(nickname + " has left. ("
                                    + names.length + "/" + updated.getMaxPlayers() + " players)")
                            .currentTurn(0).winner(-1)
                            .playerNames(names)
                            .build());
        }
    }
}
