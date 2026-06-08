package com.studyplatform;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 게임 플랫폼 메인 애플리케이션 진입점
 *
 * @SpringBootApplication 은 아래 세 어노테이션을 합친 것:
 *   - @Configuration     : 이 클래스가 Spring 빈 설정 클래스임을 표시
 *   - @EnableAutoConfiguration : 클래스패스 기반으로 필요한 빈 자동 등록
 *                               (예: WebSocket 의존성이 있으면 자동으로 WebSocket 관련 설정 활성화)
 *   - @ComponentScan     : com.studyplatform 하위 패키지의 @Component, @Service,
 *                          @Controller 등을 자동으로 스캔해서 빈으로 등록
 */
@SpringBootApplication
public class StudyPlatformApplication {

    public static void main(String[] args) {
        // SpringApplication.run(): Spring 컨텍스트 초기화 + 내장 Tomcat 서버 시작
        SpringApplication.run(StudyPlatformApplication.class, args);
    }
}
