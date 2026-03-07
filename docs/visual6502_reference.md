# Visual6502 Reference For CPU Microcode Work

This note captures the minimum external documentation set to drive cycle/micro-step work in Ever2e JVM.

## Scope
- Primary use: derive per-cycle instruction flow and timing-state expectations.
- Applies directly to: NMOS 6502 behavior model.
- Applies to 65C02 with caution: use as a structural baseline, then validate CMOS differences against WDC docs and hardware/MAME traces.

## Source Set (What To Use)
1. Visual6502 wiki index mirror (NESdev):
   - https://www.nesdev.org/wiki/Visual6502wiki
2. Visual6502 timing states:
   - https://www.nesdev.org/wiki/Visual6502wiki/6502_Timing_States
3. Visual6502 state machine:
   - https://www.nesdev.org/wiki/Visual6502wiki/6502_State_Machine
4. Visual6502 MOS 6502 project page:
   - https://www.nesdev.org/wiki/Visual6502wiki/MOS_6502
5. MOS/MCS6500 Appendix A single-cycle summary (historical baseline):
   - https://xotmatrix.github.io/6502/6502-single-cycle-execution.html

## Key Takeaways We Need
- Instruction timing is controlled by instruction decode + timing-state logic.
- Practical execution states include T0/T+/T2/T3/T4/T5 plus special paths (VEC0/VEC1, SD1/SD2).
- RMW family has explicit extra SD cycles (read-modify-write path), not just a flat cycle count.
- Branches and interrupt-recognition windows have special timing shortcuts/edge behavior.
- "Cycle count" and "internal timing path" are related but not interchangeable.

## Constraints / Caveats
- Visual6502 references are NMOS 6502 transistor-derived behavior.
- 65C02 differs in multiple places (documented opcodes, timing details, decimal behavior, interrupt details, some bus behavior).
- For Ever2e 65C02: use Visual6502 timing-path intuition, but treat WDC timing/semantics as authoritative for 65C02 intent.

## How To Use This In Our Implementation
1. Start with WDC opcode semantics and documented cycle counts.
2. Use Visual6502 timing-state docs to define per-cycle micro-step shape (fetch/address/read/write/stack/branch path).
3. Record each opcode-mode path in split spreadsheets:
   - `docs/cpu_6502_microcode_tracking.csv`
   - `docs/cpu_65c02_microcode_tracking.csv`
4. Flag rows that are NMOS-derived and require 65C02 validation.
5. Validate against:
   - JVM trace with `--trace-subinstructions --trace-kv`
   - MAME trace (register + totalcycles)
   - targeted hardware checks where possible

## Current State
- Opcode handler mapping in both split CSVs is cross-referenced against MAME opcode maps.
- `visual6502_timing_path` now contains handler-sequence extracts from MAME `om6502.lst` / `ow65c02.lst` (with base-handler fallback for inherited 65C02 handlers).
- `documented_cycles_wdc` is fully populated with MAME-sequence-derived values:
  - fixed cycles: `N`
  - conditional paths: `min/max`
  - non-terminating lockups: `N+`

## Immediate Priorities
- Fill control-flow/stack families not yet micro-queued (JSR/RTS/JMP/branches/interrupt sequences).
- Keep one micro-step = one CPU cycle in scheduler semantics.
- Explicitly tag pre/post trace interpretation so cycle fields are unambiguous.
