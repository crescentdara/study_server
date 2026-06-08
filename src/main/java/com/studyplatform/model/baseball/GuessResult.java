package com.studyplatform.model.baseball;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 숫자야구 한 번의 추측 결과
 *
 * 이 객체는 클라이언트에게 JSON으로 전송되어
 * 추측 히스토리 목록으로 화면에 표시됩니다.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GuessResult {
    private String guess;   // 추측한 3자리 숫자 (예: "152")
    private int strikes;    // 스트라이크 수 (자리 + 숫자 모두 일치)
    private int balls;      // 볼 수 (숫자는 있지만 자리가 다름)

    /**
     * 결과를 "3S0B" 형태의 문자열로 반환
     * 예: strikes=1, balls=2 → "1S2B"
     */
    public String getSummary() {
        return strikes + "S" + balls + "B";
    }
}
