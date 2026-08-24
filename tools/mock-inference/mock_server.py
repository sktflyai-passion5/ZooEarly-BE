# -*- coding: utf-8 -*-
"""
가짜 FastAPI 추론 서버 — 프론트 연동 테스트용.

진짜 FastAPI가 준비되기 전에도 앱 ↔ 게이트웨이 ↔ (여기) 전 구간을 돌려보려고 만들었다.
명세 §1.2 봉투와 각 엔드포인트의 200 응답 스키마를 그대로 흉내낸다.

  실행:  python mock_server.py            (8000번)
         python mock_server.py 9000       (포트 지정)

파이썬 표준 라이브러리만 쓴다. pip install이 필요 없다.
진짜 추론은 하지 않는다 — STT 결과도 피드백 문구도 전부 고정값이다.
"""
import base64
import json
import re
import sys
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

# 한국어 Windows 콘솔은 기본이 cp949라 '—' 같은 문자를 못 찍고 죽는다.
# 로그 한 줄 때문에 서버가 멈추면 안 되므로 출력을 UTF-8로 돌린다.
for stream in (sys.stdout, sys.stderr):
    try:
        stream.reconfigure(encoding="utf-8", errors="replace")
    except (AttributeError, ValueError):
        pass

# ── 무음 mp3 ────────────────────────────────────────────────
# 앱의 오디오 재생 배선을 확인하려면 진짜 mp3 바이트가 필요하다.
# MPEG-1 Layer3 / 44.1kHz / 128kbps / mono 프레임을 이어붙여 무음 파일을 만든다.
_FRAME = bytes([0xFF, 0xFB, 0x90, 0xC4]) + bytes(413)   # 헤더 4 + 데이터 413 = 417바이트
_FRAMES_PER_SEC = 44100 / 1152                          # 프레임당 1152 샘플


def silent_mp3_base64(seconds=1.0):
    return base64.b64encode(_FRAME * int(_FRAMES_PER_SEC * seconds)).decode()


AUDIO = {"data": silent_mp3_base64(1.0), "format": "mp3"}


# ── 테스트용 강제 에러 ──────────────────────────────────────
# 앱은 어떤 에러에서도 "괜찮아, 다시 해볼까?"로 폴백해야 한다 (명세 §0.4).
# 그 화면을 보려면 에러를 일부러 낼 수단이 필요하다.
# 텍스트나 파일명에 아래 토큰을 넣으면 해당 상황을 재현한다.
# 게이트웨이 application.yml 의 inference.path.* 와 같은 값이어야 한다.
# 옛 /ai/* 도 함께 받아 설정 전환 중에도 끊기지 않게 한다.
ROUTES = {
    "/internal/v1/chat": "chat",
    "/internal/v1/speech/transcribe": "stt",
    "/internal/v1/speech/synthesize": "tts",
    "/internal/v1/feedback/expression": "feedback",
    "/internal/v1/feedback/speaking": "pronunciation",
    "/ai/chat": "chat",
    "/ai/stt": "stt",
    "/ai/tts": "tts",
    "/ai/feedback": "feedback",
    "/ai/pronunciation": "pronunciation",
}

TRIGGERS = {
    "__slow__":       ("느린 응답 — 게이트웨이 타임아웃(504) 유발", None),
    "__stt_fail__":   ("422 STT_FAILED", 422),
    "__rate_limit__": ("429 RATE_LIMITED", 429),
    "__server_err__": ("500 — 게이트웨이가 502 AI_SERVER_ERROR로 감싼다", 500),
}
ERROR_BODY = {
    422: {"code": "STT_FAILED", "message": "STT 엔진에 문제가 생겼어요."},
    429: {"code": "RATE_LIMITED", "message": "요청이 너무 많아요. 잠시 뒤에 다시 해볼까요?"},
}


class Handler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"
    server_version = "MockInference/1.0"

    # ── 요청 읽기 ───────────────────────────────────────────
    def _read_body(self):
        """게이트웨이는 multipart를 chunked로 보낸다 — Content-Length가 없다."""
        if (self.headers.get("Transfer-Encoding") or "").lower() == "chunked":
            data = b""
            while True:
                line = self.rfile.readline().strip()
                if not line:
                    continue
                size = int(line.split(b";")[0], 16)
                if size == 0:
                    while True:
                        if self.rfile.readline() in (b"\r\n", b"\n", b""):
                            break
                    return data
                chunk = b""
                while len(chunk) < size:
                    chunk += self.rfile.read(size - len(chunk))
                data += chunk
                self.rfile.read(2)
        return self.rfile.read(int(self.headers.get("Content-Length") or 0))

    def _parse(self, body):
        """multipart면 필드 dict로, JSON이면 파싱해서 돌려준다."""
        ctype = self.headers.get("Content-Type", "")
        if ctype.startswith("multipart/form-data"):
            m = re.search(r'boundary=(?:"([^"]+)"|([^;]+))', ctype)
            boundary = (m.group(1) or m.group(2)).strip()
            fields = {}
            for part in body.split(("--" + boundary).encode()):
                if not part.strip(b"-\r\n"):
                    continue
                head, _, value = part.partition(b"\r\n\r\n")
                name = re.search(rb'name="([^"]+)"', head)
                if not name:
                    continue
                key = name.group(1).decode()
                filename = re.search(rb'filename="([^"]*)"', head)
                if filename:
                    fields[key] = {"__file__": filename.group(1).decode(),
                                   "bytes": len(value.rstrip(b"\r\n"))}
                else:
                    fields[key] = value.rstrip(b"\r\n").decode("utf-8", "replace")
            return fields
        if not body:
            return {}
        try:
            return json.loads(body.decode("utf-8"))
        except ValueError:
            return {}

    # ── 응답 쓰기 ───────────────────────────────────────────
    def _send(self, status, payload):
        raw = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(raw)))
        self.end_headers()
        self.wfile.write(raw)

    def _ok(self, data):
        self._send(200, {"success": True, "data": data})

    def _fail(self, status):
        body = ERROR_BODY.get(status)
        if body is None:                       # 500 등 — FastAPI 날것 에러를 흉내낸다
            self._send(status, {"detail": "mock internal error"})
        else:
            self._send(status, {"success": False,
                                "error": {**body, "field": None}})

    # ── 라우팅 ──────────────────────────────────────────────
    @staticmethod
    def _searchable(fields):
        """강제 에러 토큰을 찾을 문자열만 모은다.

        JSON 본문의 값은 문자열이 아닐 수 있다 — recognizedText는 null이
        유효한 값이고(명세 §5) similarity는 숫자다. 전부 문자열로 가정하면 터진다.
        """
        parts = []
        for value in fields.values():
            if isinstance(value, str):
                parts.append(value)
            elif isinstance(value, dict) and "__file__" in value:
                parts.append(value["__file__"])
        return " ".join(parts)

    def do_POST(self):
        fields = self._parse(self._read_body())
        haystack = self._searchable(fields)
        self._log(fields)

        for token, (_desc, status) in TRIGGERS.items():
            if token in haystack:
                if status is None:             # __slow__ — 게이트웨이 타임아웃 유발
                    print("  >> %s: 40초 대기" % token, flush=True)
                    time.sleep(40)
                    self._ok({})
                else:
                    print("  >> %s: %d 반환" % (token, status), flush=True)
                    self._fail(status)
                return

        # FastAPI 담당자가 정한 실제 경로. 게이트웨이의 application.yml 과 맞춰야 한다.
        # 옛 /ai/* 경로도 함께 받아준다 — 설정을 바꾸는 중에 둘 다 들어올 수 있다.
        route = ROUTES.get(self.path.rstrip("/"))
        if route == "chat":
            nickname = fields.get("nickname", "친구")
            self._ok({
                "userText": "네, 많이 주세요.",
                "aiText": "%s야, 그래! 많이 줄게. 맛있게 먹어." % nickname,
                "audio": AUDIO,
            })
        elif route == "stt":
            self._ok({"text": "많이 주세여", "confidence": 0.92})
        elif route == "tts":
            self._ok({"audio": AUDIO})
        elif route == "feedback":
            nickname = fields.get("nickname", "친구")
            native = fields.get("nativeLanguage", "KOREAN")
            self._ok({
                "understood": True,
                "matched": True,
                "similarity": 0.92,
                "title": "%s야, 잘했어요!" % nickname,
                "body": "무슨 뜻인지 잘 이해했어요.",
                "naturalSentence": "많이 주세요.",
                "naturalHint": "'주세여'보다 '주세요'가 좋아요.",
                "highlightWords": ["주세요"],
                "translation": None if native == "KOREAN" else "Cho mình nhiều nhé.",
            })
        elif route == "pronunciation":
            # 목표 문장을 어절로 쪼개고, 가운데 어절 하나를 "제일 약한 곳"으로 고정한다.
            # 실제 모델은 z 점수로 고르지만, 여기서는 화면 배선 확인이 목적이다.
            sentence = fields.get("targetSentence", "안녕! 나도 만나서 반가워")
            words = sentence.split()
            idx = min(2, len(words) - 1) if words else 0
            scored = []
            for i, w in enumerate(words):
                z = -1.82 if i == idx else round(-0.55 + i * 0.29, 2)
                scored.append({"word": w, "z": z, "warn": i == idx,
                               "worstPhone": "ㄴ" if i == idx else None})
            quiz = list(words)
            if quiz:
                quiz[idx] = "＿＿＿"
            self._ok({
                "sentence": sentence,
                "targetWord": words[idx] if words else None,
                "targetIndex": idx if words else None,
                "targetZ": -1.82 if words else None,
                "quizSentence": " ".join(quiz),
                "words": scored,
            })
        else:
            self._send(404, {"detail": "no such route: %s" % self.path})

    def do_GET(self):
        """브라우저로 열어 살아있는지 확인하는 용도."""
        self._send(200, {"mock": "inference",
                         "routes": sorted(ROUTES)})

    # ── 로그 ────────────────────────────────────────────────
    def _log(self, fields):
        print("\n[%s] %s" % (time.strftime("%H:%M:%S"), self.path), flush=True)
        for k, v in fields.items():
            if isinstance(v, dict) and "__file__" in v:
                print("  %-15s %s (%d bytes)" % (k, v["__file__"], v["bytes"]), flush=True)
            else:
                print("  %-15s %r" % (k, v), flush=True)   # null·숫자도 그대로 보이게

    def log_message(self, *args):
        pass          # 기본 액세스 로그는 끈다 — 위 _log가 더 읽기 좋다


if __name__ == "__main__":
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 8000
    print("가짜 추론 서버 :%d — 게이트웨이의 INFERENCE_BASE_URL이 여기를 봐야 한다" % port)
    print("강제 에러: 텍스트나 파일명에 아래 토큰을 넣으면 재현된다")
    for token, (desc, _) in TRIGGERS.items():
        print("  %-16s %s" % (token, desc))
    print("-" * 60, flush=True)
    ThreadingHTTPServer(("0.0.0.0", port), Handler).serve_forever()
