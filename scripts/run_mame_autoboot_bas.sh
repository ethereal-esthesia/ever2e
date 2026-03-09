#!/usr/bin/env bash
set -euo pipefail

if [[ $# -lt 1 ]]; then
  echo "usage: $0 <basic-file> [mame extra args...]" >&2
  exit 2
fi

BAS_FILE="$1"
shift

if [[ ! -f "$BAS_FILE" ]]; then
  echo "error: BASIC file not found: $BAS_FILE" >&2
  exit 2
fi

MAME_BIN="${MAME_BIN:-/Users/shane/Project/mame-alt/mame}"
MACHINE="${MAME_MACHINE:-apple2ep}"
ROMPATH="${MAME_ROMPATH:-/Users/shane/app/mame/roms}"
HOMEPATH="${MAME_HOMEPATH:-$HOME/Library/Application Support/mame}"
AUTOBOOT_DELAY="${MAME_AUTOBOOT_DELAY:-4}"

if [[ ! -x "$MAME_BIN" ]]; then
  echo "error: mame binary not executable: $MAME_BIN" >&2
  exit 2
fi

# Build \r-delimited typed input from BASIC source.
# - Normalize any host line endings to '\n'
# - Drop remaining control chars (except '\n')
# - Escape literal backslashes so they survive Lua parsing
# - Convert '\n' to literal "\r"
normalized_bas="$(
  perl -0777 -pe 's/\r\n|\n\r|\r/\n/g; s/[\x00-\x08\x0B\x0C\x0E-\x1F\x7F]//g; s/\\/\\\\/g; s/\n/\\r/g' "$BAS_FILE"
)"
cmd="${normalized_bas}\\rRUN\\r"

exec "$MAME_BIN" "$MACHINE" \
  -window -video soft -sound none -skip_gameinfo \
  -rompath "$ROMPATH" -homepath "$HOMEPATH" \
  -autoboot_delay "$AUTOBOOT_DELAY" \
  -autoboot_command "$cmd" \
  -sl1 "" -sl2 "" -sl3 "" -sl4 "" -sl5 "" -sl6 "" -sl7 "" \
  "$@"
