package com.studyplatform.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/announcements")
public class AnnouncementController {
    private final ObjectMapper objectMapper;
    private final Path announcementFile = Path.of(System.getProperty("user.dir"), "data", "announcements.json");

    public AnnouncementController(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @GetMapping
    public synchronized List<Map<String, Object>> list() throws IOException {
        return readAnnouncements();
    }

    @PostMapping
    public synchronized Map<String, Object> add(@RequestBody Map<String, String> request) throws IOException {
        String title = request.getOrDefault("title", "").trim();
        String body = request.getOrDefault("body", "").trim();
        String nickname = request.getOrDefault("nickname", "").trim();
        if (title.isBlank() || body.isBlank()) throw new IllegalArgumentException("Title and body are required.");

        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", UUID.randomUUID().toString());
        item.put("date", LocalDate.now().toString());
        item.put("version", "NEW");
        item.put("title", title);
        item.put("body", body);
        item.put("nickname", nickname);
        List<Map<String, Object>> announcements = readAnnouncements();
        announcements.add(0, item);
        writeAnnouncements(announcements);
        return item;
    }

    @DeleteMapping("/{id}")
    public synchronized void remove(@PathVariable String id) throws IOException {
        List<Map<String, Object>> announcements = readAnnouncements();
        announcements.removeIf(item -> id.equals(String.valueOf(item.get("id"))));
        writeAnnouncements(announcements);
    }

    private List<Map<String, Object>> readAnnouncements() throws IOException {
        if (!Files.exists(announcementFile)) return defaultAnnouncements();
        List<Map<String, Object>> announcements = objectMapper.readValue(announcementFile.toFile(), new TypeReference<>() { });
        return announcements == null ? new ArrayList<>() : new ArrayList<>(announcements);
    }

    private List<Map<String, Object>> defaultAnnouncements() {
        Map<String, Object> initial = new LinkedHashMap<>();
        initial.put("id", "v1.12");
        initial.put("date", "2026-08-25");
        initial.put("version", "v1.12");
        initial.put("title", "업데이트 센터 추가");
        initial.put("body", "상단 Notice 메뉴에서 최근 업데이트 내역을 확인할 수 있습니다.");
        initial.put("nickname", "");
        return new ArrayList<>(List.of(initial));
    }

    private void writeAnnouncements(List<Map<String, Object>> announcements) throws IOException {
        Files.createDirectories(announcementFile.getParent());
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(announcementFile.toFile(), announcements);
    }
}
