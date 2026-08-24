package com.zooearly.common.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.zooearly.ai.client.InferenceClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * CORS는 브라우저에서 앱을 띄울 때(Expo web)만 필요하다.
 *
 * 설정을 안 하면 preflight가 403으로 끊겨 웹에서 모든 호출이 실패하고,
 * 반대로 기본값으로 열어두면 배포 환경까지 열린다. 양쪽 다 확인해둔다.
 */
class CorsConfigTest {

    private static final String ORIGIN = "http://localhost:8081";

    @Nested
    @SpringBootTest(properties = "cors.allowed-origins=" + ORIGIN)
    @AutoConfigureMockMvc
    @DisplayName("오리진을 설정했을 때")
    class WhenConfigured {

        @Autowired
        MockMvc mockMvc;

        @MockitoBean
        InferenceClient inferenceClient;

        @Test
        @DisplayName("preflight를 통과시키고 허용 오리진을 돌려준다")
        void allowsPreflight() throws Exception {
            mockMvc.perform(options("/api/v1/ai/tts")
                            .header("Origin", ORIGIN)
                            .header("Access-Control-Request-Method", "POST")
                            .header("Access-Control-Request-Headers", "content-type"))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Access-Control-Allow-Origin", ORIGIN));
        }

        @Test
        @DisplayName("설정하지 않은 오리진은 막는다")
        void rejectsUnknownOrigin() throws Exception {
            mockMvc.perform(options("/api/v1/ai/tts")
                            .header("Origin", "http://evil.example")
                            .header("Access-Control-Request-Method", "POST"))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    @SpringBootTest
    @AutoConfigureMockMvc
    @DisplayName("기본값(비어 있음)일 때")
    class WhenNotConfigured {

        @Autowired
        MockMvc mockMvc;

        @MockitoBean
        InferenceClient inferenceClient;

        @Test
        @DisplayName("CORS를 아예 켜지 않는다 — 배포 기본값")
        void corsDisabledByDefault() throws Exception {
            mockMvc.perform(options("/api/v1/ai/tts")
                            .header("Origin", ORIGIN)
                            .header("Access-Control-Request-Method", "POST"))
                    .andExpect(status().isForbidden());
        }
    }
}
