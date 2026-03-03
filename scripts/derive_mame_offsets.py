#!/usr/bin/env python3
import argparse
import csv
import glob
import os
import re
import statistics
from typing import Dict, List, Optional, Tuple


FRAME_CYCLES_NTSC = 65 * 262


def parse_hex(value: str) -> Optional[int]:
    if value is None:
        return None
    text = value.strip()
    if not text or text == "--":
        return None
    return int(text, 16)


def load_mame(path: str) -> List[Dict[str, Optional[int]]]:
    rows: List[Dict[str, Optional[int]]] = []
    with open(path, newline="") as fh:
        reader = csv.DictReader(fh)
        for row in reader:
            rows.append(
                {
                    "pc": parse_hex(row.get("pc", "")),
                    "a": parse_hex(row.get("a", "")),
                    "x": parse_hex(row.get("x", "")),
                    "y": parse_hex(row.get("y", "")),
                    "p": parse_hex(row.get("p", "")),
                    "s": parse_hex(row.get("s", "")),
                    "tc": int(row["totalcycles"]) if row.get("totalcycles") else None,
                }
            )
    return rows


def load_jvm(path: str) -> List[Dict[str, Optional[int]]]:
    out: List[Dict[str, Optional[int]]] = []
    raw: List[Dict[str, Optional[int]]] = []
    with open(path, newline="") as fh:
        reader = csv.DictReader(fh)
        for row in reader:
            event_type = (row.get("event_type") or "").strip().lower()
            if event_type != "instr":
                continue
            tc: Optional[int] = None
            tc_raw = (row.get("cpu_total_cycles") or "").strip()
            if tc_raw:
                try:
                    tc = int(tc_raw)
                except ValueError:
                    tc = None
            raw.append(
                {
                    "pc": parse_hex(row.get("pc", "")),
                    "a": parse_hex(row.get("a", "")),
                    "x": parse_hex(row.get("x", "")),
                    "y": parse_hex(row.get("y", "")),
                    "p": parse_hex(row.get("p", "")),
                    "s": parse_hex(row.get("s", "")),
                    "tc": tc,
                }
            )

    # Collapse adjacent duplicates to retired-instruction granularity.
    prev_sig: Optional[Tuple[Optional[int], ...]] = None
    for row in raw:
        rec = row
        sig = (rec["pc"], rec["a"], rec["x"], rec["y"], rec["p"], rec["s"], rec["tc"])
        if sig != prev_sig:
            out.append(rec)
            prev_sig = sig
    return out


def row_sig(row: Dict[str, Optional[int]]) -> Tuple[Optional[int], ...]:
    return (row["pc"], row["a"], row["x"], row["y"], row["p"], row["s"])


def match_prefix(mame_rows: List[Dict[str, Optional[int]]], jvm_rows: List[Dict[str, Optional[int]]]) -> int:
    limit = min(len(mame_rows), len(jvm_rows))
    for i in range(limit):
        if row_sig(mame_rows[i]) != row_sig(jvm_rows[i]):
            return i
    return limit


def cycle_offset_stats(
    mame_rows: List[Dict[str, Optional[int]]], jvm_rows: List[Dict[str, Optional[int]]], prefix_len: int
) -> Dict[str, Optional[float]]:
    diffs: List[int] = []
    for i in range(prefix_len):
        mtc = mame_rows[i]["tc"]
        jtc = jvm_rows[i]["tc"]
        if mtc is None or jtc is None:
            continue
        diffs.append(mtc - jtc)
    if not diffs:
        return {"count": 0, "mode": None, "median": None, "min": None, "max": None}
    return {
        "count": len(diffs),
        "mode": statistics.mode(diffs),
        "median": statistics.median(diffs),
        "min": min(diffs),
        "max": max(diffs),
    }


def extract_phase(path: str) -> Optional[int]:
    name = os.path.basename(path)
    m = re.search(r"(\d+)(?=\.csv$)", name)
    if not m:
        return None
    return int(m.group(1))


def scan_phase_glob(mame_path: str, phase_glob: str) -> List[Tuple[int, str, int]]:
    mame_rows = load_mame(mame_path)
    scored: List[Tuple[int, str, int]] = []
    for path in sorted(glob.glob(phase_glob)):
        phase = extract_phase(path)
        if phase is None:
            continue
        jvm_rows = load_jvm(path)
        prefix = match_prefix(mame_rows, jvm_rows)
        scored.append((phase, path, prefix))
    scored.sort(key=lambda x: (-x[2], x[0]))
    return scored


def main() -> None:
    parser = argparse.ArgumentParser(description="Derive CPU and phase offsets from MAME/JVM traces.")
    parser.add_argument("--mame", required=True, help="MAME CSV with totalcycles and registers.")
    parser.add_argument("--jvm", required=True, help="JVM CSV with cpu_total_cycles and registers.")
    parser.add_argument("--phase-glob", help="Optional JVM phase sweep glob, e.g. '/private/tmp/jvm_phase_sync_*.csv'")
    parser.add_argument("--top", type=int, default=8, help="How many phase candidates to print.")
    args = parser.parse_args()

    mame_rows = load_mame(args.mame)
    jvm_rows = load_jvm(args.jvm)
    prefix = match_prefix(mame_rows, jvm_rows)
    stats = cycle_offset_stats(mame_rows, jvm_rows, prefix)

    print(f"mame_rows={len(mame_rows)} jvm_rows_collapsed={len(jvm_rows)}")
    print(f"matched_prefix={prefix}")
    if prefix < min(len(mame_rows), len(jvm_rows)):
        m = mame_rows[prefix]
        j = jvm_rows[prefix]
        print(
            "first_divergence "
            f"idx={prefix+1} "
            f"mame_pc={m['pc']:04X} jvm_pc={j['pc']:04X} "
            f"mame_a={m['a']:02X} jvm_a={j['a']:02X} "
            f"mame_p={m['p']:02X} jvm_p={j['p']:02X}"
        )

    print("cpu_cycle_offset_stats")
    print(
        f"count={stats['count']} mode={stats['mode']} median={stats['median']} "
        f"min={stats['min']} max={stats['max']}"
    )
    if stats["mode"] is not None:
        print(f"recommended_cpu_cycle_offset={int(stats['mode'])}")

    if args.phase_glob:
        scored = scan_phase_glob(args.mame, args.phase_glob)
        if not scored:
            print("phase_scan: no files matched")
            return
        print(f"phase_scan frame_cycles_ntsc={FRAME_CYCLES_NTSC}")
        for phase, path, pref in scored[: args.top]:
            print(f"phase={phase:>4} matched_prefix={pref:>8} file={path}")
        best_phase, _, best_prefix = scored[0]
        print(f"recommended_vblbar_offset_cycles={best_phase}")
        print(f"best_phase_prefix={best_prefix}")


if __name__ == "__main__":
    main()
