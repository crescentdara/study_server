package com.studyplatform.controller;

import com.studyplatform.service.TetrisRecordService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 테트리스 전적 REST API
 *
 * 판 안에서는 방 상태(gameData.records)로 전적이 내려가지만, 로비에서는 방에
 * 들어가지 않고도 티어 순위를 볼 수 있어야 하므로 따로 노출한다.
 */
@RestController
@RequestMapping("/api/tetris")
public class TetrisController {
    private final TetrisRecordService recordService;
    private final TetrisRecordService survivalRecordService;

    public TetrisController(
            TetrisRecordService recordService,
            @Qualifier("tetrisSurvivalRecordService") TetrisRecordService survivalRecordService
    ) {
        this.recordService = recordService;
        this.survivalRecordService = survivalRecordService;
    }

    /** 대전 티어·레이팅 순위 — 전체 공유 */
    @GetMapping("/leaderboard")
    public List<Map<String, Object>> leaderboard(@RequestParam(defaultValue = "10") int limit) {
        return recordService.leaderboard(limit);
    }

    /** 서바이벌 티어·레이팅 순위 — 대전과 따로 매긴다 */
    @GetMapping("/survival/leaderboard")
    public List<Map<String, Object>> survivalLeaderboard(@RequestParam(defaultValue = "10") int limit) {
        return survivalRecordService.leaderboard(limit);
    }
}
