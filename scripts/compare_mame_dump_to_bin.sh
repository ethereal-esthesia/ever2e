#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'EOF'
usage: compare_mame_dump_to_bin.sh --expected <file.bin> --dump <mame_dump.bin> [--base <addr>]

Compares a MAME memory dump against expected assembled bytes.

Options:
  --expected <file>   Expected binary (required)
  --dump <file>       Dumped binary from MAME (required)
  --base <addr>       Base address for reporting (default: 0x3000)

Examples:
  compare_mame_dump_to_bin.sh --expected /tmp/out.bin --dump /tmp/mame.bin
  compare_mame_dump_to_bin.sh --expected /tmp/out.bin --dump /tmp/mame.bin --base 0x4000
EOF
}

EXPECTED=""
DUMP=""
BASE="0x3000"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --expected) EXPECTED="${2:-}"; shift 2 ;;
    --dump) DUMP="${2:-}"; shift 2 ;;
    --base) BASE="${2:-}"; shift 2 ;;
    -h|--help) usage; exit 0 ;;
    *) echo "unknown arg: $1" >&2; usage; exit 2 ;;
  esac
done

if [[ -z "$EXPECTED" || -z "$DUMP" ]]; then
  usage
  exit 2
fi
if [[ ! -f "$EXPECTED" ]]; then
  echo "error: expected file not found: $EXPECTED" >&2
  exit 2
fi
if [[ ! -f "$DUMP" ]]; then
  echo "error: dump file not found: $DUMP" >&2
  exit 2
fi

base_dec=$((BASE))
exp_size=$(wc -c <"$EXPECTED" | tr -d ' ')
dump_size=$(wc -c <"$DUMP" | tr -d ' ')

echo "expected: $EXPECTED (${exp_size} bytes)"
echo "dump    : $DUMP (${dump_size} bytes)"
echo "base    : $(printf '0x%04X' "$base_dec")"

min_size=$exp_size
if (( dump_size < min_size )); then
  min_size=$dump_size
fi

if (( min_size == 0 )); then
  echo "error: one input is empty" >&2
  exit 2
fi

tmp_exp="$(mktemp "${TMPDIR:-/tmp}/cmp-exp.XXXXXX")"
tmp_dump="$(mktemp "${TMPDIR:-/tmp}/cmp-dump.XXXXXX")"
trap 'rm -f "$tmp_exp" "$tmp_dump"' EXIT

dd if="$EXPECTED" of="$tmp_exp" bs=1 count="$min_size" status=none
dd if="$DUMP" of="$tmp_dump" bs=1 count="$min_size" status=none

if cmp -s "$tmp_exp" "$tmp_dump"; then
  if (( exp_size == dump_size )); then
    echo "match: files are identical"
  else
    echo "prefix match: first ${min_size} bytes identical, size differs"
    echo "  expected size=${exp_size}, dump size=${dump_size}"
  fi
  exit 0
fi

first_off_1based=$(cmp -l "$tmp_exp" "$tmp_dump" | awk 'NR==1{print $1}')
if [[ -z "$first_off_1based" ]]; then
  echo "error: mismatch found but could not locate first differing byte" >&2
  exit 1
fi

off=$((first_off_1based - 1))
addr=$((base_dec + off))

exp_byte=$(od -An -tx1 -j "$off" -N1 "$tmp_exp" | tr -d ' \n')
dump_byte=$(od -An -tx1 -j "$off" -N1 "$tmp_dump" | tr -d ' \n')

echo "mismatch at offset $off (address $(printf '0x%04X' "$addr"))"
echo "  expected=$(echo "$exp_byte" | tr '[:lower:]' '[:upper:]') dump=$(echo "$dump_byte" | tr '[:lower:]' '[:upper:]')"

start=$((off - 8))
if (( start < 0 )); then start=0; fi
count=24

echo
echo "expected window:"
hexdump -v -e '1/1 "%02X "' -s "$start" -n "$count" "$tmp_exp" | sed 's/ $//'
echo
echo "dump window:"
hexdump -v -e '1/1 "%02X "' -s "$start" -n "$count" "$tmp_dump" | sed 's/ $//'
echo

exit 1
