# G65SC02 CMD Table IV vs JVM Cycles

Source of truth: `/Users/shane/Project/ever2e-jvm/docs/cmd_g65sc02_tableiv_header_first5.html`

- Table IV opcode entries compared: 176
- Cycle mismatches: 0
- Mnemonic label mismatches: 2

## Cycle Result

All listed Table IV opcode cycle counts match JVM base cycle counts.

## Mnemonic Label Differences (Non-cycle)

| Opcode | JVM Mnemonic | Table IV Mnemonic |
|---|---|---|
| 1A | INA | INC |
| 3A | DEA | DEC |

## Notes

- This compare intentionally excludes microcode scripts.
- Branch/decimal/page-cross conditional adders are not part of the base-cycle mismatch count above.
