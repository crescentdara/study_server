package com.studyplatform.service;

import com.studyplatform.dto.request.LobbyVendingRequest;
import com.studyplatform.dto.response.LobbyVendingEvent;
import com.studyplatform.dto.response.LobbyVendingMessage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LobbyVendingServiceTest {
    @Test
    void createsValidatedSharedEvent() {
        LobbyVendingService service = new LobbyVendingService();
        LobbyVendingRequest request = new LobbyVendingRequest();
        request.setSessionId("s1");
        request.setNickname("tester");
        request.setDrink("mix_coffee");

        request.setType("DISPENSE");
        request.setX(.4);
        request.setY(.6);

        LobbyVendingMessage message = service.apply(request);
        assertNotNull(message);
        LobbyVendingEvent event = message.getCup();
        assertEquals("MIX_COFFEE", event.getDrink());
        assertEquals("tester", event.getNickname());
        assertFalse(event.getEventId().isBlank());
        assertEquals(.4, event.getX());
    }

    @Test
    void rejectsUnknownDrink() {
        LobbyVendingRequest request = new LobbyVendingRequest();
        request.setDrink("SODA");
        request.setType("DISPENSE");
        assertNull(new LobbyVendingService().apply(request));
    }

    @Test
    void snapshotsMovesAndRemovesSharedCup() {
        LobbyVendingService service = new LobbyVendingService();
        LobbyVendingRequest dispense = new LobbyVendingRequest();
        dispense.setType("DISPENSE");
        dispense.setEventId("cup-1");
        dispense.setDrink("MILK");
        dispense.setX(.3);
        dispense.setY(.5);
        assertNotNull(service.apply(dispense));

        LobbyVendingRequest enter = new LobbyVendingRequest();
        enter.setType("ENTER");
        assertEquals(1, service.apply(enter).getCups().size());

        LobbyVendingRequest move = new LobbyVendingRequest();
        move.setType("MOVE");
        move.setEventId("cup-1");
        move.setX(.7);
        move.setY(.8);
        assertEquals(.7, service.apply(move).getCup().getX());

        LobbyVendingRequest remove = new LobbyVendingRequest();
        remove.setType("REMOVE");
        remove.setEventId("cup-1");
        assertEquals("REMOVE", service.apply(remove).getType());
        assertEquals(0, service.size());
    }
}
