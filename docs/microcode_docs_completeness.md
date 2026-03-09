# Microcode Docs Completeness

This file summarizes documentation structure and test-backed coverage status.

## Scope
- `/Users/shane/Project/ever2e-jvm/docs/cpu_65c02_microcode_tracking.csv`
- `/Users/shane/Project/ever2e-jvm/docs/nmos/cpu_6502_microcode_tracking.csv`
- `/Users/shane/Project/ever2e-jvm/docs/cmd_g65sc02_page9_nops_measured_cycles.csv`
- `/Users/shane/Project/ever2e-jvm/docs/cmd_g65sc02_microcode_profile.md`
- `/Users/shane/Project/ever2e-jvm/docs/cmd_g65sc02_microcode_matrix.csv`

## Structural Completeness
- 65C02 tracking table: 256 opcode rows, no empty columns.
- 6502 tracking table: 256 opcode rows, no empty columns.
- CMD page-9 measured NOP table: 79 opcode rows; cycle column populated for all rows.

## Status Completeness
- 65C02/CMD/WDC runtime coverage is validated by tests:
  - `cmd` non-NOP coverage: `checked=177`, `passed=177`, `failed=0`
  - `cmd` NOP coverage: `checked=79`, `passed=79`, `failed=0`
  - `wdc` non-NOP coverage: `checked=177`, `passed=177`, `failed=0`
  - `wdc` NOP coverage: `checked=79`, `passed=79`, `failed=0`
- 6502 (NMOS) docs are retained for reference only under `docs/nmos/`; NMOS CPU is not currently an implemented runtime profile.

## Notes
- `cmd_g65sc02_page9_nops_pending_cycles.csv` remains as historical "pre-measurement" capture.
- Use `cmd_g65sc02_page9_nops_measured_cycles.csv` as authoritative NOP timing input for CMD profile docs/tests.
- Use `cmd_g65sc02_microcode_profile.md` + `cmd_g65sc02_microcode_matrix.csv` as the CMD-focused microcode index (source, tests, status, cycles, bytes).
