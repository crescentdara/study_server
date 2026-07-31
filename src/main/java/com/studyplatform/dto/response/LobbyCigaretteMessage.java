package com.studyplatform.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class LobbyCigaretteMessage {
    private String type;
    private String sessionId;
    private LobbyCigaretteState cigarette;
    private List<LobbyCigaretteState> cigarettes;

    public static LobbyCigaretteMessage snapshot(List<LobbyCigaretteState> cigarettes) {
        return new LobbyCigaretteMessage("SNAPSHOT", null, null, cigarettes);
    }

    public static LobbyCigaretteMessage upsert(LobbyCigaretteState cigarette) {
        return new LobbyCigaretteMessage("UPSERT", cigarette.getSessionId(), cigarette, null);
    }

    public static LobbyCigaretteMessage remove(String sessionId) {
        return new LobbyCigaretteMessage("REMOVE", sessionId, null, null);
    }
}
