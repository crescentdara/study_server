package com.studyplatform.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LobbyCigaretteState {
    private String sessionId;
    private String nickname;
    private double x;
    private double y;
    private double burn;
    private boolean lit;
    private boolean holding;
    private String actionId;
    private long updatedAt;
}
