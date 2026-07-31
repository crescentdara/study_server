package com.studyplatform.model.davinci;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class DaVinciGameTest {

    @Test
    void sortsByNumberAndBlackBeforeWhiteAndAllowsJokerAnywhere() {
        DaVinciGame sorted = new DaVinciGame(2, deck(12, 0, 24, 5, 13, 1, 25, 6));
        assertThat(sorted.getPlayerTiles().get(0)).containsExactly(0, 12, 5, 24);
        assertThat(sorted.getPlayerTiles().get(1)).containsExactly(1, 13, 6, 25);

        DaVinciGame joker = new DaVinciGame(2, deck(0, 1, 2, 3, 12, 13, 14, 15, 24));
        assertThat(joker.drawTile(0)).isNull();
        assertThat(joker.getPendingTileId()).isEqualTo(24);
        assertThat(joker.placeTile(0, 0)).isNull();
        assertThat(joker.getPlayerTiles().get(0).get(0)).isEqualTo(24);
    }

    @Test
    void supportsConsecutiveGuessesPassWrongRevealEliminationAndWin() {
        DaVinciGame game = new DaVinciGame(2, deck(
                0, 1, 2, 3,
                12, 13, 14, 15,
                4, 16, 5
        ));

        assertThat(game.drawTile(0)).isNull();
        assertThat(game.placeTile(0, 4)).isNull();
        assertThat(game.guess(0, 1, 0, 0)).isNull();
        assertThat(game.guess(0, 1, 1, 1)).isNull();
        assertThat(game.getCorrectGuessesThisTurn()).isEqualTo(2);
        assertThat(game.pass(0)).isNull();
        assertThat(game.getCurrentTurn()).isEqualTo(1);

        assertThat(game.drawTile(1)).isNull();
        assertThat(game.placeTile(1, 4)).isNull();
        assertThat(game.guess(1, 0, 0, 11)).isEqualTo("WRONG");
        assertThat(game.getCurrentTurn()).isEqualTo(0);
        assertThat(game.getRevealed().get(1).get(4)).isTrue();

        assertThat(game.drawTile(0)).isNull();
        assertThat(game.placeTile(0, 5)).isNull();
        assertThat(game.guess(0, 1, 2, 2)).isNull();
        assertThat(game.guess(0, 1, 3, 3)).isNull();
        assertThat(game.isEliminated(1)).isTrue();
        assertThat(game.getWinner()).isEqualTo(0);
        assertThat(game.isFinisherPending()).isTrue();
        assertThat(game.getLastEliminatorPlayer()).isEqualTo(0);
        assertThat(game.getLastEliminatedPlayer()).isEqualTo(1);
        assertThat(game.executeFinisher(1, "TRASH", 1)).isEqualTo("Only the eliminator can choose the finisher");
        assertThat(game.executeFinisher(0, "TRASH", 1)).isNull();
        assertThat(game.isFinisherPending()).isFalse();
        assertThat(game.getExecutionStyle()).isEqualTo("TRASH");
        assertThat(game.getExecutionTaunt()).isEqualTo("삭제 완료. 복구할 가치 없음.");
        assertThat(game.getExecutionEventId()).isEqualTo(1);
    }

    private static List<Integer> deck(int... prefix) {
        List<Integer> result = new ArrayList<>();
        Set<Integer> used = new HashSet<>();
        for (int id : prefix) {
            if (used.add(id)) result.add(id);
        }
        for (int id = 0; id < 26; id++) {
            if (used.add(id)) result.add(id);
        }
        return result;
    }
}
