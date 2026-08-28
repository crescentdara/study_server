package com.studyplatform.service;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.Comparator;
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

    public synchronized Map<String, Object> today(String nickname) { return view(current(), nickname); }

    public synchronized Map<String, Object> addMenu(String nickname, String menu) {
        String name = clean(nickname); String meal = clean(menu);
        if (name.isBlank()) throw new IllegalArgumentException("닉네임을 먼저 입력하세요.");
        if (meal.isBlank() || meal.length() > 100) throw new IllegalArgumentException("메뉴는 1~100자로 입력하세요.");
        DayRecord day = current();
        if (day.menus.stream().anyMatch(item -> key(item.nickname).equals(key(name)))) {
            throw new IllegalArgumentException("오늘은 메뉴를 이미 등록했습니다.");
        }
        MenuRecord item = new MenuRecord(); item.id = UUID.randomUUID().toString(); item.nickname = name; item.menu = meal;
        day.menus.add(item); persist(); return view(day, name);
    }

    public synchronized Map<String, Object> vote(String nickname, String menuId) {
        String name = clean(nickname); String id = clean(menuId);
        if (name.isBlank()) throw new IllegalArgumentException("닉네임을 먼저 입력하세요.");
        DayRecord day = current();
        MenuRecord target = day.menus.stream().filter(item -> item.id.equals(id)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("메뉴를 찾을 수 없습니다."));
        if (day.menus.size() < 3) throw new IllegalArgumentException("서로 다른 3명이 메뉴를 등록한 뒤 투표할 수 있습니다.");
        if (key(target.nickname).equals(key(name))) throw new IllegalArgumentException("내가 등록한 메뉴에는 투표할 수 없습니다.");
        if (day.votes.containsKey(key(name))) throw new IllegalArgumentException("오늘은 이미 투표했습니다.");
        day.votes.put(key(name), id); persist(); return view(day, name);
    }

    private DayRecord current() {
        String date = LocalDate.now(KOREA).toString();
        DayRecord record = store.days.computeIfAbsent(date, ignored -> { DayRecord fresh = new DayRecord(); fresh.date = date; return fresh; });
        if (record.menus == null) record.menus = new ArrayList<>();
        if (record.votes == null) record.votes = new LinkedHashMap<>();
        return record;
    }

    private Map<String, Object> view(DayRecord day, String nickname) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String vote : day.votes.values()) counts.merge(vote, 1, Integer::sum);
        int top = counts.values().stream().max(Integer::compareTo).orElse(0);
        List<Map<String, Object>> menus = day.menus.stream().sorted(Comparator.comparing((MenuRecord item) -> counts.getOrDefault(item.id, 0)).reversed())
                .map(item -> { Map<String, Object> row = new LinkedHashMap<>(); row.put("id", item.id); row.put("menu", item.menu); row.put("nickname", item.nickname); row.put("votes", counts.getOrDefault(item.id, 0)); row.put("winner", top > 0 && counts.getOrDefault(item.id, 0) == top); return row; }).toList();
        boolean isWinner = top > 0 && day.menus.stream().anyMatch(item -> key(item.nickname).equals(key(nickname)) && counts.getOrDefault(item.id, 0) == top);
        Map<String, Object> result = new LinkedHashMap<>(); result.put("date", day.date); result.put("menus", menus); result.put("voterCount", day.votes.size()); result.put("myVoteMenuId", day.votes.get(key(nickname))); result.put("isWinner", isWinner); return result;
    }

    public synchronized boolean isTodayWinner(String nickname) {
        String name = clean(nickname);
        if (name.isBlank()) return false;
        DayRecord day = current();
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String vote : day.votes.values()) counts.merge(vote, 1, Integer::sum);
        int top = counts.values().stream().max(Integer::compareTo).orElse(0);
        return top > 0 && day.menus.stream().anyMatch(item -> key(item.nickname).equals(key(name)) && counts.getOrDefault(item.id, 0) == top);
    }

    private LunchStore load() {
        if (!Files.exists(path)) return new LunchStore();
        try { LunchStore loaded = objectMapper.readValue(path.toFile(), LunchStore.class); return loaded == null || loaded.days == null ? new LunchStore() : loaded; }
        catch (IOException ignored) { return new LunchStore(); }
    }
    private void persist() {
        try { Path parent = path.getParent(); if (parent != null) Files.createDirectories(parent); Path temporary = path.resolveSibling(path.getFileName() + ".tmp"); objectMapper.writerWithDefaultPrettyPrinter().writeValue(temporary.toFile(), store); try { Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); } catch (AtomicMoveNotSupportedException ignored) { Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING); } }
        catch (IOException exception) { throw new IllegalStateException("점심 투표를 저장할 수 없습니다.", exception); }
    }
    private String clean(String value) { return value == null ? "" : value.trim(); }
    private String key(String value) { return clean(value).toLowerCase(Locale.ROOT); }
    public static class LunchStore { public Map<String, DayRecord> days = new LinkedHashMap<>(); }
    public static class DayRecord { public String date = ""; public List<MenuRecord> menus = new ArrayList<>(); public Map<String, String> votes = new LinkedHashMap<>(); }
    public static class MenuRecord { public String id = ""; public String nickname = ""; public String menu = ""; }
}
