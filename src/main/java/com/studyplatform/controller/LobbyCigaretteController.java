package com.studyplatform.controller;

import com.studyplatform.dto.request.LobbyCigaretteRequest;
import com.studyplatform.dto.response.LobbyCigaretteMessage;
import com.studyplatform.service.LobbyCigaretteService;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

@Controller
public class LobbyCigaretteController {
    private static final String TOPIC = "/topic/lobby/cigarette";

    private final LobbyCigaretteService service;
    private final SimpMessagingTemplate messaging;

    public LobbyCigaretteController(LobbyCigaretteService service, SimpMessagingTemplate messaging) {
        this.service = service;
        this.messaging = messaging;
    }

    @MessageMapping("/study/lobby/cigarette")
    public void cigarette(@Payload LobbyCigaretteRequest request) {
        LobbyCigaretteMessage response = service.apply(request);
        if (response != null) messaging.convertAndSend(TOPIC, response);
    }
}
