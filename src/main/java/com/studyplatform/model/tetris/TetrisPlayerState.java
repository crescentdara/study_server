package com.studyplatform.model.tetris;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class TetrisPlayerState {
    private List<List<String>> board = new ArrayList<>();
    private int score = 0;
    private int lines = 0;
    private int cycle = 1;
    private boolean running = true;
    private boolean gameOver = false;
    private long updatedAt = System.currentTimeMillis();

    public TetrisPlayerState() {
        for (int rowIndex = 0; rowIndex < 20; rowIndex += 1) {
            List<String> row = new ArrayList<>(10);
            for (int colIndex = 0; colIndex < 10; colIndex += 1) {
                row.add("");
            }
            board.add(row);
        }
    }
}
