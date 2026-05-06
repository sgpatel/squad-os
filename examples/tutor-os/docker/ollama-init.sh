#!/bin/sh
#
# ollama-init.sh — pull the chat + embedding models the tutor-api
# expects on first run. Idempotent: `ollama pull` no-ops if a model
# is already cached in the persistent /root/.ollama volume.
#
# Wired as a one-shot init container in docker-compose.yml that runs
# AFTER the ollama service is healthy and BEFORE tutor-api starts —
# so the first tutor-api request never gets a 404 model error.
#
# Override the model list via env:
#   OLLAMA_CHAT_MODEL  (default: llama3.2:3b)
#   OLLAMA_EMBED_MODEL (default: nomic-embed-text)

set -eu

OLLAMA_HOST="${OLLAMA_HOST:-http://ollama:11434}"
CHAT_MODEL="${OLLAMA_CHAT_MODEL:-llama3.2:3b}"
EMBED_MODEL="${OLLAMA_EMBED_MODEL:-nomic-embed-text}"

# Wait for the ollama HTTP API to come up. The healthcheck in the
# compose file already gates `depends_on` for tutor-api, but we still
# need a brief wait here because this init container starts the moment
# the ollama process responds, which is a few hundred ms before
# /api/tags is fully ready.
echo "[ollama-init] waiting for $OLLAMA_HOST/api/tags …"
i=0
until wget -q -O - "$OLLAMA_HOST/api/tags" >/dev/null 2>&1; do
  i=$((i + 1))
  if [ "$i" -ge 60 ]; then
    echo "[ollama-init] ollama did not come up after 60s — giving up" >&2
    exit 1
  fi
  sleep 1
done
echo "[ollama-init] ollama is up"

# `ollama pull` via HTTP — saves us bundling the ollama CLI in this
# image. Streams JSON; we just check the final status.
pull_model() {
  model="$1"
  echo "[ollama-init] pulling $model …"
  # The /api/pull endpoint streams progress; we capture stderr to
  # surface failures but discard the noisy progress JSON on stdout.
  if wget -q -O /dev/null --post-data="{\"name\":\"$model\",\"stream\":false}" \
       --header='Content-Type: application/json' \
       "$OLLAMA_HOST/api/pull"; then
    echo "[ollama-init]   → $model ready"
  else
    echo "[ollama-init]   ! $model pull failed (continuing — model may already be present)" >&2
  fi
}

pull_model "$CHAT_MODEL"
pull_model "$EMBED_MODEL"

echo "[ollama-init] done"
