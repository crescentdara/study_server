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
import java.time.Instant;
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
    private final Path commentFile = Path.of(System.getProperty("user.dir"), "data", "calendar-comments.json");

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
        List<Map<String, String>> comments = readComments();
        comments.removeIf(comment -> id.equals(comment.get("eventId")));
        writeComments(comments);
    }

    @GetMapping("/{eventId}/comments")
    public synchronized List<Map<String, String>> listComments(@PathVariable String eventId) throws IOException {
        return readComments().stream()
                .filter(comment -> eventId.equals(comment.get("eventId")))
                .toList();
    }

    @GetMapping("/comment-status")
    public synchronized List<Map<String, String>> commentStatus() throws IOException {
        Map<String, Map<String, String>> statusByEvent = new LinkedHashMap<>();
        for (Map<String, String> comment : readComments()) {
            String eventId = comment.getOrDefault("eventId", "");
            if (eventId.isBlank()) continue;
            Map<String, String> status = statusByEvent.computeIfAbsent(eventId, ignored -> {
                Map<String, String> value = new LinkedHashMap<>();
                value.put("eventId", eventId);
                value.put("count", "0");
                value.put("latestCommentAt", "");
                return value;
            });
            status.put("count", String.valueOf(Integer.parseInt(status.get("count")) + 1));
            String createdAt = comment.getOrDefault("createdAt", "");
            if (createdAt.compareTo(status.get("latestCommentAt")) > 0) status.put("latestCommentAt", createdAt);
        }
        return new ArrayList<>(statusByEvent.values());
    }

    @PostMapping("/{eventId}/comments")
    public synchronized Map<String, String> createComment(@PathVariable String eventId, @RequestBody Map<String, String> request) throws IOException {
        requireEvent(eventId);
        Map<String, String> comment = normalizedComment(request);
        comment.put("id", UUID.randomUUID().toString());
        comment.put("eventId", eventId);
        comment.put("createdAt", Instant.now().toString());
        comment.put("updatedAt", comment.get("createdAt"));
        List<Map<String, String>> comments = readComments();
        comments.add(comment);
        writeComments(comments);
        return comment;
    }

    @PutMapping("/{eventId}/comments/{commentId}")
    public synchronized Map<String, String> updateComment(@PathVariable String eventId, @PathVariable String commentId,
                                                           @RequestBody Map<String, String> request) throws IOException {
        List<Map<String, String>> comments = readComments();
        for (Map<String, String> comment : comments) {
            if (eventId.equals(comment.get("eventId")) && commentId.equals(comment.get("id"))) {
                comment.put("content", commentContent(request));
                comment.put("updatedAt", Instant.now().toString());
                writeComments(comments);
                return comment;
            }
        }
        throw new IllegalArgumentException("Calendar comment not found.");
    }

    @DeleteMapping("/{eventId}/comments/{commentId}")
    public synchronized void deleteComment(@PathVariable String eventId, @PathVariable String commentId) throws IOException {
        List<Map<String, String>> comments = readComments();
        boolean removed = comments.removeIf(comment -> eventId.equals(comment.get("eventId")) && commentId.equals(comment.get("id")));
        if (!removed) throw new IllegalArgumentException("Calendar comment not found.");
        writeComments(comments);
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

    private Map<String, String> normalizedComment(Map<String, String> request) {
        Map<String, String> comment = new LinkedHashMap<>();
        comment.put("content", commentContent(request));
        comment.put("nickname", request.getOrDefault("nickname", "").trim());
        return comment;
    }

    private String commentContent(Map<String, String> request) {
        String content = request.getOrDefault("content", "").trim();
        if (content.isBlank()) throw new IllegalArgumentException("Comment content is required.");
        if (content.length() > 500) throw new IllegalArgumentException("Comment must be 500 characters or fewer.");
        return content;
    }

    private void requireEvent(String eventId) throws IOException {
        boolean exists = readEvents().stream().anyMatch(event -> eventId.equals(event.get("id")));
        if (!exists) throw new IllegalArgumentException("Calendar event not found.");
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

    private List<Map<String, String>> readComments() throws IOException {
        if (!Files.exists(commentFile)) return new ArrayList<>();
        List<Map<String, String>> comments = objectMapper.readValue(commentFile.toFile(), new TypeReference<>() { });
        return comments == null ? new ArrayList<>() : new ArrayList<>(comments);
    }

    private void writeComments(List<Map<String, String>> comments) throws IOException {
        Files.createDirectories(commentFile.getParent());
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(commentFile.toFile(), comments);
    }
}
