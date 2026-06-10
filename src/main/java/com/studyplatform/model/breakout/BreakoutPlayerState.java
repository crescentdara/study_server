package com.studyplatform.model.breakout;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class BreakoutPlayerState {
    private double paddleX = 210;
    private double ballX = 210;
    private double ballY = 360;
    private int score = 0;
    private int bricksLeft = 40;
    private boolean running = true;
    private boolean gameOver = false;
    private boolean cleared = false;
    private List<Integer> bricks = new ArrayList<>();
    private long updatedAt = System.currentTimeMillis();
}
