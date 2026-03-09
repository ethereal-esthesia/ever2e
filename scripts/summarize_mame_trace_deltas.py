#!/usr/bin/env python3
"""
Summarize instruction deltas from a MAME trace file.

Input format expected (pairs of lines):
  PC=3002 TC=009084F6
  3002: nop #$02
"""

from __future__ import annotations

import argparse
import csv
import re
from collections import defaultdict
from pathlib import Path


PC_RE = re.compile(r"^PC=([0-9A-Fa-f]{4})\s+TC=([0-9A-Fa-f]{8})$")
DIS_RE = re.compile(r"^([0-9A-Fa-f]{4}):\s*(.*)$")


def parse_trace(path: Path) -> list[dict]:
    rows = []
    cur_pc = None
    cur_tc = None
    for line in path.read_text().splitlines():
        m = PC_RE.match(line.strip())
        if m:
            cur_pc = int(m.group(1), 16)
            cur_tc = int(m.group(2), 16)
            continue
        m = DIS_RE.match(line.strip())
        if m and cur_pc is not None and cur_tc is not None:
            dis_pc = int(m.group(1), 16)
            if dis_pc != cur_pc:
                continue
            disasm = m.group(2).strip()
            mnemonic = disasm.split()[0].upper() if disasm else "?"
            rows.append(
                {
                    "pc": cur_pc,
                    "tc": cur_tc,
                    "disasm": disasm,
                    "mnemonic": mnemonic,
                }
            )
            cur_pc = None
            cur_tc = None
    return rows


def add_deltas(rows: list[dict]) -> list[dict]:
    out = []
    for i in range(len(rows) - 1):
        cur = rows[i]
        nxt = rows[i + 1]
        delta_tc = nxt["tc"] - cur["tc"]
        delta_pc = (nxt["pc"] - cur["pc"]) & 0xFFFF
        out.append(
            {
                "pc": f"{cur['pc']:04X}",
                "next_pc": f"{nxt['pc']:04X}",
                "tc": f"{cur['tc']:08X}",
                "next_tc": f"{nxt['tc']:08X}",
                "cycles": delta_tc,
                "length": delta_pc,
                "mnemonic": cur["mnemonic"],
                "disasm": cur["disasm"],
            }
        )
    return out


def write_rows_csv(path: Path, rows: list[dict]) -> None:
    if not rows:
        path.write_text("")
        return
    with path.open("w", newline="") as f:
        w = csv.DictWriter(
            f,
            fieldnames=[
                "pc",
                "next_pc",
                "tc",
                "next_tc",
                "cycles",
                "length",
                "mnemonic",
                "disasm",
            ],
        )
        w.writeheader()
        w.writerows(rows)


def summarize(rows: list[dict]) -> list[dict]:
    stats: dict[str, dict[str, int]] = defaultdict(lambda: defaultdict(int))
    for r in rows:
        key = r["disasm"]
        stats[key]["count"] += 1
        stats[key]["sum_cycles"] += int(r["cycles"])
        stats[key]["sum_length"] += int(r["length"])
        stats[key][f"cycles_{r['cycles']}"] += 1
        stats[key][f"length_{r['length']}"] += 1
    summary = []
    for disasm, s in sorted(stats.items(), key=lambda kv: (-kv[1]["count"], kv[0])):
        count = s["count"]
        avg_cycles = s["sum_cycles"] / count
        avg_len = s["sum_length"] / count
        cycle_buckets = ",".join(
            f"{k.split('_',1)[1]}:{v}"
            for k, v in sorted(s.items())
            if k.startswith("cycles_")
        )
        len_buckets = ",".join(
            f"{k.split('_',1)[1]}:{v}"
            for k, v in sorted(s.items())
            if k.startswith("length_")
        )
        summary.append(
            {
                "disasm": disasm,
                "count": count,
                "avg_cycles": f"{avg_cycles:.3f}",
                "avg_length": f"{avg_len:.3f}",
                "cycles_dist": cycle_buckets,
                "length_dist": len_buckets,
            }
        )
    return summary


def write_summary_csv(path: Path, rows: list[dict]) -> None:
    with path.open("w", newline="") as f:
        w = csv.DictWriter(
            f,
            fieldnames=["disasm", "count", "avg_cycles", "avg_length", "cycles_dist", "length_dist"],
        )
        w.writeheader()
        w.writerows(rows)


def main() -> None:
    ap = argparse.ArgumentParser(description="Summarize MAME trace instruction deltas.")
    ap.add_argument("--trace", required=True, help="Input trace path")
    ap.add_argument(
        "--rows-out",
        default="",
        help="Optional per-row CSV output (default: <trace>.rows.csv)",
    )
    ap.add_argument(
        "--summary-out",
        default="",
        help="Optional summary CSV output (default: <trace>.summary.csv)",
    )
    args = ap.parse_args()

    trace_path = Path(args.trace)
    rows_out = Path(args.rows_out) if args.rows_out else trace_path.with_suffix(trace_path.suffix + ".rows.csv")
    summary_out = Path(args.summary_out) if args.summary_out else trace_path.with_suffix(
        trace_path.suffix + ".summary.csv"
    )

    parsed = parse_trace(trace_path)
    deltas = add_deltas(parsed)
    summary_rows = summarize(deltas)
    write_rows_csv(rows_out, deltas)
    write_summary_csv(summary_out, summary_rows)

    print(f"parsed instructions: {len(parsed)}")
    print(f"delta rows: {len(deltas)}")
    print(f"wrote rows: {rows_out}")
    print(f"wrote summary: {summary_out}")


if __name__ == "__main__":
    main()

