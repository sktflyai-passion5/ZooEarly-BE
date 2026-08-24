package com.zooearly.common.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 요청 한 건을 한 줄로 남긴다.
 *
 * 스프링은 정상 요청을 기본으로 로그에 안 남긴다. 그래서 앱을 붙여놓고도
 * "지금 실제로 통신이 되고 있나"를 서버 쪽에서 확인할 방법이 없었다.
 * 연동 중에 이게 안 보이면 앱 문제인지 서버 문제인지 가릴 수가 없다.
 *
 * 남기는 것: 메서드, 경로, 상태코드, 소요시간, 호출한 IP.
 * 남기지 않는 것: 본문. 아이 음성과 발화 내용이 로그에 쌓이면 안 된다 —
 * "오디오 원본을 저장하지 않는다"는 설계와 같은 이유다.
 */
@Component
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger("com.zooearly.access");

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        long startedAt = System.currentTimeMillis();
        try {
            chain.doFilter(request, response);
        } finally {
            long tookMs = System.currentTimeMillis() - startedAt;
            int status = response.getStatus();
            String line = "{} {} → {} ({}ms) from {}";
            Object[] args = {
                request.getMethod(), path(request), status, tookMs, clientIp(request)
            };
            // 실패는 눈에 띄어야 한다 — 연동 중 확인해야 할 건 대부분 이쪽이다
            if (status >= 400) {
                log.warn(line, args);
            } else {
                log.info(line, args);
            }
        }
    }

    private String path(HttpServletRequest request) {
        String query = request.getQueryString();
        return query == null ? request.getRequestURI() : request.getRequestURI() + "?" + query;
    }

    /**
     * 프록시(Container Apps ingress) 뒤에 있으면 remoteAddr이 프록시 IP다.
     * 어느 기기에서 붙었는지 봐야 연동 문제를 가릴 수 있어 원본 IP를 먼저 본다.
     */
    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
