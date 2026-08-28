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
        if (title.isBlank() || body.isBlank()) throw new IllegalArgumentException("Title and body are required.");

        Map<String, Object> suggestion = new LinkedHashMap<>();
        suggestion.put("id", UUID.randomUUID().toString());
        suggestion.put("date", LocalDate.now().toString());
        suggestion.put("title", title);
        suggestion.put("body", body);
        suggestion.put("status", "OPEN");
        List<Map<String, Object>> suggestions = readSuggestions();
        suggestions.add(0, suggestion);
        writeSuggestions(suggestions);
        return suggestion;
    }

    @PostMapping("/{id}/status")
    public synchronized Map<String, Object> updateStatus(@PathVariable String id, @RequestBody Map<String, String> request) throws IOException {
        requireOwner(request.get("nickname"));
        String status = request.getOrDefault("status", "").trim().toUpperCase();
        if (!"RESOLVED".equals(status) && !"DECLINED".equals(status)) throw new IllegalArgumentException("Unknown suggestion status.");

        List<Map<String, Object>> suggestions = readSuggestions();
        for (Map<String, Object> suggestion : suggestions) {
            if (!id.equals(String.valueOf(suggestion.get("id")))) continue;
            suggestion.put("status", status);
            suggestion.put("decisionDate", LocalDate.now().toString());
            writeSuggestions(suggestions);
            return suggestion;
        }
        throw new IllegalArgumentException("Suggestion not found.");
    }

    @DeleteMapping("/{id}")
    public synchronized void remove(@PathVariable String id, @RequestParam String nickname) throws IOException {
        requireOwner(nickname);
        List<Map<String, Object>> suggestions = readSuggestions();
        if (!suggestions.removeIf(suggestion -> id.equals(String.valueOf(suggestion.get("id"))))) throw new IllegalArgumentException("Suggestion not found.");
        writeSuggestions(suggestions);
    }

    private void requireOwner(String nickname) {
        if (!OWNER_NICKNAME.equals(nickname == null ? "" : nickname.trim())) throw new IllegalArgumentException("Only the owner can manage suggestions.");
    }

    private List<Map<String, Object>> readSuggestions() throws IOException {
        if (!Files.exists(suggestionFile)) return new ArrayList<>();
        List<Map<String, Object>> suggestions = objectMapper.readValue(suggestionFile.toFile(), new TypeReference<>() { });
        List<Map<String, Object>> result = suggestions == null ? new ArrayList<>() : new ArrayList<>(suggestions);
        boolean migrated = false;
        for (Map<String, Object> suggestion : result) {
            // Existing author and reply data is deliberately discarded to make the board anonymous.
            if (suggestion.remove("nickname") != null) migrated = true;
            if (suggestion.remove("replies") != null) migrated = true;
            if (!suggestion.containsKey("status")) { suggestion.put("status", "OPEN"); migrated = true; }
        }
        if (migrated) writeSuggestions(result);
        return result;
    }

    private void writeSuggestions(List<Map<String, Object>> suggestions) throws IOException {
        Files.createDirectories(suggestionFile.getParent());
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(suggestionFile.toFile(), suggestions);
    }
}
