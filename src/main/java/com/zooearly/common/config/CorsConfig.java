package com.zooearly.common.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 프론트가 브라우저에서 돌 때 필요한 설정이다.
 *
 * 앱이 Azure Static Website로 배포되면서 실서버에서도 켜야 하는 값이 됐다.
 * React Native 네이티브였다면 브라우저가 아니라서 CORS 자체가 없었을 것이다.
 * 설정이 없으면 스프링이 preflight(OPTIONS)를 403 "Invalid CORS request"로 끊어
 * 프론트의 모든 호출이 실패한다.
 *
 * 기본값은 비어 있고 그때는 CORS를 아예 켜지 않는다. 와일드카드(*)를 기본값으로
 * 두지 않는 이유이기도 하다 — 열 거면 누가 봐도 알게 오리진을 명시해서 연다.
 * 허용 오리진은 배포 환경마다 다르므로 코드에 박지 않고 환경변수로만 주입한다.
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    private final String[] allowedOrigins;

    public CorsConfig(@Value("${cors.allowed-origins:}") String origins) {
        this.allowedOrigins = origins.isBlank()
                ? new String[0]
                : origins.split("\\s*,\\s*");
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        // 오리진을 명시하지 않았으면 아무것도 등록하지 않는다.
        // 와일드카드(*)를 기본값으로 두지 않는 이유다 — 열어둘 거면 누가 봐도 알게 명시적으로 연다.
        if (allowedOrigins.length == 0) {
            return;
        }
        registry.addMapping("/api/v1/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods("GET", "POST", "OPTIONS")
                .allowedHeaders("*")
                .maxAge(3600);   // preflight 결과를 1시간 캐시해 왕복을 줄인다
    }
}
