package com.studyplatform.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class LunchVoteService {
    private static final ZoneId KOREA = ZoneId.of("Asia/Seoul");
    private final ObjectMapper objectMapper;
    private final Path path;
    private LunchStore store;

    public LunchVoteService(ObjectMapper objectMapper, @Value("${lunch.vote.path:data/lunch-votes.json}") String path) {
        this.objectMapper = objectMapper;
        this.path = Path.of(path).toAbsolutePath().normalize();
        this.store = load();
    }

    public synchronized Map<String, Object> today(String nickname) { return view(current()); }

    public synchronized Map<String, Object> addMenu(String nickname, String menu) {
        String name = clean(nickname);
        String meal = clean(menu);
        if (name.isBlank()) throw new IllegalArgumentException("Nickname is required.");
        if (meal.isBlank() || meal.length() > 100) throw new IllegalArgumentException("Menu must be between 1 and 100 characters.");
        DayRecord day = current();
        if (day.menus.stream().anyMatch(item -> key(item.nickname).equals(key(name)))) {
            throw new IllegalArgumentException("You have already registered a menu today.");
        }
        MenuRecord item = new MenuRecord();
        item.id = UUID.randomUUID().toString();
        item.nickname = name;
        item.menu = meal;
        day.menus.add(item);
        persist();
        return view(day);
    }

    private DayRecord current() {
        String date = LocalDate.now(KOREA).toString();
        DayRecord record = store.days.computeIfAbsent(date, ignored -> { DayRecord fresh = new DayRecord(); fresh.date = date; return fresh; });
        if (record.menus == null) record.menus = new ArrayList<>();
        return record;
    }

    private Map<String, Object> view(DayRecord day) {
        List<Map<String, Object>> menus = day.menus.stream().map(item -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", item.id);
            row.put("menu", item.menu);
            row.put("nickname", item.nickname);
            return row;
        }).toList();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("date", day.date);
        result.put("menus", menus);
        return result;
    }

    private LunchStore load() {
        if (!Files.exists(path)) return new LunchStore();
        try {
            LunchStore loaded = objectMapper.readValue(path.toFile(), LunchStore.class);
            return loaded == null || loaded.days == null ? new LunchStore() : loaded;
        } catch (IOException ignored) {
            return new LunchStore();
        }
    }

    private void persist() {
        try {
            Path parent = path.getParent();
            if (parent != null) Files.createDirectories(parent);
            Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(temporary.toFile(), store);
            try {
                Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to save lunch menus.", exception);
        }
    }

    private String clean(String value) { return value == null ? "" : value.trim(); }
    private String key(String value) { return clean(value).toLowerCase(Locale.ROOT); }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class LunchStore { public Map<String, DayRecord> days = new LinkedHashMap<>(); }
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class DayRecord { public String date = ""; public List<MenuRecord> menus = new ArrayList<>(); }
    public static class MenuRecord { public String id = ""; public String nickname = ""; public String menu = ""; }
}
