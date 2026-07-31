package com.studyplatform.service;

import com.studyplatform.dto.request.LobbyCigaretteRequest;
import com.studyplatform.dto.response.LobbyCigaretteMessage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LobbyCigaretteServiceTest {
    @Test
    void spawnsClampsSnapshotsAndRemoves() {
        LobbyCigaretteService service = new LobbyCigaretteService();
        LobbyCigaretteRequest spawn = request("SPAWN", "s1");
        spawn.setNickname("tester");
        spawn.setX(4);
        spawn.setY(-1);
        spawn.setLit(true);

        LobbyCigaretteMessage added = service.apply(spawn);
        assertEquals("UPSERT", added.getType());
        assertEquals(0.96, added.getCigarette().getX());
        assertEquals(0.08, added.getCigarette().getY());

        LobbyCigaretteMessage snapshot = service.apply(request("ENTER", ""));
        assertEquals(1, snapshot.getCigarettes().size());

        LobbyCigaretteRequest puff = request("PUFF", "s1");
        puff.setActionId("puff-75-s1-1");
        assertEquals("puff-75-s1-1", service.apply(puff).getCigarette().getActionId());

        assertEquals("REMOVE", service.apply(request("REMOVE", "s1")).getType());
        assertEquals(0, service.size());
    }

    @Test
    void limitsLobbyAndIgnoresUnknownEvents() {
        LobbyCigaretteService service = new LobbyCigaretteService();
        for (int i = 0; i < LobbyCigaretteService.MAX_CIGARETTES; i++) {
            assertNotNull(service.apply(request("SPAWN", "s" + i)));
        }
        assertNull(service.apply(request("SPAWN", "overflow")));
        assertNull(service.apply(request("NOPE", "s0")));
    }

    private static LobbyCigaretteRequest request(String type, String sessionId) {
        LobbyCigaretteRequest request = new LobbyCigaretteRequest();
        request.setType(type);
        request.setSessionId(sessionId);
        request.setX(.5);
        request.setY(.5);
        return request;
    }
}
