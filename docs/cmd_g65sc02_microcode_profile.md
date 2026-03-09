# CMD G65SC02 Microcode Profile (Focused)

This is the CMD-only microcode coverage/status document.

## Scope
- CPU profile: `cmd`
- Excludes NMOS-only documentation (now under `docs/nmos/`).

## Primary Sources
- Microcode definitions:
  - `/Users/shane/Project/ever2e-jvm/src/core/cpu/cpu8/Cpu65c02Opcode.java`
  - `/Users/shane/Project/ever2e-jvm/src/core/cpu/cpu8/Cpu65c02Microcode.java`
- CMD profile opcode-table overrides (including CMD NOP mapping):
  - `/Users/shane/Project/ever2e-jvm/src/core/cpu/cpu8/Cpu65c02Cmd.java`
- Opcode bytes/cycles table (all 256 opcodes):
  - `/Users/shane/Project/ever2e-jvm/docs/g65sc02_cycle_table_from_java.csv`
- CMD measured NOP timings used as authority:
  - `/Users/shane/Project/ever2e-jvm/docs/cmd_g65sc02_page9_nops_measured_cycles.csv`

## Associated Tests
- Profile microcode/cycle coverage:
  - `/Users/shane/Project/ever2e-jvm/src/test/cpu/Cpu65c02ProfileMicrocodeCoverageTest.java`
  - CMD non-NOP coverage test: `cmdProfileAllNonNopOpcodesMatchMicrocodeCycleCount`
  - CMD strict runtime micro-queue test: `cmdProfileAllNonNopsAreRuntimeMicroQueued`
  - CMD NOP coverage test: `cmdProfileNopOpcodesMatchMicrocodeCycleCount`
- Microcode enum/script integrity:
  - `/Users/shane/Project/ever2e-jvm/src/test/cpu/Cpu65c02MicrocodeTest.java`
- CMD measured NOP timing/width parity:
  - `/Users/shane/Project/ever2e-jvm/src/test/cpu/Cpu65c02CycleTimingTest.java`
  - `cmdProfileUsesHardwareValidatedNopTimingAndLength`
  - `cmdProfileAllMeasuredNopCyclesMatchHardwareTable`
  - `cmdProfileMeasuredCompatibilityNopWidthsMatchMameTable`

## Current Status (CMD)
- Total opcodes: `256`
- Non-NOP opcodes: `177` (cycle-coverage tests pass)
- Non-NOP runtime micro-queue coverage: `177/177` (strict queueing test passes)
- NOP opcodes: `79` (measured timing/width table tests pass)
- Overall status: `PASS` for CMD profile microcode-cycle parity checks.

## Explicit Family Buckets (Progress)
- `implemented`: `lda`, `sta`, `inc`, `dec`, `asl`, `lsr`, `rol`, `ror`, `ora`, `and`, `eor`, `adc`, `sbc`, `cmp`, `bit`, `ldx`, `ldy`, `stx`, `sty`, `cpx`, `cpy`, `jsr`, `branch`, `control_misc`, `stack`, `flags`, `jump`, `bit_test_set`, `transfer`, `index_incdec`, `stz`, `interrupt_control_flow`
- `note`: Some buckets intentionally overlap (for example `jump` includes `JSR`; `bit_test_set` includes `BIT/TSB/TRB`) to keep reporting and rollout by intent.

## Focused Matrix (Source + Tests + Status + Cycles + Bytes)
- Full per-opcode matrix:
  - `/Users/shane/Project/ever2e-jvm/docs/cmd_g65sc02_microcode_matrix.csv`
- Columns:
  - `opcode_hex, mnemonic, address_mode, bytes, base_cycles, microcode_source, associated_tests, status`

This matrix is the single CMD-focused index that ties each opcode’s bytes/cycles to its microcode source and validating tests.
