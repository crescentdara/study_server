package com.studyplatform.controller;

import com.studyplatform.dto.request.LobbyVendingRequest;
import com.studyplatform.dto.response.LobbyVendingMessage;
import com.studyplatform.service.LobbyVendingService;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

@Controller
public class LobbyVendingController {
    private static final String TOPIC = "/topic/lobby/vending";

    private final LobbyVendingService service;
    private final SimpMessagingTemplate messaging;

    public LobbyVendingController(LobbyVendingService service, SimpMessagingTemplate messaging) {
        this.service = service;
        this.messaging = messaging;
    }

    @MessageMapping("/study/lobby/vending")
    public void vending(@Payload LobbyVendingRequest request) {
        LobbyVendingMessage response = service.apply(request);
        if (response != null) messaging.convertAndSend(TOPIC, response);
    }
}
