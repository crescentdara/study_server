package com.studyplatform.controller;

import com.studyplatform.dto.request.LobbyDrawingRequest;
import com.studyplatform.dto.response.LobbyDrawingMessage;
import com.studyplatform.service.LobbyDrawingService;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

@Controller
public class LobbyDrawingController {
    private static final String TOPIC = "/topic/lobby/drawing";

    private final LobbyDrawingService service;
    private final SimpMessagingTemplate messaging;

    public LobbyDrawingController(LobbyDrawingService service, SimpMessagingTemplate messaging) {
        this.service = service;
        this.messaging = messaging;
    }

    @MessageMapping("/study/lobby/drawing")
    public synchronized void drawing(@Payload LobbyDrawingRequest request) {
        LobbyDrawingMessage response = service.apply(request);
        if (response != null) messaging.convertAndSend(TOPIC, response);
    }
}
