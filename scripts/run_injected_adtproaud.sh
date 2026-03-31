#!/usr/bin/env bash
set -euo pipefail

# Launch ever2e-jvm with ADTPro adtproaud.raw injected at $0800.
# Default mode is interactive windowed run.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
RAW_DEFAULT="/Users/shane/ADTPro/dest/org/adtpro/resources/adtproaud.raw"
EMU_BASE_DEFAULT="$ROOT_DIR/ROMS/Apple2eNoSlots.emu"
INJECTED_EMU_DEFAULT="/tmp/adtproaud_injected.emu"

MODE="interactive"
RAW_PATH="$RAW_DEFAULT"
EMU_BASE="$EMU_BASE_DEFAULT"
INJECTED_EMU="$INJECTED_EMU_DEFAULT"
EXTRA_ARGS=""

usage() {
  cat <<USAGE
Usage: $(basename "$0") [options]

Options:
  --headless                 Run with runHeadless task
  --interactive              Run with run task (default)
  --raw <path>               Path to adtproaud.raw
  --base-emu <path>          Base .emu file to clone (default: Apple2eNoSlots.emu)
  --out-emu <path>           Output injected .emu path (default: /tmp/adtproaud_injected.emu)
  --extra-args "<args>"      Extra args passed to emulator run task
  -h, --help                 Show this help

Examples:
  $(basename "$0")
  $(basename "$0") --headless --extra-args "--steps 50000000 --halt-execution 0xBF5B --print-cpu-state-at-exit --no-sound"
USAGE
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --headless)
      MODE="headless"
      shift
      ;;
    --interactive)
      MODE="interactive"
      shift
      ;;
    --raw)
      RAW_PATH="$2"
      shift 2
      ;;
    --base-emu)
      EMU_BASE="$2"
      shift 2
      ;;
    --out-emu)
      INJECTED_EMU="$2"
      shift 2
      ;;
    --extra-args)
      EXTRA_ARGS="$2"
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "Unknown option: $1" >&2
      usage
      exit 2
      ;;
  esac
done

if [[ ! -f "$RAW_PATH" ]]; then
  echo "error: raw payload not found: $RAW_PATH" >&2
  exit 1
fi
if [[ ! -f "$EMU_BASE" ]]; then
  echo "error: base emu not found: $EMU_BASE" >&2
  exit 1
fi

# Clone base .emu and override payload/start address.
awk '
  !/^binary\.file=/ && !/^address\.start=/ { print }
  END {
    print "binary.file='"$RAW_PATH"'"
    print "address.start=0x0800"
  }
' "$EMU_BASE" > "$INJECTED_EMU"

echo "Wrote injected emu: $INJECTED_EMU"
echo "  binary.file=$RAW_PATH"
echo "  address.start=0x0800"

cd "$ROOT_DIR"
if [[ "$MODE" == "headless" ]]; then
  ./gradlew runHeadless --args="$INJECTED_EMU ${EXTRA_ARGS}"
else
  ./gradlew run --args="$INJECTED_EMU ${EXTRA_ARGS}"
fi
