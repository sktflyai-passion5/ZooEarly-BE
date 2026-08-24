package com.zooearly.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zooearly.ai.client.InferenceClient;
import com.zooearly.common.exception.BusinessException;
import com.zooearly.common.response.ErrorCode;
import java.io.IOException;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
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

    /**
     * 동화 장면 순서 — 명세 §7. 하루를 시간순으로 잇는 것이라 순서 자체가 의미다.
     * FastAPI도 이 순서가 아니면 422로 거절하므로, 게이트웨이가 먼저 400으로 끊는다.
     */
    private static final java.util.List<String> STORY_CATEGORIES =
            java.util.List.of("school_arrival", "class", "lunch", "school_departure");
    /** 대화 장면 — 상대방 대사가 있어야 이야기가 성립한다. class(시 읽기)만 예외다 */
    private static final Set<String> STORY_DIALOGUE_CATEGORIES =
            Set.of("school_arrival", "lunch", "school_departure");

    private final InferenceClient inferenceClient;
    private final ObjectMapper objectMapper;

    /**
     * FastAPI 쪽 경로. 앱에 노출하는 /api/v1/ai/* 와 이름이 같을 필요가 없다.
     * 설정에서 주입받는 이유: FastAPI가 경로를 바꿔도 재배포 없이 application.yml만
     * 고치면 되고, 앱 계약은 그대로 유지되기 때문이다.
     */
    private final String chatPath;
    private final String sttPath;
    private final String ttsPath;
    private final String feedbackPath;
    private final String pronunciationPath;
    private final String sentencesPath;
    private final String storyPath;

    public AiRelayService(InferenceClient inferenceClient, ObjectMapper objectMapper,
                          @Value("${inference.path.chat}") String chatPath,
                          @Value("${inference.path.stt}") String sttPath,
                          @Value("${inference.path.tts}") String ttsPath,
                          @Value("${inference.path.feedback}") String feedbackPath,
                          @Value("${inference.path.pronunciation}") String pronunciationPath,
                          @Value("${inference.path.sentences}") String sentencesPath,
                          @Value("${inference.path.story}") String storyPath) {
        this.inferenceClient = inferenceClient;
        this.objectMapper = objectMapper;
        this.chatPath = chatPath;
        this.sttPath = sttPath;
        this.ttsPath = ttsPath;
        this.feedbackPath = feedbackPath;
        this.pronunciationPath = pronunciationPath;
        this.sentencesPath = sentencesPath;
        this.storyPath = storyPath;
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
        parts.add("nickname", nickname);
        return inferenceClient.postMultipartChat(chatPath, parts);
    }

    // ── stt ───────────────────────────────────────────────

    public String stt(MultipartFile audio, String language) {
        validateAudio(audio);

        MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
        parts.add("audio", audio.getResource());
        if (language != null) {
            parts.add("language", language);
        }
        return inferenceClient.postMultipartStt(sttPath, parts);
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
        // 명세 §4 — 읽을 문장의 언어. 필수다.
        // 같은 엔드포인트로 한국어와 모국어가 둘 다 나가므로 추측의 여지를 두지 않는다.
        // /stt의 language(BCP-47 자유 문자열)와 달리 여기는 닫힌 enum이라 검증할 수 있다
        JsonNode language = body.get("language");
        if (language == null || !language.isTextual()) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "language");
        }
        requireEnum(language.asText(), LANGUAGES, "language");
        return inferenceClient.postJson(ttsPath, rawBody);
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
        JsonNode nickname = body.get("nickname");
        if (nickname == null || !nickname.isTextual()) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "nickname");
        }
        validateNickname(nickname.asText());
        return inferenceClient.postJson(feedbackPath, rawBody);
    }

    // ── pronunciation ─────────────────────────────────────

    /**
     * 발음 채점 — 명세 §6.
     *
     * /feedback(표현 교정)과 다르다. 저쪽은 "어떤 단어를 골랐나"를 텍스트로 보고,
     * 이쪽은 "어떻게 소리 냈나"를 오디오로 본다. 그래서 STT를 거치지 않고
     * 녹음을 그대로 보낸다 — 텍스트로는 발음을 알 수 없기 때문이다.
     *
     * targetSentence(자유 텍스트)가 아니라 sentenceId(고정 9개 중 하나)를 받는다 —
     * FastAPI가 채점 기준 문장을 자기 목록에서 직접 찾아야 해서 자유 텍스트를 받지 않는다
     * (2026-08-24 FastAPI 명세 변경). 어떤 id가 유효한지는 여기서 검증하지 않는다 —
     * 그 목록(9개 문장)은 sentences()가 FastAPI에서 그대로 받아오는 것이라 게이트웨이가
     * 따로 알 필요가 없고, 잘못된 id는 FastAPI가 422로 걸러준다.
     */
    public String pronunciation(MultipartFile audio, String sentenceId) {
        validateAudio(audio);
        if (sentenceId == null || sentenceId.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "sentenceId");
        }

        MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
        parts.add("audio", audio.getResource());
        parts.add("sentenceId", sentenceId);
        return inferenceClient.postMultipart(pronunciationPath, parts);
    }

    /**
     * 발음 연습 문장 9개(등교·급식·하교 × 3) 조회 — 명세 §6-1.
     *
     * 입력이 없어 검증할 것도 없다. FastAPI 응답을 그대로 통과시킨다 — §0.1.
     * "표현 고르기" 화면의 선택지 3개가 이 목록에서 온다(시나리오별로 3개씩 필터링).
     */
    public String sentences() {
        return inferenceClient.get(sentencesPath);
    }

    // ── story ─────────────────────────────────────────────

    /**
     * 동화 생성 — 명세 §7.
     *
     * 하루치 플레이 기록(등교·수업·점심·하교 4장면)을 받아 LLM이 동화로 엮는다.
     * 다른 엔드포인트와 달리 "방금 한 행동"이 아니라 "오늘 한 일 전부"를 한 번에 보낸다 —
     * 그 기록을 모아두는 것은 앱의 몫이다. 게이트웨이는 저장하지 않는다(무상태).
     *
     * 여기서 구조를 검증하는 이유: 장면이 4개가 아니거나 순서가 어긋나면 FastAPI가
     * 어차피 422로 돌려보낸다. 그 왕복은 LLM 호출까지 갈 것도 없는 낭비라,
     * "잘못된 요청은 FastAPI까지 보내지 않는다"(§0.1)는 역할대로 먼저 끊는다.
     *
     * body는 파싱만 하고 가공하지 않는다. 앱이 보낸 그대로 FastAPI로 넘어간다 — §0.1.
     */
    public String story(String rawBody) {
        JsonNode body = parseJson(rawBody);

        JsonNode childName = body.get("childName");
        if (childName == null || !childName.isTextual()) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "childName");
        }
        // 내레이션에 그대로 실리는 이름이라 닉네임과 같은 규칙을 쓴다
        validateChildName(childName.asText(), "childName");

        JsonNode scenes = body.get("scenes");
        if (scenes == null || !scenes.isArray() || scenes.size() != STORY_CATEGORIES.size()) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "scenes");
        }

        for (int i = 0; i < scenes.size(); i++) {
            JsonNode scene = scenes.get(i);
            String field = "scenes[" + i + "]";
            if (scene == null || !scene.isObject()) {
                throw new BusinessException(ErrorCode.INVALID_PARAMETER, field);
            }

            JsonNode category = scene.get("category");
            String expected = STORY_CATEGORIES.get(i);
            if (category == null || !category.isTextual() || !expected.equals(category.asText())) {
                throw new BusinessException(ErrorCode.INVALID_PARAMETER, field + ".category");
            }

            if (STORY_DIALOGUE_CATEGORIES.contains(expected)) {
                // 상대방 대사가 없으면 LLM이 지어내야 한다. 실제 기록만으로 쓰는 것이 이 기능의 전제다
                requireNonBlank(scene.get("partnerLine"), field + ".partnerLine");
            } else {
                // class — 아이가 읽은 동시 전문
                requireNonBlank(scene.get("poemText"), field + ".poemText");
            }
        }

        return inferenceClient.postJsonStory(storyPath, rawBody);
    }

    // ── 검증 헬퍼 ─────────────────────────────────────────

    /** 값이 문자열이고 비어 있지 않아야 한다. null·빈 문자열·공백만 있는 경우를 모두 막는다 */
    private void requireNonBlank(JsonNode node, String field) {
        if (node == null || !node.isTextual() || node.asText().isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, field);
        }
    }

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
     * nickname은 필수값이다 — 명세 §2 / §5.
     * 앱 온보딩에서 반드시 입력받는 값이므로 매 요청에 실려 온다.
     * 공백만 있는 값은 호칭으로 쓸 수 없으므로 누락과 같게 본다.
     */
    private void validateNickname(String nickname) {
        validateChildName(nickname, "nickname");
    }

    /**
     * 아이 이름 규칙 — 명세 §2 / §5 / §7.
     *
     * 필드명을 받는 이유: 같은 규칙을 nickname(대화·피드백)과 childName(동화)이 함께 쓰는데,
     * 에러의 field를 고정하면 앱이 어느 입력을 고쳐야 할지 알 수 없다.
     */
    private void validateChildName(String value, String field) {
        if (value == null || value.isBlank() || value.length() > MAX_NICKNAME_LENGTH) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, field);
        }
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
