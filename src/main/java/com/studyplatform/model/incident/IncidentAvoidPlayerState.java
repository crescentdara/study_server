package com.studyplatform.model.incident;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class IncidentAvoidPlayerState {
    private double x = 180;
    private int score = 0;
    private long survivedMs = 0;
    private boolean running = true;
    private boolean gameOver = false;
    private List<List<Double>> incidents = new ArrayList<>();
    private long updatedAt = System.currentTimeMillis();
}
