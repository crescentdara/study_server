package com.studyplatform.service;

import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket 세션 ID ↔ 앱 세션(roomId + appSessionId) 매핑 레지스트리
 *
 * STOMP 연결이 끊겼을 때 SessionDisconnectEvent에서 받는 ID는
 * WebSocket 세션 ID이므로, 이를 앱 레벨 sessionId로 역조회하기 위해 필요합니다.
 */
@Service
public class SessionRegistry {

    public record SessionInfo(String roomId, String appSessionId) {}

    private final Map<String, SessionInfo> map = new ConcurrentHashMap<>();

    public void register(String wsSessionId, String roomId, String appSessionId) {
        map.put(wsSessionId, new SessionInfo(roomId, appSessionId));
    }

    public SessionInfo remove(String wsSessionId) {
        return map.remove(wsSessionId);
    }
}
