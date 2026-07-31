package com.studyplatform.dto.request;

import lombok.Data;

@Data
public class LobbyCigaretteRequest {
    private String type;
    private String sessionId;
    private String nickname;
    private double x;
    private double y;
    private double burn;
    private boolean lit;
    private boolean holding;
    private String actionId;
}
