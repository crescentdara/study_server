package com.studyplatform.model.alkkagi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

class AlkkagiGameTest {

    @Test
    void twoAndThreePlayerGamesUseTheSameMapPool() {
        for (long seed = 0; seed < 100; seed++) {
            AlkkagiGame twoPlayerGame = new AlkkagiGame(2, seed);
            AlkkagiGame threePlayerGame = new AlkkagiGame(3, seed);

            assertEquals(twoPlayerGame.getMapType(), threePlayerGame.getMapType());
            assertFalse(threePlayerGame.getMapType().startsWith("HEX_"));
            assertEquals(18, threePlayerGame.getStones().size());
        }
    }

    @Test
    void thirdPlayerStartsInsideThePlayablePartOfTheNarrowBridge() {
        AlkkagiGame game = findThreePlayerGame("NARROW_BRIDGE");

        game.getStones().stream()
                .filter(stone -> stone.getOwner() == 2)
                .forEach(stone -> {
                    assertEquals(0.50, stone.getX(), 0.00001);
                    assertTrue(stone.getY() > 0.33 && stone.getY() < 0.67);
                });
    }

    @Test
    void funMapsAreAvailableToEveryPlayerCount() {
        for (String mapType : List.of("ICE_SAND", "ELASTIC_WALLS", "MAGNET_FIELD", "DONUT_RING", "OFFICE_DESK")) {
            assertEquals(mapType, findGame(2, mapType).getMapType());
            assertEquals(mapType, findGame(3, mapType).getMapType());
        }
    }

    @Test
    void everyPlayerGetsAtLeastTwoSpecialStones() {
        AlkkagiGame game = new AlkkagiGame(3, 42L);

        for (int owner = 0; owner < 3; owner++) {
            int player = owner;
            long specialCount = game.getStones().stream()
                    .filter(stone -> stone.getOwner() == player)
                    .filter(stone -> !"NORMAL".equals(stone.getType()))
                    .count();
            assertTrue(specialCount >= 2);
        }
    }

    @Test
    void donutCenterAcceptsAnOutStone() {
        AlkkagiGame game = findGame(2, "DONUT_RING");
        AlkkagiStone shotStone = game.getStones().stream()
                .filter(stone -> stone.getOwner() == 0)
                .findFirst()
                .orElseThrow();
        assertNull(game.beginShot(0, shotStone.getId(), 1.0, 0.0));

        List<AlkkagiStone> result = game.getStones().stream()
                .map(stone -> new AlkkagiStone(
                        stone.getId(), stone.getOwner(), stone.getX(), stone.getY(), stone.isActive(), stone.getType()))
                .collect(Collectors.toList());
        AlkkagiStone outStone = result.get(shotStone.getId());
        outStone.setX(0.50);
        outStone.setY(0.50);
        outStone.setActive(false);

        assertNull(game.applyShotResult(0, 1, result));
    }

    private AlkkagiGame findThreePlayerGame(String mapType) {
        return findGame(3, mapType);
    }

    private AlkkagiGame findGame(int players, String mapType) {
        for (long seed = 0; seed < 10_000; seed++) {
            AlkkagiGame game = new AlkkagiGame(players, seed);
            if (mapType.equals(game.getMapType())) return game;
        }
        throw new AssertionError("Could not generate map: " + mapType);
    }
}
