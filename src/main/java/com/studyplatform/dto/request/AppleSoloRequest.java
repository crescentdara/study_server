package com.studyplatform.dto.request;

import lombok.Data;

/**
 * 사과게임 혼자 하기 요청
 *
 * start는 nickname만, clear는 instanceId와 사각 범위(r1,c1~r2,c2), finish는
 * instanceId만, pause는 instanceId와 paused만 쓴다. 한 판이 곧 세션이므로
 * 방 정보는 필요하지 않다.
 */
@Data
public class AppleSoloRequest {
    private String nickname;
    private String instanceId;
    private int r1;
    private int c1;
    private int r2;
    private int c2;
    private boolean paused;
    private String mode;
}
