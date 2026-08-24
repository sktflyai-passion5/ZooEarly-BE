package com.zooearly.common.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 브라우저에서 앱을 띄울 때(Expo web)만 필요한 설정이다.
 *
 * 실제 배포 대상인 React Native 네이티브 앱은 브라우저가 아니라서 CORS 자체가 없다.
 * 하지만 개발 중 `npm run web`으로 화면을 확인하려면 preflight(OPTIONS)가 통과해야 하는데,
 * 설정이 없으면 스프링이 403 "Invalid CORS request"로 끊어 모든 호출이 실패한다.
 *
 * 기본값은 비어 있고, 그때는 CORS를 아예 켜지 않는다 — 배포 환경에서 굳이 열어둘 이유가 없다.
 * 개발자가 필요할 때만 CORS_ALLOWED_ORIGINS로 오리진을 명시해서 켠다.
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
