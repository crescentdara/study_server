package com.studyplatform.service;

import com.studyplatform.dto.response.ChatMessage;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ChatHistoryServiceTest {
    @Test
    void clearRemovesOldMessagesAndRejectsMessagesAllocatedBeforeClear() {
        var service = new ChatHistoryService();
        var old = new ChatMessage();
        old.setId(service.nextId());
        assertThat(service.addLobby(old)).isTrue();
        var inFlight = new ChatMessage();
        inFlight.setId(service.nextId());
        long clearedThrough = service.clearLobby();
        assertThat(clearedThrough).isEqualTo(inFlight.getId());
        assertThat(service.lobbyState().messages()).isEmpty();
        assertThat(service.addLobby(inFlight)).isFalse();
        var after = new ChatMessage();
        after.setId(service.nextId());
        assertThat(service.addLobby(after)).isTrue();
        assertThat(service.lobbyState().messages()).containsExactly(after);
        assertThat(service.lobbyState().clearedThrough()).isEqualTo(clearedThrough);
    }
}
