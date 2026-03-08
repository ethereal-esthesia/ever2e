# NMOS Table V Invalid Opcodes vs JVM Opcode Table

Source: legacy Table-V invalid-opcode mapping (moved from the old `cmd_g65sc02_tablev_vs_java.*` label) and JVM `Cpu65c02.OPCODE`.

- Invalid-opcode entries compared: 87
- Mismatches: 9

| Opcode | CMD Expected | Bytes | Cycles | JVM | JVM Bytes | JVM Cycles | Result |
|---|---|---:|---:|---|---:|---:|---|
| 12 | NOP | 2 | 2 | ORA | 2 | 5 | mismatch |
| 32 | NOP | 2 | 2 | AND | 2 | 5 | mismatch |
| 52 | NOP | 2 | 2 | EOR | 2 | 5 | mismatch |
| 72 | NOP | 2 | 2 | ADC | 2 | 5 | mismatch |
| 92 | NOP | 2 | 2 | STA | 2 | 5 | mismatch |
| A2 | NOP | 2 | 2 | LDX | 2 | 2 | mismatch |
| B2 | NOP | 2 | 2 | LDA | 2 | 5 | mismatch |
| D2 | NOP | 2 | 2 | CMP | 2 | 5 | mismatch |
| F2 | NOP | 2 | 2 | SBC | 2 | 5 | mismatch |
