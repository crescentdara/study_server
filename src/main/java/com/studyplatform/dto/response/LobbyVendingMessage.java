package com.studyplatform.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class LobbyVendingMessage {
    private String type;
    private String eventId;
    private LobbyVendingEvent cup;
    private List<LobbyVendingEvent> cups;

    public static LobbyVendingMessage snapshot(List<LobbyVendingEvent> cups) {
        return new LobbyVendingMessage("SNAPSHOT", null, null, cups);
    }

    public static LobbyVendingMessage cup(String type, LobbyVendingEvent cup) {
        return new LobbyVendingMessage(type, cup.getEventId(), cup, null);
    }

    public static LobbyVendingMessage remove(String eventId) {
        return new LobbyVendingMessage("REMOVE", eventId, null, null);
    }
}
