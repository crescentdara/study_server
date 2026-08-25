package com.studyplatform.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/announcements")
public class AnnouncementController {
    private final List<Map<String, String>> announcements = new ArrayList<>(List.of(
            Map.of("id", "v1.12", "date", "2026-08-25", "version", "v1.12", "title", "업데이트 센터 추가", "body", "좌측 알림 아이콘에서 최근 업데이트 내역을 확인할 수 있습니다.")
    ));
    @GetMapping
    public synchronized List<Map<String, String>> list() {
        return List.copyOf(announcements);
    }
    @PostMapping
    public synchronized Map<String, String> add(@RequestBody Map<String, String> request) {
        String title = request.getOrDefault("title", "").trim();
        String body = request.getOrDefault("body", "").trim();
        String nickname = request.getOrDefault("nickname", "").trim();
        if (title.isBlank() || body.isBlank()) throw new IllegalArgumentException("Title and body are required.");
        Map<String, String> item = Map.of("id", UUID.randomUUID().toString(), "date", LocalDate.now().toString(), "version", "NEW", "title", title, "body", body, "nickname", nickname);
        announcements.add(0, item);
        return item;
    }

    @DeleteMapping("/{id}")
    public synchronized void remove(@PathVariable String id) {
        announcements.removeIf(item -> id.equals(item.get("id")));
    }
}
