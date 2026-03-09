; PROBE_DB_STP_65C02.asm
        * = $3000
        .align 256, $00
ENTRY:
        NOP
        .byte $DB
        BRK
        RTS
