#!/usr/bin/env bash
# Local backend'i AI saglayicisi dogru yapilandirilmis sekilde baslatir.
#
# Neden: application.yml'deki AI_PROVIDER/OLLAMA_*/ANTHROPIC_* degiskenleri
# set edilmeden calistirilirsa AI tedavi onerisi/SOAP taslagi ozellikleri
# sessizce "AI modeli henuz baglanmadi" fallback'ine duser (bkz.
# OllamaTreatmentRecommendationAdapter, ClaudeTreatmentRecommendationAdapter).
#
# Kullanim: backend/ dizininden calistir: ./run-local.sh
#
# Saglayici secimi (AI_PROVIDER):
#   ollama (varsayilan) -- yerel/ucretsiz, OLLAMA_MODEL Ollama'da fiilen
#     `ollama pull` ile indirilmis tam etiketle birebir eslesmeli
#     (ornegin llama3.1:8b-instruct-q4_0) -- "llama3.1" gibi kisa bir isim
#     Ollama'da 404 doner.
#   claude -- Anthropic API, ucretli. ANTHROPIC_API_KEY GEREKLI.
#
# Gercek ANTHROPIC_API_KEY'i asla bu dosyaya veya baska bir commit'lenen
# dosyaya yazma -- bunun yerine backend/.env.local olustur (gitignore'da,
# bkz. .gitignore ".env.local" satiri) ve icine ekle:
#   AI_PROVIDER=claude
#   ANTHROPIC_API_KEY=sk-ant-...
# Bu script varsa .env.local'i otomatik okur.

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
if [ -f "$SCRIPT_DIR/.env.local" ]; then
  set -a
  # shellcheck disable=SC1091
  source "$SCRIPT_DIR/.env.local"
  set +a
fi

export AI_PROVIDER="${AI_PROVIDER:-ollama}"
export OLLAMA_BASE_URL="${OLLAMA_BASE_URL:-http://localhost:11434}"
export OLLAMA_MODEL="${OLLAMA_MODEL:-llama3.1:8b-instruct-q4_0}"
export ANTHROPIC_MODEL="${ANTHROPIC_MODEL:-claude-sonnet-5}"

echo "AI_PROVIDER=$AI_PROVIDER"
if [ "$AI_PROVIDER" = "claude" ]; then
  echo "ANTHROPIC_MODEL=$ANTHROPIC_MODEL"
  if [ -z "$ANTHROPIC_API_KEY" ]; then
    echo "UYARI: ANTHROPIC_API_KEY bos -- fallback moduna dusecek. backend/.env.local dosyasina ekleyin." >&2
  fi
else
  echo "OLLAMA_BASE_URL=$OLLAMA_BASE_URL"
  echo "OLLAMA_MODEL=$OLLAMA_MODEL"
fi

./mvnw spring-boot:run
