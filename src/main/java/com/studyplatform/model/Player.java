package com.studyplatform.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 플레이어 정보 모델
 *
 * ─── Lombok 어노테이션 설명 ───────────────────────────────────────────────────
 * @Data          : @Getter + @Setter + @ToString + @EqualsAndHashCode 를 한번에 적용
 * @NoArgsConstructor : 매개변수 없는 기본 생성자 자동 생성 (JSON 역직렬화에 필요)
 * @AllArgsConstructor: 모든 필드를 매개변수로 받는 생성자 자동 생성
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Player {
    /**
     * 플레이어를 구분하는 고유 ID
     * 클라이언트가 sessionStorage에 저장한 UUID를 사용합니다.
     * (실제 서비스에서는 Spring Security + JWT로 인증하는 것이 일반적)
     */
    private String sessionId;

    /** 화면에 표시될 닉네임 */
    private String nickname;

    /**
     * 방에서의 플레이어 순서 (0: 방장/먼저 입장, 1: 게스트/나중에 입장)
     * 이 인덱스로 게임 데이터 배열에 접근합니다.
     */
    private int playerIndex;
}
