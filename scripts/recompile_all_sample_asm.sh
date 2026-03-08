#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SAMPLES_DIR="$ROOT/samples"
OUT_DIR="$SAMPLES_DIR/generated"
CLEANUP=0

while [[ $# -gt 0 ]]; do
  case "$1" in
    --samples-dir)
      SAMPLES_DIR="${2:-}"
      shift 2
      ;;
    --out-dir)
      OUT_DIR="${2:-}"
      shift 2
      ;;
    --cleanup)
      CLEANUP=1
      shift
      ;;
    -h|--help)
      cat <<'EOF'
usage: recompile_all_sample_asm.sh [options]

Options:
  --samples-dir <dir>  Directory containing .asm files (default: ./samples)
  --out-dir <dir>      Output directory (default: <samples>/generated)
  --cleanup            Remove generated .bin files after .bas generation
EOF
      exit 0
      ;;
    *)
      echo "unknown arg: $1" >&2
      exit 2
      ;;
  esac
done

if [[ ! -d "$SAMPLES_DIR" ]]; then
  echo "error: samples dir not found: $SAMPLES_DIR" >&2
  exit 2
fi

ASM_FILES=()
while IFS= read -r f; do
  ASM_FILES+=("$f")
done < <(find "$SAMPLES_DIR" -maxdepth 1 -type f -name '*.asm' | sort)
if [[ ${#ASM_FILES[@]} -eq 0 ]]; then
  echo "no .asm files found in $SAMPLES_DIR"
  exit 0
fi

mkdir -p "$OUT_DIR/bin" "$OUT_DIR/basic" "$OUT_DIR/intermediate"

TMP_DIR="$(mktemp -d "${TMPDIR:-/tmp}/asm-rebuild.XXXXXX")"
trap 'rm -rf "$TMP_DIR"' EXIT
CLASS_DIR="$TMP_DIR/classes"
mkdir -p "$CLASS_DIR"

echo "compiling tools.AsmToBasicLoader..."
javac -d "$CLASS_DIR" "$ROOT/src/tools/AsmToBasicLoader.java"

for asm in "${ASM_FILES[@]}"; do
  base_name="$(basename "$asm" .asm)"
  work_asm="$TMP_DIR/${base_name}.ca65.asm"
  obj_file="$TMP_DIR/${base_name}.o"
  bin_file="$OUT_DIR/bin/${base_name}.bin"
  byte_src="$OUT_DIR/intermediate/${base_name}.bytes.asm"
  bas_file="$OUT_DIR/basic/${base_name}.bas"

  load_addr_hex="$(sed -nE 's/^[[:space:]]*\*[[:space:]]*=[[:space:]]*\$([0-9A-Fa-f]+).*/\1/p' "$asm" | head -n1 | tr '[:lower:]' '[:upper:]')"
  if [[ -z "$load_addr_hex" ]]; then
    load_addr_hex="3000"
  fi

  sed -E 's/^[[:space:]]*\*[[:space:]]*=[[:space:]]*\$([0-9A-Fa-f]+)/.org $\1/' "$asm" >"$work_asm"

  echo "assemble: $(basename "$asm") (base=\$${load_addr_hex})"
  ca65 -I "$(dirname "$asm")" "$work_asm" -o "$obj_file"
  ld65 -t none -o "$bin_file" "$obj_file"

  xxd -p -c16 "$bin_file" \
    | awk '
        NF {
          printf ".byte ";
          for (i = 1; i <= length($1); i += 2) {
            if (i > 1) printf ",";
            printf "$%s", substr($1, i, 2);
          }
          printf "\n";
        }
      ' >"$byte_src"

  (
    cd "$ROOT"
    java -cp "$CLASS_DIR" tools.AsmToBasicLoader \
      --in "$byte_src" \
      --out "$bas_file" \
      --base "0x$load_addr_hex" >/dev/null
  )

  if (( CLEANUP == 1 )); then
    rm -f "$bin_file"
  fi

  if (( CLEANUP == 0 )); then
    echo "  -> $bin_file"
  fi
  echo "  -> $bas_file"
done

echo "done: rebuilt ${#ASM_FILES[@]} asm file(s)"
