package com.studyplatform.controller;

import com.studyplatform.service.InfiniteStairsRecordService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/infinite-stairs")
public class InfiniteStairsController {
    private final InfiniteStairsRecordService recordService;

    public InfiniteStairsController(InfiniteStairsRecordService recordService) {
        this.recordService = recordService;
    }

    @GetMapping("/leaderboard")
    public Map<String, Object> leaderboard(@RequestParam(defaultValue = "10") int limit) {
        List<Map<String, Object>> records = recordService.leaderboard(limit);
        return Map.of("weekStart", recordService.weekStart(), "records", records);
    }

    @PostMapping("/record")
    public void record(@RequestBody RecordRequest request) {
        if (request != null) recordService.record(request.nickname, request.score);
    }

    public static class RecordRequest {
        public String nickname;
        public int score;
    }
}
