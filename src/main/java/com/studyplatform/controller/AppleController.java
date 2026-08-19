package com.studyplatform.controller;

import com.studyplatform.dto.request.AppleSoloRequest;
import com.studyplatform.service.AppleBoxRecordService;
import com.studyplatform.service.AppleSoloService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

/**
 * 사과게임 REST API
 *
 * 사과게임은 방 없이 혼자 하는 게임이라 WebSocket(방 상태 브로드캐스트)을 쓰지 않고
 * 이 REST만으로 한 판이 돌아간다. 랭킹은 방에 들어가지 않아도 보이도록 함께 노출한다.
 */
@RestController
@RequestMapping("/api/apple")
public class AppleController {
    private final AppleSoloService soloService;
    private final AppleBoxRecordService recordService;

    public AppleController(AppleSoloService soloService, AppleBoxRecordService recordService) {
        this.soloService = soloService;
        this.recordService = recordService;
    }

    /** 누적 최고 점수 랭킹 — 전체 공유 */
    @GetMapping("/leaderboard")
    public List<Map<String, Object>> leaderboard(@RequestParam(defaultValue = "10") int limit) {
        return recordService.leaderboard(limit);
    }

    /** 이번 주 최고 점수 랭킹 — 누적과 따로 관리되고 월요일에 초기화된다 */
    @GetMapping("/leaderboard/weekly")
    public Map<String, Object> weeklyLeaderboard(@RequestParam(defaultValue = "10") int limit) {
        return Map.of(
                "weekStart", recordService.currentWeekStart(),
                "records", recordService.weeklyLeaderboard(limit)
        );
    }

    /** 새 판 시작 — 누른 순간 보드가 만들어지고 바로 시작한다 */
    @PostMapping("/start")
    public Map<String, Object> start(@RequestBody(required = false) AppleSoloRequest request) {
        return soloService.start(request == null ? null : request.getNickname(), request == null ? null : request.getMode());
    }

    /** 드래그한 사각 범위 정리 시도 (합이 10인지는 서버가 검증) */
    @PostMapping("/clear")
    public Map<String, Object> clear(@RequestBody AppleSoloRequest request) {
        return expired(() -> soloService.clear(
                request.getInstanceId(),
                request.getR1(), request.getC1(), request.getR2(), request.getC2()
        ));
    }

    /** 제한 시간 종료·중단 알림 — 이 시점에 랭킹에 기록된다 */
    @PostMapping("/finish")
    public Map<String, Object> finish(@RequestBody AppleSoloRequest request) {
        return expired(() -> soloService.finish(request.getInstanceId()));
    }

    @PostMapping("/verify")
    public Map<String, Object> verify(@RequestBody AppleSoloRequest request) {
        return expired(() -> soloService.verify(request.getInstanceId()));
    }

    @PostMapping("/rearrange")
    public Map<String, Object> rearrange(@RequestBody AppleSoloRequest request) {
        return expired(() -> soloService.rearrange(request.getInstanceId()));
    }

    /** 퍼즈 전환 (P키) — 화면을 가리는 동안 시간도 함께 멈춘다 */
    @PostMapping("/pause")
    public Map<String, Object> pause(@RequestBody AppleSoloRequest request) {
        return expired(() -> soloService.pause(request.getInstanceId(), request.isPaused()));
    }

    /** 현재 상태 조회 */
    @GetMapping("/state")
    public Map<String, Object> state(@RequestParam String instanceId) {
        return expired(() -> soloService.state(instanceId));
    }

    /**
     * 서버를 다시 켜거나 오래 방치한 판은 사라진다. 그럴 때 500이 아니라 410으로
     * 알려주어 클라이언트가 '새 판 시작'으로 안내할 수 있게 한다.
     */
    private Map<String, Object> expired(java.util.function.Supplier<Map<String, Object>> action) {
        try {
            return action.get();
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.GONE, exception.getMessage());
        }
    }
}
