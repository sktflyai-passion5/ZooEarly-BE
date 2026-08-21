package com.zooearly.ai;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * AI API 명세서 v1.0 — 4개 엔드포인트.
 * 앱 → 게이트웨이: /api/v1/ai/*  →  FastAPI: /ai/*  (§0.3 경로 미러링)
 *
 * 성공 응답 body는 FastAPI가 만든 §1.2 포맷 그대로다. 게이트웨이는 가공하지 않는다.
 */
@RestController
@RequestMapping("/api/v1/ai")
public class AiController {

    private final AiRelayService relayService;

    public AiController(AiRelayService relayService) {
        this.relayService = relayService;
    }

    /** §2 음성 대화 — STT → LLM → TTS 통합 파이프라인 */
    @PostMapping(value = "/chat", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<String> chat(
            @RequestPart("audio") MultipartFile audio,
            @RequestParam("scenario") String scenario,
            @RequestParam("history") String history,
            @RequestParam(value = "nativeLanguage", required = false) String nativeLanguage,
            @RequestParam("nickname") String nickname) {
        String body = relayService.chat(audio, scenario, history, nativeLanguage, nickname);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Audio-Retention", "none")   // 명세 §2 계약 3 — 오디오 미저장 보증
                .body(body);
    }

    /** §3 음성 → 텍스트 */
    @PostMapping(value = "/stt", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<String> stt(
            @RequestPart("audio") MultipartFile audio,
            @RequestParam(value = "language", required = false) String language) {
        return json(relayService.stt(audio, language));
    }

    /** §4 텍스트 → 음성 */
    @PostMapping(value = "/tts", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> tts(@RequestBody String rawBody) {
        return json(relayService.tts(rawBody));
    }

    /** §5 발화 피드백 생성 */
    @PostMapping(value = "/feedback", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> feedback(@RequestBody String rawBody) {
        return json(relayService.feedback(rawBody));
    }

    private ResponseEntity<String> json(String body) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(body);
    }
}
