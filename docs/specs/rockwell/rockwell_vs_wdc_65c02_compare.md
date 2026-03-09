# Rockwell vs WDC 65C02 Quick Comparison

## Sources
- Rockwell: [docs/specs/rockwell/Rockwell_1987_Controller_Products_Databook.pdf](/Users/shane/Project/ever2e-jvm/docs/specs/rockwell/Rockwell_1987_Controller_Products_Databook.pdf)
  - 65C02 family section is on PDF pages 12-27.
- WDC: [docs/specs/wdc/WDC_W65C02S_Datasheet_2024-02-16.pdf](/Users/shane/Project/ever2e-jvm/docs/specs/wdc/WDC_W65C02S_Datasheet_2024-02-16.pdf)

## High-confidence overlaps
- Both document CMOS 65C02-family behavior (not NMOS 6502).
- Both include core 65C02 instruction additions over NMOS 6502:
  - `BRA`, `STZ`, `TSB`, `TRB`, `PHX/PLX`, `PHY/PLY`, `(zp)` forms.
- Both document bit-branch/bit-manipulation instructions:
  - `BBR/BBS`, `RMB/SMB`.

## Key deltas relevant to emulator timing/semantics
- Opcode count:
  - Rockwell: 210 opcodes (databook page 20).
  - WDC W65C02S: 212 opcodes (WDC page 5).
- Instruction count summary:
  - WDC explicitly lists 70 instructions and includes `WAI` and `STP` in the main instruction table (WDC pages 5, 21).
  - Rockwell table focuses on the R65C00 family listing and compatibility additions (databook pages 18-20).
- Timing presentation style:
  - Rockwell matrix annotates conditional cycle adders inline (`*`, `**`, decimal adders) (databook page 20).
  - WDC provides a separate addressing-mode timing table with explicit notes for page-cross, branch-taken, and RMW cycle adders (WDC page 20).

## Implications for our emulator docs/tests
- For "what instructions exist" and modern W65C02S behavior, prefer WDC as primary.
- For historical Rockwell Apple-era compatibility interpretation, keep Rockwell section as cross-check.
- Treat NMOS Visual6502 data as structural/timing intuition only; do not assume exact CMOS parity.

## Opcode Matrix Compare
- Full per-opcode compare file:
  - [rockwell_vs_wdc_opcode_matrix.csv](/Users/shane/Project/ever2e-jvm/docs/specs/rockwell/rockwell_vs_wdc_opcode_matrix.csv)
- Summary:
  - 256 opcode slots compared.
  - Raw token differences: 84 (timing-profile marker differences, e.g. `_c_` vs `_s_`).
  - Normalized semantic differences (mnemonic/addressing identity): 0.
- Practical conclusion:
  - Rockwell R65C02 and WDC W65C02S opcode matrix semantics match for implemented slots.
  - Differences are in timing-path/dummy-fetch behavior and vendor-specific timing profile details.
