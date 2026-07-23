package com.studyplatform.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TetrisRecordServiceTest {
    @TempDir
    Path tempDir;

    @Test
    void completedMultiplayerMatchPersistsOverallAndHeadToHeadRecords() {
        Path recordPath = tempDir.resolve("records.json");
        TetrisRecordService service = new TetrisRecordService(new ObjectMapper(), recordPath);

        assertThat(service.recordCompletedMatch(
                "match-1",
                List.of("철수", "영희", "민수")
        )).isTrue();

        TetrisRecordService reloaded = new TetrisRecordService(new ObjectMapper(), recordPath);
        Map<String, Map<String, Object>> records = reloaded.recordsFor(List.of("철수", "영희", "민수"));

        assertThat(records.get("철수"))
                .containsEntry("matches", 1)
                .containsEntry("wins", 1)
                .containsEntry("losses", 0);
        assertThat(records.get("영희"))
                .containsEntry("matches", 1)
                .containsEntry("wins", 0)
                .containsEntry("losses", 1);
        assertThat(records.get("민수"))
                .containsEntry("matches", 1)
                .containsEntry("wins", 0)
                .containsEntry("losses", 1);

        @SuppressWarnings("unchecked")
        Map<String, Object> ironOpponents = (Map<String, Object>) records.get("철수").get("opponents");
        assertThat(ironOpponents).containsKeys("영희", "민수");
        @SuppressWarnings("unchecked")
        Map<String, Object> youngheeOpponents = (Map<String, Object>) records.get("영희").get("opponents");
        assertThat(youngheeOpponents.get("민수")).isEqualTo(Map.of("wins", 1, "losses", 0));
    }

    @Test
    void duplicateMatchIdIsNotRecordedTwice() {
        TetrisRecordService service = new TetrisRecordService(
                new ObjectMapper(),
                tempDir.resolve("records.json")
        );

        assertThat(service.recordCompletedMatch("same-match", List.of("A", "B"))).isTrue();
        assertThat(service.recordCompletedMatch("same-match", List.of("A", "B"))).isFalse();

        assertThat(service.recordsFor(List.of("A")).get("A"))
                .containsEntry("matches", 1)
                .containsEntry("wins", 1);
    }
}
