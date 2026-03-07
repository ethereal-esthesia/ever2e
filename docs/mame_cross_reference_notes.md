# MAME Cross-Reference Notes (6502 vs 65C02)

This project now keeps separate opcode tracking tables for NMOS 6502 and W65C02.

## Source Files Used
- 6502 opcode map: `/Users/shane/app/mame/src/devices/cpu/m6502/dm6502.lst`
- 65C02 opcode map: `/Users/shane/app/mame/src/devices/cpu/m6502/dw65c02.lst`
- 6502 micro-op definitions: `/Users/shane/app/mame/src/devices/cpu/m6502/om6502.lst`
- 65C02 micro-op definitions: `/Users/shane/app/mame/src/devices/cpu/m6502/ow65c02.lst`

## What Was Aligned
- Opcode-to-handler mapping is now derived from MAME tables for each CPU core.
- `mnemonic`, `address_mode`, and `notes` now reflect MAME handler identity (`mame_handler=...`).
- 6502 table now correctly includes unofficial/opcode-variant handlers present in MAME.
- 65C02 table now correctly reflects CMOS handler set and lacks NMOS unofficial opcodes.
- `visual6502_timing_path` is populated from MAME handler sequence bodies:
  - 6502: `om6502.lst`
  - 65C02: `ow65c02.lst` with fallback to `om6502.lst` for inherited handlers.
- `documented_cycles_wdc` is now populated with MAME-sequence-derived cycle counts/ranges (`min/max`) where handler scripts are available.

## Cycle Field Status
- `documented_cycles_wdc` is now fully populated in both split tables from parsed MAME sequence scripts.
- Conditional timing paths are represented as `min/max`.
- Non-terminating lockup opcodes (e.g., NMOS `KIL`) are represented as `N+` to indicate entry cycles before infinite looping.

## Accuracy Policy
- Prefer explicit `tbd` over guessed timing values.
- For 6502 timing path details: cross-check with Visual6502 + MAME om6502 handlers.
- For 65C02 timing path details: cross-check WDC docs first, then reconcile with MAME ow65c02 handlers and traces.
