package com.studyplatform.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
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
@RequestMapping("/api/calendar-events")
public class CalendarController {
    private final ObjectMapper objectMapper;
    private final Path calendarFile = Path.of(System.getProperty("user.dir"), "data", "calendar-events.json");

    public CalendarController(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @GetMapping
    public synchronized List<Map<String, String>> list() throws IOException {
        return readEvents();
    }

    @PostMapping
    public synchronized Map<String, String> create(@RequestBody Map<String, String> request) throws IOException {
        Map<String, String> event = normalizedEvent(request);
        event.put("id", UUID.randomUUID().toString());
        List<Map<String, String>> events = readEvents();
        events.add(event);
        writeEvents(events);
        return event;
    }

    @PutMapping("/{id}")
    public synchronized Map<String, String> update(@PathVariable String id, @RequestBody Map<String, String> request) throws IOException {
        List<Map<String, String>> events = readEvents();
        for (int index = 0; index < events.size(); index++) {
            if (id.equals(events.get(index).get("id"))) {
                Map<String, String> event = normalizedEvent(request);
                event.put("id", id);
                events.set(index, event);
                writeEvents(events);
                return event;
            }
        }
        throw new IllegalArgumentException("Calendar event not found.");
    }

    @DeleteMapping("/{id}")
    public synchronized void delete(@PathVariable String id) throws IOException {
        List<Map<String, String>> events = readEvents();
        events.removeIf(event -> id.equals(event.get("id")));
        writeEvents(events);
    }

    private Map<String, String> normalizedEvent(Map<String, String> request) {
        String date = request.getOrDefault("date", "").trim();
        String title = request.getOrDefault("title", "").trim();
        if (date.isBlank() || title.isBlank()) throw new IllegalArgumentException("Date and title are required.");
        LocalDate.parse(date);
        Map<String, String> event = new LinkedHashMap<>();
        event.put("date", date);
        event.put("title", title);
        event.put("time", request.getOrDefault("time", "").trim());
        event.put("color", request.getOrDefault("color", "#4ec9b0").trim());
        event.put("nickname", request.getOrDefault("nickname", "").trim());
        return event;
    }

    private List<Map<String, String>> readEvents() throws IOException {
        if (!Files.exists(calendarFile)) return new ArrayList<>();
        List<Map<String, String>> events = objectMapper.readValue(calendarFile.toFile(), new TypeReference<>() { });
        return events == null ? new ArrayList<>() : new ArrayList<>(events);
    }

    private void writeEvents(List<Map<String, String>> events) throws IOException {
        Files.createDirectories(calendarFile.getParent());
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(calendarFile.toFile(), events);
    }
}
