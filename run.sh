#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────────────
# SquadOS Phase 1 — build & test runner
# Works with plain JDK 21 (javac + java). Maven optional.
#
# Usage:
#   chmod +x run.sh
#   ./run.sh          # compile + run all 17 tests
#   ./run.sh demo     # compile + run the Hello Squad demo
#   ./run.sh clean    # remove compiled output
# ─────────────────────────────────────────────────────────────────────────────

set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
CORE="$ROOT/squad-core"
STARTER="$ROOT/squad-starter"
OUT="$ROOT/out"
CORE_OUT="$OUT/core"
STARTER_OUT="$OUT/starter"

GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m'

step() { echo -e "${CYAN}▶ $1${NC}"; }
ok()   { echo -e "${GREEN}✓ $1${NC}"; }
err()  { echo -e "${RED}✗ $1${NC}"; exit 1; }
warn() { echo -e "${YELLOW}! $1${NC}"; }

# ── Verify Java 21 ────────────────────────────────────────────────────────────
check_java() {
  if ! command -v javac &>/dev/null; then
    err "javac not found. Install JDK 21: https://adoptium.net"
  fi
  JAVA_VER=$(javac -version 2>&1 | awk '{print $2}' | cut -d'.' -f1)
  if [ "$JAVA_VER" -lt 21 ]; then
    err "Java 21+ required (found $JAVA_VER). Download: https://adoptium.net"
  fi
  ok "Java $JAVA_VER found"
}

# ── Clean ─────────────────────────────────────────────────────────────────────
clean() {
  step "Cleaning output directories"
  rm -rf "$OUT"
  ok "Clean done"
}

# ── Compile squad-core ────────────────────────────────────────────────────────
compile_core() {
  step "Compiling squad-core (main sources)"
  mkdir -p "$CORE_OUT"

  # Collect all main source files
  find "$CORE/src/main/java" -name "*.java" > /tmp/squados_sources.txt
  SOURCE_COUNT=$(wc -l < /tmp/squados_sources.txt)
  echo "  Found $SOURCE_COUNT source files"

  javac \
    --release 21 \
    -d "$CORE_OUT" \
    @/tmp/squados_sources.txt \
    2>&1 || err "Compilation failed — check errors above"

  ok "squad-core compiled ($SOURCE_COUNT files)"
}

# ── Compile tests ─────────────────────────────────────────────────────────────
compile_tests() {
  step "Compiling test sources"
  mkdir -p "$CORE_OUT"

  find "$CORE/src/test/java" -name "*.java" > /tmp/squados_tests.txt
  TEST_COUNT=$(wc -l < /tmp/squados_tests.txt)
  echo "  Found $TEST_COUNT test files"

  javac \
    --release 21 \
    -cp "$CORE_OUT" \
    -d "$CORE_OUT" \
    @/tmp/squados_tests.txt \
    2>&1 || err "Test compilation failed — check errors above"

  ok "Tests compiled ($TEST_COUNT files)"
}

# ── Run tests ─────────────────────────────────────────────────────────────────
run_tests() {
  step "Running Phase 1 test suite"
  echo ""

  java \
    -cp "$CORE_OUT" \
    io.squados.tests.SquadOsPhase1Tests \
    2>&1

  TEST_EXIT=$?
  echo ""
  if [ $TEST_EXIT -eq 0 ]; then
    ok "All tests passed — Phase 1 gate cleared"
  else
    err "Tests failed — see output above"
  fi
}

# ── Compile starter ───────────────────────────────────────────────────────────
compile_starter() {
  step "Compiling squad-starter (hello world)"
  mkdir -p "$STARTER_OUT"

  # Copy squad.yml to output classpath root
  cp "$STARTER/src/main/resources/squad.yml" "$STARTER_OUT/"

  find "$STARTER/src/main/java" -name "*.java" > /tmp/squados_starter.txt

  javac \
    --release 21 \
    -cp "$CORE_OUT" \
    -d "$STARTER_OUT" \
    @/tmp/squados_starter.txt \
    2>&1 || err "Starter compilation failed"

  ok "squad-starter compiled"
}

# ── Run demo ──────────────────────────────────────────────────────────────────
run_demo() {
  echo ""
  echo -e "${CYAN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
  echo -e "${CYAN}  SquadOS Hello Squad Demo                 ${NC}"
  echo -e "${CYAN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
  echo ""

  if [ -n "${ANTHROPIC_API_KEY:-}" ]; then
    warn "ANTHROPIC_API_KEY set — real LLM wiring requires Phase 2 (SpringAiLlmAdapter)"
    warn "Running with MockLlmPort for Phase 1 validation"
  fi

  java \
    -cp "$CORE_OUT:$STARTER_OUT" \
    com.example.Main \
    2>&1
}

# ── Main ──────────────────────────────────────────────────────────────────────
MODE="${1:-test}"

case "$MODE" in
  clean)
    clean
    ;;
  demo)
    check_java
    compile_core
    compile_starter
    run_demo
    ;;
  test|"")
    check_java
    compile_core
    compile_tests
    run_tests
    ;;
  all)
    check_java
    compile_core
    compile_tests
    run_tests
    compile_starter
    run_demo
    ;;
  *)
    echo "Usage: ./run.sh [test|demo|clean|all]"
    exit 1
    ;;
esac
