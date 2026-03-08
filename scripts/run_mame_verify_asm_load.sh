#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'EOF'
usage: run_mame_verify_asm_load.sh --asm <file.asm> [options]

Verifies that MAME-loaded bytes match the generated .bin for a sample asm:
1) Ensures generated .bin/.bas exist
2) Fails if outputs are stale vs asm/include dependencies
3) Runs MAME with autoboot BASIC loader + debugger dump
4) Compares dump against expected .bin

Options:
  --asm <file>           Source asm file (required)
  --samples-dir <dir>    Samples directory (default: ./samples)
  --generated-dir <dir>  Generated output dir (default: <samples>/generated)
  --steps <n>            Debugger steps before dump (default: 2000000)
  --start-pc <addr>      One-shot PC breakpoint before stepping (default: asm base addr)
  --stop-on-prompt       Stop when BASIC or monitor prompt loop is reached (default)
  --no-stop-on-prompt    Disable prompt-loop stop and use step timeout only
  --basic-prompt-pc <a>  Primary BASIC prompt PC (default: 0xFD16)
  --monitor-prompt-pc <a> Primary monitor prompt PC (default: 0xC27D)
  --dump <file>          Dump output path (default: /tmp/<asm>.mame.dump.bin)
  --base <addr>          Override dump base address (default: parse from asm, else 0x3000)
  --size <n>             Override dump size (default: expected bin size)
  --autoboot-delay <n>   MAME autoboot delay (default: 0.1)
  --machine <name>       MAME machine (default: apple2ep)
  --keep-debugscript     Keep generated debugscript (default: delete temp file)
  --skip-compare         Skip expected-bin compare (useful for runtime state dumps)
  --headless             Run MAME headless (sets SDL_VIDEODRIVER=dummy)
  --no-full-dump         Disable full 64K dump on exit/breakpoint
  --full-dump <file>     Full dump output path (default: /tmp/<asm>.full.dump.txt)

Default behavior:
  headless: start trace at run, quit at BASIC/monitor prompt
  video   : start trace at run, stop trace at BASIC/monitor prompt

Env overrides:
  MAME_BIN      (default: /Users/shane/Project/mame-alt/mame)
  MAME_ROMPATH  (default: /Users/shane/app/mame/roms)
  MAME_HOMEPATH (default: $HOME/Library/Application Support/mame)
EOF
}

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SAMPLES_DIR="$ROOT/samples"
GENERATED_DIR=""
ASM_PATH=""
STEPS=2000000
START_PC="auto"
STOP_ON_PROMPT=1
BASIC_PROMPT_PC="0xFD16"
MONITOR_PROMPT_PC="0xC27D"
DUMP_PATH=""
BASE_OVERRIDE=""
SIZE_OVERRIDE=""
AUTOBOOT_DELAY=0.1
MACHINE="apple2ep"
KEEP_DEBUGSCRIPT=0
SKIP_COMPARE=0
HEADLESS=0
FULL_DUMP=1
FULL_DUMP_PATH=""

while [[ $# -gt 0 ]]; do
  case "$1" in
    --asm) ASM_PATH="${2:-}"; shift 2 ;;
    --samples-dir) SAMPLES_DIR="${2:-}"; shift 2 ;;
    --generated-dir) GENERATED_DIR="${2:-}"; shift 2 ;;
    --steps) STEPS="${2:-}"; shift 2 ;;
    --start-pc) START_PC="${2:-}"; shift 2 ;;
    --stop-on-prompt) STOP_ON_PROMPT=1; shift ;;
    --no-stop-on-prompt) STOP_ON_PROMPT=0; shift ;;
    --basic-prompt-pc) BASIC_PROMPT_PC="${2:-}"; shift 2 ;;
    --monitor-prompt-pc) MONITOR_PROMPT_PC="${2:-}"; shift 2 ;;
    --dump) DUMP_PATH="${2:-}"; shift 2 ;;
    --base) BASE_OVERRIDE="${2:-}"; shift 2 ;;
    --size) SIZE_OVERRIDE="${2:-}"; shift 2 ;;
    --autoboot-delay) AUTOBOOT_DELAY="${2:-}"; shift 2 ;;
    --machine) MACHINE="${2:-}"; shift 2 ;;
    --keep-debugscript) KEEP_DEBUGSCRIPT=1; shift ;;
    --skip-compare) SKIP_COMPARE=1; shift ;;
    --headless) HEADLESS=1; shift ;;
    --no-full-dump) FULL_DUMP=0; shift ;;
    --full-dump) FULL_DUMP_PATH="${2:-}"; shift 2 ;;
    -h|--help) usage; exit 0 ;;
    *) echo "unknown arg: $1" >&2; usage; exit 2 ;;
  esac
done

if [[ -z "$ASM_PATH" ]]; then
  echo "error: --asm is required" >&2
  usage
  exit 2
fi

if [[ ! -f "$ASM_PATH" ]]; then
  if [[ -f "$SAMPLES_DIR/$ASM_PATH" ]]; then
    ASM_PATH="$SAMPLES_DIR/$ASM_PATH"
  else
    echo "error: asm file not found: $ASM_PATH" >&2
    exit 2
  fi
fi

if [[ -z "$GENERATED_DIR" ]]; then
  GENERATED_DIR="$SAMPLES_DIR/generated"
fi

MAME_BIN="${MAME_BIN:-/Users/shane/Project/mame-alt/mame}"
MAME_ROMPATH="${MAME_ROMPATH:-/Users/shane/app/mame/roms}"
MAME_HOMEPATH="${MAME_HOMEPATH:-$HOME/Library/Application Support/mame}"

if [[ ! -x "$MAME_BIN" ]]; then
  echo "error: MAME binary not executable: $MAME_BIN" >&2
  exit 2
fi

base_name="$(basename "$ASM_PATH" .asm)"
expected_bin="$GENERATED_DIR/bin/${base_name}.bin"
loader_bas="$GENERATED_DIR/basic/${base_name}.bas"

if [[ ! -f "$expected_bin" ]]; then
  echo "error: expected bin missing: $expected_bin" >&2
  echo "hint: run scripts/recompile_all_sample_asm.sh" >&2
  exit 2
fi
if [[ ! -f "$loader_bas" ]]; then
  echo "error: expected loader bas missing: $loader_bas" >&2
  echo "hint: run scripts/recompile_all_sample_asm.sh" >&2
  exit 2
fi

declare -a deps=()
add_dep() {
  local dep="$1"
  local d
  if (( ${#deps[@]} > 0 )); then
    for d in "${deps[@]}"; do
      [[ "$d" == "$dep" ]] && return 1
    done
  fi
  deps+=("$dep")
  return 0
}
collect_deps() {
  local f="$1"
  local inc rel
  add_dep "$f" || true
  while IFS= read -r rel; do
    inc="$(cd "$(dirname "$f")" && pwd)/$rel"
    if [[ -f "$inc" ]]; then
      if add_dep "$inc"; then
        collect_deps "$inc"
      fi
    fi
  done < <(sed -nE 's/^[[:space:]]*\.include[[:space:]]+"([^"]+)".*/\1/p' "$f")
}
collect_deps "$ASM_PATH"

stale=0
if (( ${#deps[@]} > 0 )); then
  for dep in "${deps[@]}"; do
    if [[ "$expected_bin" -ot "$dep" || "$loader_bas" -ot "$dep" ]]; then
      stale=1
      echo "stale: output older than dependency: $dep" >&2
    fi
  done
fi
if (( stale == 1 )); then
  echo "error: generated artifacts are stale; re-run recompile script first." >&2
  exit 2
fi

if [[ -n "$BASE_OVERRIDE" ]]; then
  base_dec=$((BASE_OVERRIDE))
else
  parsed="$(sed -nE 's/^[[:space:]]*\*[[:space:]]*=[[:space:]]*\$([0-9A-Fa-f]+).*/\1/p' "$ASM_PATH" | head -n1)"
  if [[ -z "$parsed" ]]; then
    base_dec=$((0x3000))
  else
    base_dec=$((16#$parsed))
  fi
fi

if [[ "$START_PC" == "auto" ]]; then
  START_PC="$(printf '0x%X' "$base_dec")"
fi

if [[ -n "$SIZE_OVERRIDE" ]]; then
  size_dec=$((SIZE_OVERRIDE))
else
  size_dec=$(wc -c <"$expected_bin" | tr -d ' ')
fi

if [[ -z "$DUMP_PATH" ]]; then
  DUMP_PATH="/tmp/${base_name}.mame.dump.bin"
fi
if [[ -z "$FULL_DUMP_PATH" ]]; then
  FULL_DUMP_PATH="/tmp/${base_name}.full.dump.txt"
fi

autoboot_cmd="$(
  perl -0777 -pe 's/\r\n|\n\r|\r/\n/g; s/[\x00-\x08\x0B\x0C\x0E-\x1F\x7F]//g; s/\\/\\\\/g; s/\n/\\r/g' "$loader_bas"
)"
autoboot_cmd+="\\rRUN\\r"

tmp_debugscript="$(mktemp "${TMPDIR:-/tmp}/mame-verify.XXXXXX")"
debugscript="${tmp_debugscript}.cmd"
mv "$tmp_debugscript" "$debugscript"
if (( KEEP_DEBUGSCRIPT == 0 )); then
  trap 'rm -f "$debugscript"' EXIT
fi

if (( STOP_ON_PROMPT == 1 )); then
  primary_dump_cmd="save ${DUMP_PATH},0x$(printf "%04X" "$base_dec"),0x$(printf "%X" "$size_dec");"
  full_dump_cmd=""
  if (( FULL_DUMP == 1 )); then
    full_dump_cmd=" dump ${FULL_DUMP_PATH},0x0000,0x10000;"
  fi
  if (( HEADLESS == 1 )); then
    prompt_action="{${primary_dump_cmd}${full_dump_cmd} quit}"
  else
    prompt_action="{trace off,maincpu; go}"
  fi
  basic_bp_2=$((BASIC_PROMPT_PC + 0x0002))
  basic_bp_3=$((BASIC_PROMPT_PC + 0x0004))
  monitor_bp_2=$((MONITOR_PROMPT_PC + 0x0002))
  monitor_bp_3=$((MONITOR_PROMPT_PC + 0x000E))
  monitor_bp_4=$((MONITOR_PROMPT_PC + 0x0011))
  if (( HEADLESS == 1 )); then
    if [[ -n "$START_PC" ]]; then
      cat >"$debugscript" <<EOF
focus maincpu
bpset 0x$(printf "%X" "$((START_PC))"),1
go
trace /tmp/${base_name}.verify.trace,maincpu,noloop,{tracelog "PC=%04X TC=%08X\n",pc,totalcycles}
bpset 0x$(printf "%X" "$((BASIC_PROMPT_PC))"),1,${prompt_action}
bpset 0x$(printf "%X" "$basic_bp_2"),1,${prompt_action}
bpset 0x$(printf "%X" "$basic_bp_3"),1,${prompt_action}
bpset 0x$(printf "%X" "$((MONITOR_PROMPT_PC))"),1,${prompt_action}
bpset 0x$(printf "%X" "$monitor_bp_2"),1,${prompt_action}
bpset 0x$(printf "%X" "$monitor_bp_3"),1,${prompt_action}
bpset 0x$(printf "%X" "$monitor_bp_4"),1,${prompt_action}
go
step #${STEPS}
trace off,maincpu
${primary_dump_cmd}
${full_dump_cmd:+$full_dump_cmd}
quit
EOF
    else
      cat >"$debugscript" <<EOF
focus maincpu
trace /tmp/${base_name}.verify.trace,maincpu,noloop,{tracelog "PC=%04X TC=%08X\n",pc,totalcycles}
bpset 0x$(printf "%X" "$((BASIC_PROMPT_PC))"),1,${prompt_action}
bpset 0x$(printf "%X" "$basic_bp_2"),1,${prompt_action}
bpset 0x$(printf "%X" "$basic_bp_3"),1,${prompt_action}
bpset 0x$(printf "%X" "$((MONITOR_PROMPT_PC))"),1,${prompt_action}
bpset 0x$(printf "%X" "$monitor_bp_2"),1,${prompt_action}
bpset 0x$(printf "%X" "$monitor_bp_3"),1,${prompt_action}
bpset 0x$(printf "%X" "$monitor_bp_4"),1,${prompt_action}
go
step #${STEPS}
trace off,maincpu
${primary_dump_cmd}
${full_dump_cmd:+$full_dump_cmd}
quit
EOF
    fi
  else
    if [[ -n "$START_PC" ]]; then
      cat >"$debugscript" <<EOF
focus maincpu
bpset 0x$(printf "%X" "$((START_PC))"),1
go
trace /tmp/${base_name}.verify.trace,maincpu,noloop,{tracelog "PC=%04X TC=%08X\n",pc,totalcycles}
bpset 0x$(printf "%X" "$((BASIC_PROMPT_PC))"),1,${prompt_action}
bpset 0x$(printf "%X" "$basic_bp_2"),1,${prompt_action}
bpset 0x$(printf "%X" "$basic_bp_3"),1,${prompt_action}
bpset 0x$(printf "%X" "$((MONITOR_PROMPT_PC))"),1,${prompt_action}
bpset 0x$(printf "%X" "$monitor_bp_2"),1,${prompt_action}
bpset 0x$(printf "%X" "$monitor_bp_3"),1,${prompt_action}
bpset 0x$(printf "%X" "$monitor_bp_4"),1,${prompt_action}
go
EOF
    else
      cat >"$debugscript" <<EOF
focus maincpu
trace /tmp/${base_name}.verify.trace,maincpu,noloop,{tracelog "PC=%04X TC=%08X\n",pc,totalcycles}
bpset 0x$(printf "%X" "$((BASIC_PROMPT_PC))"),1,${prompt_action}
bpset 0x$(printf "%X" "$basic_bp_2"),1,${prompt_action}
bpset 0x$(printf "%X" "$basic_bp_3"),1,${prompt_action}
bpset 0x$(printf "%X" "$((MONITOR_PROMPT_PC))"),1,${prompt_action}
bpset 0x$(printf "%X" "$monitor_bp_2"),1,${prompt_action}
bpset 0x$(printf "%X" "$monitor_bp_3"),1,${prompt_action}
bpset 0x$(printf "%X" "$monitor_bp_4"),1,${prompt_action}
go
EOF
    fi
  fi
else
  if [[ -n "$START_PC" ]]; then
    cat >"$debugscript" <<EOF
focus maincpu
bpset 0x$(printf "%X" "$((START_PC))"),1
go
step #${STEPS}
save ${DUMP_PATH},0x$(printf "%04X" "$base_dec"),0x$(printf "%X" "$size_dec")
$(if (( FULL_DUMP == 1 )); then printf 'dump %s,0x0000,0x10000\n' "$FULL_DUMP_PATH"; fi)
quit
EOF
  else
    cat >"$debugscript" <<EOF
focus maincpu
step #${STEPS}
save ${DUMP_PATH},0x$(printf "%04X" "$base_dec"),0x$(printf "%X" "$size_dec")
$(if (( FULL_DUMP == 1 )); then printf 'dump %s,0x0000,0x10000\n' "$FULL_DUMP_PATH"; fi)
quit
EOF
  fi
fi

echo "verify asm  : $ASM_PATH"
echo "expected bin: $expected_bin"
echo "loader bas  : $loader_bas"
echo "dump path   : $DUMP_PATH"
echo "base/size   : $(printf '0x%04X' "$base_dec") / $(printf '0x%X' "$size_dec")"
echo "steps       : $STEPS"
if [[ -n "$START_PC" ]]; then
  echo "start pc    : $(printf '0x%04X' "$((START_PC))")"
else
  echo "start pc    : disabled"
fi
if (( STOP_ON_PROMPT == 1 )); then
  echo "stop prompt : enabled (basic=$(printf '0x%04X' "$((BASIC_PROMPT_PC))"), monitor=$(printf '0x%04X' "$((MONITOR_PROMPT_PC))"))"
else
  echo "stop prompt : disabled"
fi
echo "machine     : $MACHINE"
if (( HEADLESS == 1 )); then
  echo "headless    : enabled (SDL_VIDEODRIVER=dummy)"
else
  echo "headless    : disabled"
fi
if (( FULL_DUMP == 1 )); then
  echo "full dump   : enabled ($FULL_DUMP_PATH)"
else
  echo "full dump   : disabled"
fi

if (( HEADLESS == 1 )); then
  SDL_VIDEODRIVER=dummy "$MAME_BIN" "$MACHINE" \
    -window -video soft -sound none -skip_gameinfo \
    -rompath "$MAME_ROMPATH" -homepath "$MAME_HOMEPATH" \
    -autoboot_delay "$AUTOBOOT_DELAY" \
    -autoboot_command "$autoboot_cmd" \
    -debug -debugger none -debugscript "$debugscript" \
    -sl1 "" -sl2 "" -sl3 "" -sl4 "" -sl5 "" -sl6 "" -sl7 ""
else
  "$MAME_BIN" "$MACHINE" \
    -window -video soft -sound none -skip_gameinfo \
    -rompath "$MAME_ROMPATH" -homepath "$MAME_HOMEPATH" \
    -autoboot_delay "$AUTOBOOT_DELAY" \
    -autoboot_command "$autoboot_cmd" \
    -debug -debugger none -debugscript "$debugscript" \
    -sl1 "" -sl2 "" -sl3 "" -sl4 "" -sl5 "" -sl6 "" -sl7 ""
fi

if (( SKIP_COMPARE == 0 )); then
  "$ROOT/scripts/compare_mame_dump_to_bin.sh" \
    --expected "$expected_bin" \
    --dump "$DUMP_PATH" \
    --base "$(printf '0x%X' "$base_dec")"
  echo "verify result: dump matches expected bin"
else
  echo "verify result: compare skipped (dump captured only)"
fi
