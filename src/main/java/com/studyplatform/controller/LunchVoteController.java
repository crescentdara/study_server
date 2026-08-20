package com.studyplatform.controller;

import com.studyplatform.service.LunchVoteService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestController
@RequestMapping("/api/lunch")
public class LunchVoteController {
    private final LunchVoteService lunchVoteService;
    public LunchVoteController(LunchVoteService lunchVoteService) { this.lunchVoteService = lunchVoteService; }
    @GetMapping("/today") public Map<String, Object> today(@RequestParam(defaultValue = "") String nickname) { return lunchVoteService.today(nickname); }
    @PostMapping("/menus") public Map<String, Object> addMenu(@RequestBody Map<String, String> request) { return action(() -> lunchVoteService.addMenu(request.get("nickname"), request.get("menu"))); }
    @PostMapping("/votes") public Map<String, Object> vote(@RequestBody Map<String, String> request) { return action(() -> lunchVoteService.vote(request.get("nickname"), request.get("menuId"))); }
    private Map<String, Object> action(java.util.function.Supplier<Map<String, Object>> call) { try { return call.get(); } catch (IllegalArgumentException exception) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage()); } }
}
