package com.studyplatform.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 서버 인스턴스 식별
 *
 * 방·게임·로비 채팅은 모두 서버 메모리에 있어서 재배포하면 사라진다. 그런데 열어 둔
 * 브라우저는 그대로 남아 없는 방을 붙잡고 있게 되므로, 클라이언트가 '서버가 새로 떴다'는
 * 사실을 알아챌 수 있어야 한다.
 *
 * bootId는 서버가 뜰 때마다 새로 만들어지므로, 클라이언트는 이 값이 바뀌면 재배포된
 * 것으로 보고 화면을 새로 불러온다.
 */
@RestController
@RequestMapping("/api/server")
public class ServerInstanceController {
    private final String bootId = UUID.randomUUID().toString();
    private final long startedAt = System.currentTimeMillis();

    @GetMapping("/instance")
    public Map<String, Object> instance() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("bootId", bootId);
        result.put("startedAt", startedAt);
        return result;
    }
}
