# G65SC02 vs Current JVM Opcode Table

Source compare: JVM `Cpu65c02.OPCODE` vs MAME WDC `dw65c02s.lst` (default microcode baseline).

- Opcode slots compared: 256
- Mnemonic mismatch candidates: 36

| Opcode | JVM | WDC Token | WDC Guess |
|---|---|---|---|
| 07 | NOP | rmb_bzp | RMB |
| 0F | NOP | bbr_zpb | BBR |
| 17 | NOP | rmb_bzp | RMB |
| 1A | INA | inc_acc | INC |
| 1F | NOP | bbr_zpb | BBR |
| 27 | NOP | rmb_bzp | RMB |
| 2F | NOP | bbr_zpb | BBR |
| 37 | NOP | rmb_bzp | RMB |
| 3A | DEA | dec_acc | DEC |
| 3F | NOP | bbr_zpb | BBR |
| 47 | NOP | rmb_bzp | RMB |
| 4F | NOP | bbr_zpb | BBR |
| 57 | NOP | rmb_bzp | RMB |
| 5F | NOP | bbr_zpb | BBR |
| 67 | NOP | rmb_bzp | RMB |
| 6F | NOP | bbr_zpb | BBR |
| 77 | NOP | rmb_bzp | RMB |
| 7F | NOP | bbr_zpb | BBR |
| 87 | NOP | smb_bzp | SMB |
| 8F | NOP | bbs_zpb | BBS |
| 97 | NOP | smb_bzp | SMB |
| 9F | NOP | bbs_zpb | BBS |
| A7 | NOP | smb_bzp | SMB |
| AF | NOP | bbs_zpb | BBS |
| B7 | NOP | smb_bzp | SMB |
| BF | NOP | bbs_zpb | BBS |
| C7 | NOP | smb_bzp | SMB |
| CB | NOP | wai_imp | WAI |
| CF | NOP | bbs_zpb | BBS |
| D7 | NOP | smb_bzp | SMB |
| DB | NOP | stp_imp | STP |
| DF | NOP | bbs_zpb | BBS |
| E7 | NOP | smb_bzp | SMB |
| EF | NOP | bbs_zpb | BBS |
| F7 | NOP | smb_bzp | SMB |
| FF | NOP | bbs_zpb | BBS |
