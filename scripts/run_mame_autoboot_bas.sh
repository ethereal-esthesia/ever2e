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

# Build CR-delimited typed input from BASIC source.
cmd=""
while IFS= read -r line || [[ -n "$line" ]]; do
  cmd+="${line}"$'\r'
done <"$BAS_FILE"
cmd+="RUN"$'\r'

exec "$MAME_BIN" "$MACHINE" \
  -window -video soft -sound none -skip_gameinfo \
  -rompath "$ROMPATH" -homepath "$HOMEPATH" \
  -autoboot_delay "$AUTOBOOT_DELAY" \
  -autoboot_command "$cmd" \
  "$@"
