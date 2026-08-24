#!/usr/bin/env bash
# 게이트웨이가 살아있고 에러 계약을 지키는지 한 번에 확인한다.
#
#   ./tools/smoke-test.sh                                   로컬 (localhost:8080)
#   ./tools/smoke-test.sh http://localhost:18080            포트 지정
#   ./tools/smoke-test.sh https://<FQDN>                    배포 후 확인
#
# 강제 에러 토큰(__slow__ 등)은 목 서버만 이해한다.
# 진짜 FastAPI를 붙였을 때는 1번과 5번만 의미가 있고 나머지는 건너뛴다.
set -u

BASE="${1:-http://localhost:8080}"
TTS="$BASE/api/v1/ai/tts"
pass=0
fail=0

# $1 설명 / $2 기대 상태코드 / $3 기대 에러코드(없으면 -) / $4 요청 body
check() {
  local name="$1" want_status="$2" want_code="$3" body="$4"
  local out status doc
  # 본문과 상태코드를 한 번의 호출로 받는다 — 두 번 부르면 타임아웃 케이스가 두 배로 걸린다
  out=$(curl -s -m 45 -w '\n%{http_code}' -X POST "$TTS" \
          -H 'Content-Type: application/json' -d "$body" 2>/dev/null)
  status="${out##*$'\n'}"
  doc="${out%$'\n'*}"

  local ok=1
  [ "$status" = "$want_status" ] || ok=0
  if [ "$want_code" != "-" ]; then
    case "$doc" in *"\"$want_code\""*) ;; *) ok=0 ;; esac
  fi

  if [ "$ok" = "1" ]; then
    printf '  \033[32mOK\033[0m   %-22s %s %s\n' "$name" "$status" "$want_code"
    pass=$((pass + 1))
  else
    printf '  \033[31mFAIL\033[0m %-22s 기대=%s/%s 실제=%s\n' "$name" "$want_status" "$want_code" "$status"
    printf '       %s\n' "$doc"
    fail=$((fail + 1))
  fi
}

echo "대상: $BASE"
echo "------------------------------------------------------------"

check "검증실패"      400 INVALID_PARAMETER '{"language":"KOREAN"}'
check "정상"          200 -                 '{"text":"안녕","language":"KOREAN"}'
check "STT엔진죽음"    422 STT_FAILED        '{"text":"__stt_fail__","language":"KOREAN"}'
check "쿼터초과"      429 RATE_LIMITED      '{"text":"__rate_limit__","language":"KOREAN"}'
check "추론서버5xx"    502 AI_SERVER_ERROR   '{"text":"__server_err__","language":"KOREAN"}'
check "타임아웃"      504 AI_TIMEOUT        '{"text":"__slow__","language":"KOREAN"}'

echo "------------------------------------------------------------"
echo "통과 $pass / 실패 $fail"
[ "$fail" -eq 0 ]
