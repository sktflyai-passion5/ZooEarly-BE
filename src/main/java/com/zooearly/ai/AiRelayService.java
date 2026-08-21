package com.zooearly.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zooearly.ai.client.InferenceClient;
import com.zooearly.common.exception.BusinessException;
import com.zooearly.common.response.ErrorCode;
import java.io.IOException;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.multipart.MultipartFile;

/**
 * 게이트웨이의 전부 — 검증하고, 전달하고, 끝. (§0.1)
 * 비즈니스 로직·DB 저장·응답 가공을 여기에 추가하지 않는다.
 *
 * 오디오는 MultipartFile.getResource()로 스트림 그대로 넘긴다 — 명세 §8.
 * getBytes()를 쓰면 파일 전체가 힙에 복사돼 10MB 동시 요청에 취약해진다.
 */
@Service
public class AiRelayService {

    private static final Set<String> AUDIO_EXTENSIONS = Set.of("m4a", "wav", "webm");
    private static final Set<String> SCENARIOS = Set.of("ARRIVAL", "CLASS", "LUNCH", "DISMISSAL");
    private static final Set<String> LANGUAGES = Set.of("KOREAN", "CHINESE", "VIETNAMESE");
    private static final Set<String> VOICES = Set.of("TEACHER", "FRIEND");
    private static final long MAX_AUDIO_BYTES = 10L * 1024 * 1024;
    private static final int MAX_NICKNAME_LENGTH = 20;   // 명세 §2 / §5

    private final InferenceClient inferenceClient;
    private final ObjectMapper objectMapper;

    public AiRelayService(InferenceClient inferenceClient, ObjectMapper objectMapper) {
        this.inferenceClient = inferenceClient;
        this.objectMapper = objectMapper;
    }

    // ── chat ──────────────────────────────────────────────

    public String chat(MultipartFile audio, String scenario, String history,
                       String nativeLanguage, String nickname) {
        validateAudio(audio);
        requireEnum(scenario, SCENARIOS, "scenario");
        validateHistory(history);
        if (nativeLanguage != null) {
            requireEnum(nativeLanguage, LANGUAGES, "nativeLanguage");
        }
        validateNickname(nickname);

        MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
        parts.add("audio", audio.getResource());
        parts.add("scenario", scenario);
        parts.add("history", history);
        if (nativeLanguage != null) {
            parts.add("nativeLanguage", nativeLanguage);
        }
        if (hasText(nickname)) {
            parts.add("nickname", nickname);
        }
        return inferenceClient.postMultipartChat("/ai/chat", parts);
    }

    // ── stt ───────────────────────────────────────────────

    public String stt(MultipartFile audio, String language) {
        validateAudio(audio);

        MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
        parts.add("audio", audio.getResource());
        if (language != null) {
            parts.add("language", language);
        }
        return inferenceClient.postMultipart("/ai/stt", parts);
    }

    // ── tts ───────────────────────────────────────────────

    public String tts(String rawBody) {
        JsonNode body = parseJson(rawBody);
        JsonNode text = body.get("text");
        if (text == null || text.isNull() || text.asText().isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "text");
        }
        if (text.asText().length() > 200) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "text");
        }
        if (body.hasNonNull("voice")) {
            requireEnum(body.get("voice").asText(), VOICES, "voice");
        }
        if (body.hasNonNull("speed")) {
            double speed = body.get("speed").asDouble();
            if (speed < 0.5 || speed > 1.5) {
                throw new BusinessException(ErrorCode.INVALID_PARAMETER, "speed");
            }
        }
        return inferenceClient.postJson("/ai/tts", rawBody);
    }

    // ── feedback ──────────────────────────────────────────

    public String feedback(String rawBody) {
        JsonNode body = parseJson(rawBody);
        JsonNode target = body.get("targetSentence");
        if (target == null || target.isNull() || target.asText().isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "targetSentence");
        }
        // recognizedText는 null이 유효값이지만 키 자체는 있어야 한다 (명세 §5)
        if (!body.has("recognizedText")) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "recognizedText");
        }
        if (body.hasNonNull("scenario")) {
            requireEnum(body.get("scenario").asText(), SCENARIOS, "scenario");
        }
        if (body.hasNonNull("nativeLanguage")) {
            requireEnum(body.get("nativeLanguage").asText(), LANGUAGES, "nativeLanguage");
        }
        if (body.hasNonNull("nickname")) {
            JsonNode nickname = body.get("nickname");
            if (!nickname.isTextual()) {
                throw new BusinessException(ErrorCode.INVALID_PARAMETER, "nickname");
            }
            validateNickname(nickname.asText());
        }
        return inferenceClient.postJson("/ai/feedback", rawBody);
    }

    // ── 검증 헬퍼 ─────────────────────────────────────────

    /** §1.4 업로드 규격: m4a/wav/webm, 최대 10MB */
    private void validateAudio(MultipartFile audio) {
        if (audio == null || audio.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "audio");
        }
        if (audio.getSize() > MAX_AUDIO_BYTES) {
            throw new BusinessException(ErrorCode.AUDIO_TOO_LARGE, "audio");
        }
        String filename = audio.getOriginalFilename();
        String extension = filename == null ? "" :
                filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
        if (!AUDIO_EXTENSIONS.contains(extension)) {
            throw new BusinessException(ErrorCode.UNSUPPORTED_AUDIO_FORMAT, "audio");
        }
    }

    /** history는 [{role, content}] 배열의 JSON 문자열이어야 한다 (명세 §2) */
    private void validateHistory(String history) {
        JsonNode node = parseJsonField(history, "history");
        if (!node.isArray()) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "history");
        }
        for (JsonNode turn : node) {
            JsonNode role = turn.get("role");
            JsonNode content = turn.get("content");
            boolean roleOk = role != null && Set.of("user", "assistant").contains(role.asText());
            boolean contentOk = content != null && content.isTextual();
            if (!roleOk || !contentOk) {
                throw new BusinessException(ErrorCode.INVALID_PARAMETER, "history");
            }
        }
    }

    /**
     * nickname은 선택값이다 — 명세 §2 / §5.
     *
     * 빈 문자열은 "보내지 않음"과 같게 처리한다. 앱이 온보딩 전이거나 호칭을
     * 아직 안 정했을 때 ""를 보낼 수 있는데, 그걸 400으로 끊으면 아이 화면이 막힌다.
     * 게이트웨이가 앱을 깨뜨리지 않는 쪽을 택한다.
     */
    private void validateNickname(String nickname) {
        if (hasText(nickname) && nickname.length() > MAX_NICKNAME_LENGTH) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "nickname");
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private void requireEnum(String value, Set<String> allowed, String field) {
        if (value == null || !allowed.contains(value)) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, field);
        }
    }

    private JsonNode parseJson(String rawBody) {
        return parseJsonField(rawBody, null);
    }

    private JsonNode parseJsonField(String raw, String field) {
        try {
            return objectMapper.readTree(raw);
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, field);
        }
    }
}
