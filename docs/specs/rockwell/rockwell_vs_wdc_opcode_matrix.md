# Rockwell vs WDC Opcode Matrix Compare

Sources used for machine-readable matrix generation:
- `/Users/shane/app/mame/src/devices/cpu/m6502/dr65c02.lst` (Rockwell R65C02 variant)
- `/Users/shane/app/mame/src/devices/cpu/m6502/dw65c02s.lst` (WDC W65C02S variant)

These were used to build a 256-opcode side-by-side matrix in:
- `/Users/shane/Project/ever2e-jvm/docs/specs/rockwell/rockwell_vs_wdc_opcode_matrix.csv`

## Summary
- Total opcode slots compared: 256
- Raw token differences: 84
- Normalized mnemonic/addressing differences: 0

Normalization removes variant timing markers (`_c_` vs `_s_`) and keeps opcode semantic identity.

Conclusion: opcode semantics/matrix layout match; differences are timing-profile implementation details, not opcode presence/absence.
