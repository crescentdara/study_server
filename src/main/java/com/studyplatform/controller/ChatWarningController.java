package com.studyplatform.controller;

import com.studyplatform.dto.request.ChatWarningRequest;
import com.studyplatform.service.ChatWarningService;
import com.studyplatform.service.LunchVoteService;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/chat/lobby/warnings")
public class ChatWarningController {
    private final ChatWarningService warningService;
    private final LunchVoteService lunchVoteService;
    private final SimpMessagingTemplate messagingTemplate;

    public ChatWarningController(ChatWarningService warningService, LunchVoteService lunchVoteService, SimpMessagingTemplate messagingTemplate) {
        this.warningService = warningService;
        this.lunchVoteService = lunchVoteService;
        this.messagingTemplate = messagingTemplate;
    }

    @GetMapping
    public Map<String, List<String>> warnings() {
        return warningService.snapshot();
    }

    @org.springframework.web.bind.annotation.PostMapping
    public Map<String, List<String>> add(@RequestBody ChatWarningRequest request) {
        return update(request, true);
    }

    @DeleteMapping
    public Map<String, List<String>> remove(@RequestBody ChatWarningRequest request) {
        return update(request, false);
    }

    @DeleteMapping("/all")
    public Map<String, List<String>> clear(@RequestBody ChatWarningRequest request) {
        validateModeratorAndTarget(request);
        Map<String, List<String>> state = warningService.clear(normalize(request.getTargetNickname()));
        messagingTemplate.convertAndSend("/topic/lobby/chat-warnings", state);
        return state;
    }

    private Map<String, List<String>> update(ChatWarningRequest request, boolean add) {
        validateModeratorAndTarget(request);
        String target = normalize(request.getTargetNickname());
        String color = normalize(request.getColor()).toLowerCase();
        if (!"yellow".equals(color) && !"red".equals(color)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Warning color must be yellow or red.");
        }
        Map<String, List<String>> state = add
                ? warningService.add(target, color)
                : warningService.removeLast(target, color);
        messagingTemplate.convertAndSend("/topic/lobby/chat-warnings", state);
        return state;
    }

    private void validateModeratorAndTarget(ChatWarningRequest request) {
        if (request == null || !lunchVoteService.isTodayWinner(normalize(request.getModeratorNickname()))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only today's lunch vote winner can manage warning cards.");
        }
        String target = normalize(request.getTargetNickname());
        if (target.isBlank() || target.equals(normalize(request.getModeratorNickname()))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Select another chat participant.");
        }
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
