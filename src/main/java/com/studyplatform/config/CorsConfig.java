package com.studyplatform.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

/**
 * CORS(Cross-Origin Resource Sharing) 설정
 *
 * ─── CORS가 필요한 이유 ────────────────────────────────────────────────────────
 * 브라우저는 보안상 "동일 출처 정책(Same-Origin Policy)"을 적용합니다.
 * 출처(Origin) = 프로토콜 + 도메인 + 포트 (셋 중 하나라도 다르면 다른 출처)
 *
 * 개발 환경에서:
 *   - 프론트엔드: http://localhost:3000  ← Vite 개발 서버
 *   - 백엔드:     http://localhost:8080  ← Spring Boot
 *
 * 포트가 다르므로(3000 ≠ 8080) 다른 출처로 판단 → 브라우저가 요청을 막음!
 * 서버에서 명시적으로 허용해줘야 합니다 → 이게 CORS 설정
 *
 * ─── CorsFilter vs WebMvcConfigurer.addCorsMappings ──────────────────────────
 * 두 방식 모두 CORS를 설정하지만, WebSocket을 사용할 때는 Filter 방식이 더 안전합니다.
 * (Filter가 요청 처리 더 앞 단계에서 동작하기 때문)
 */
// @Configuration: 이 클래스가 Spring 설정 클래스임을 나타냅니다.
// 내부에 @Bean 메서드를 선언하면 Spring이 애플리케이션 시작 시 자동으로 빈으로 등록합니다.
@Configuration
public class CorsConfig {

    /**
     * @Bean: 이 메서드의 반환값을 Spring 빈으로 등록합니다.
     * Spring Boot가 자동으로 이 필터를 HTTP 요청 처리 체인에 추가합니다.
     *
     * ─── CorsFilter 동작 흐름 ─────────────────────────────────────────────────
     * 브라우저 → [CorsFilter가 CORS 헤더 검사/추가] → Spring MVC 컨트롤러
     * 필터 단계에서 처리하므로 Spring Security 등 다른 설정보다 먼저 적용됩니다.
     */
    @Bean
    public CorsFilter corsFilter() {
        // CorsConfiguration: 어떤 출처/헤더/메서드를 허용할지 규칙을 담는 객체
        CorsConfiguration config = new CorsConfiguration();

        // 쿠키(세션) 및 인증 정보(Authorization 헤더)를 요청에 포함할 수 있도록 허용
        // false로 설정하면 로그인 세션을 유지하는 요청이 차단됨
        config.setAllowCredentials(true);

        // 모든 출처 허용 (개발용, 운영에서는 특정 도메인만)
        // addAllowedOrigin("*") 대신 addAllowedOriginPattern("*")을 쓰는 이유:
        // setAllowCredentials(true)와 "*"를 함께 쓰면 보안 정책 충돌이 발생하기 때문에
        // 와일드카드 패턴 방식(OriginPattern)을 사용해야 합니다.
        config.addAllowedOriginPattern("*");

        // Content-Type, Authorization 등 클라이언트가 보내는 모든 HTTP 헤더 허용
        config.addAllowedHeader("*");

        // GET, POST, PUT, DELETE 등 모든 HTTP 메서드 허용
        config.addAllowedMethod("*");

        // UrlBasedCorsConfigurationSource: URL 경로 패턴별로 CORS 설정을 매핑하는 객체
        // 예: "/api/**"에만 적용하거나, "/**"로 전체 경로에 적용할 수 있음
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();

        // "/**": 애플리케이션의 모든 경로에 위 CORS 설정을 적용
        source.registerCorsConfiguration("/**", config);

        // 설정이 적용된 CorsFilter를 반환 → Spring이 빈으로 관리하며 자동으로 필터 체인에 등록
        return new CorsFilter(source);
    }
}
