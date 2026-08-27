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
@RequestMapping("/api/suggestions")
public class SuggestionController {
    private static final String OWNER_NICKNAME = "뚱이";
    private final ObjectMapper objectMapper;
    private final Path suggestionFile = Path.of(System.getProperty("user.dir"), "data", "suggestions.json");

    public SuggestionController(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @GetMapping
    public synchronized List<Map<String, Object>> list() throws IOException {
        return readSuggestions();
    }

    @PostMapping
    public synchronized Map<String, Object> add(@RequestBody Map<String, String> request) throws IOException {
        String title = request.getOrDefault("title", "").trim();
        String body = request.getOrDefault("body", "").trim();
        String nickname = request.getOrDefault("nickname", "").trim();
        if (title.isBlank() || body.isBlank()) throw new IllegalArgumentException("Title and body are required.");

        Map<String, Object> suggestion = new LinkedHashMap<>();
        suggestion.put("id", UUID.randomUUID().toString());
        suggestion.put("date", LocalDate.now().toString());
        suggestion.put("title", title);
        suggestion.put("body", body);
        suggestion.put("nickname", nickname);
        suggestion.put("replies", new ArrayList<Map<String, Object>>());
        List<Map<String, Object>> suggestions = readSuggestions();
        suggestions.add(0, suggestion);
        writeSuggestions(suggestions);
        return suggestion;
    }

    @PostMapping("/{id}/replies")
    @SuppressWarnings("unchecked")
    public synchronized Map<String, Object> reply(@PathVariable String id, @RequestBody Map<String, String> request) throws IOException {
        String nickname = request.getOrDefault("nickname", "").trim();
        String body = request.getOrDefault("body", "").trim();
        if (!OWNER_NICKNAME.equals(nickname)) throw new IllegalArgumentException("Only the owner can reply.");
        if (body.isBlank()) throw new IllegalArgumentException("Reply body is required.");

        List<Map<String, Object>> suggestions = readSuggestions();
        for (Map<String, Object> suggestion : suggestions) {
            if (!id.equals(String.valueOf(suggestion.get("id")))) continue;
            Map<String, Object> reply = new LinkedHashMap<>();
            reply.put("id", UUID.randomUUID().toString());
            reply.put("date", LocalDate.now().toString());
            reply.put("body", body);
            reply.put("nickname", nickname);
            List<Map<String, Object>> replies = (List<Map<String, Object>>) suggestion.computeIfAbsent("replies", ignored -> new ArrayList<Map<String, Object>>());
            replies.add(reply);
            writeSuggestions(suggestions);
            return reply;
        }
        throw new IllegalArgumentException("Suggestion not found.");
    }

    @DeleteMapping("/{id}")
    public synchronized void remove(@PathVariable String id, @RequestParam String nickname) throws IOException {
        List<Map<String, Object>> suggestions = readSuggestions();
        boolean removed = suggestions.removeIf(suggestion -> id.equals(String.valueOf(suggestion.get("id"))) && (OWNER_NICKNAME.equals(nickname) || nickname.equals(String.valueOf(suggestion.get("nickname")))));
        if (!removed) throw new IllegalArgumentException("Suggestion cannot be deleted.");
        writeSuggestions(suggestions);
    }

    @DeleteMapping("/{suggestionId}/replies/{replyId}")
    @SuppressWarnings("unchecked")
    public synchronized void removeReply(@PathVariable String suggestionId, @PathVariable String replyId, @RequestParam String nickname) throws IOException {
        if (!OWNER_NICKNAME.equals(nickname)) throw new IllegalArgumentException("Only the owner can delete replies.");
        List<Map<String, Object>> suggestions = readSuggestions();
        for (Map<String, Object> suggestion : suggestions) {
            if (!suggestionId.equals(String.valueOf(suggestion.get("id")))) continue;
            List<Map<String, Object>> replies = (List<Map<String, Object>>) suggestion.getOrDefault("replies", new ArrayList<>());
            if (!replies.removeIf(reply -> replyId.equals(String.valueOf(reply.get("id"))))) throw new IllegalArgumentException("Reply not found.");
            writeSuggestions(suggestions);
            return;
        }
        throw new IllegalArgumentException("Suggestion not found.");
    }

    private List<Map<String, Object>> readSuggestions() throws IOException {
        if (!Files.exists(suggestionFile)) return new ArrayList<>();
        List<Map<String, Object>> suggestions = objectMapper.readValue(suggestionFile.toFile(), new TypeReference<>() { });
        return suggestions == null ? new ArrayList<>() : new ArrayList<>(suggestions);
    }

    private void writeSuggestions(List<Map<String, Object>> suggestions) throws IOException {
        Files.createDirectories(suggestionFile.getParent());
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(suggestionFile.toFile(), suggestions);
    }
}
