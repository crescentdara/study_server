package com.studyplatform.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * WebSocket + STOMP 설정 클래스
 *
 * ─── WebSocket이란? ───────────────────────────────────────────────────────────
 * HTTP는 클라이언트가 요청(Request)해야만 서버가 응답(Response)하는 단방향 통신입니다.
 * 게임처럼 "상대방이 뭔가 했을 때 나도 즉시 알아야" 하는 경우에는 부적합합니다.
 *
 * WebSocket은 한 번 연결되면 서버와 클라이언트가 언제든지 서로 메시지를 주고받을 수
 * 있는 양방향(Full-Duplex) 통신 프로토콜입니다.
 *
 * ─── STOMP란? ─────────────────────────────────────────────────────────────────
 * STOMP(Simple Text Oriented Messaging Protocol)는 WebSocket 위에서 동작하는
 * 메시징 프로토콜로, "발행/구독(Pub/Sub)" 패턴을 지원합니다.
 *
 * Pub/Sub 패턴:
 *   - 발행(Publish): 서버가 특정 "채널"에 메시지를 보냄
 *   - 구독(Subscribe): 클라이언트가 특정 "채널"을 구독하면 그 채널의 메시지를 받음
 *
 * 예) /topic/study/abc123 채널을 구독한 두 플레이어는
 *     서버가 그 채널에 게임 상태를 보내면 동시에 받아서 화면이 업데이트됨.
 *
 * ─── 메시지 흐름 ──────────────────────────────────────────────────────────────
 * 클라이언트 → 서버:  /app/study/{roomId}/move  →  @MessageMapping 메서드 처리
 * 서버 → 클라이언트: /topic/study/{roomId}      →  구독한 모든 클라이언트에게 전송
 */
@Configuration
@EnableWebSocketMessageBroker  // STOMP 기반 메시지 브로커 활성화
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    /**
     * 메시지 브로커 설정
     *
     * "브로커(Broker)"는 메시지를 중간에서 전달해주는 역할입니다.
     * Spring이 제공하는 Simple Broker는 메모리 내에서 동작하는 경량 브로커입니다.
     * (실제 서비스에서는 RabbitMQ, Kafka 같은 외부 브로커를 사용하기도 함)
     */
    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // /topic 과 /queue 로 시작하는 목적지는 브로커가 처리
        //   - /topic : 1:N 브로드캐스트 (방 안의 모든 사람에게 전송)
        //   - /queue : 1:1 개인 메시지 (특정 사용자에게만 전송)
        config.enableSimpleBroker("/topic", "/queue");

        // 클라이언트 → 서버 메시지의 prefix
        // /app/study/123/move 로 보내면 @MessageMapping("/study/123/move") 메서드가 처리
        config.setApplicationDestinationPrefixes("/app");

        // 개인 메시지 prefix: /user/{sessionId}/queue/... 형태로 특정 유저에게 전송 가능
        config.setUserDestinationPrefix("/user");
    }

    /**
     * WebSocket 엔드포인트 등록
     *
     * 클라이언트가 WebSocket 연결을 시작할 URL을 등록합니다.
     * SockJS는 WebSocket을 지원하지 않는 구형 브라우저/환경을 위한 폴백(Fallback)입니다.
     * (WebSocket → 롱폴링 → 일반 폴링 순으로 자동 전환)
     */
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")            // 연결 URL: http://localhost:8080/ws
                .setAllowedOriginPatterns("*") // CORS 허용 (개발 환경용)
                .withSockJS();                 // SockJS 폴백 활성화
    }
}
