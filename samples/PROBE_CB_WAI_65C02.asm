; PROBE_CB_WAI_65C02.asm
        * = $3000
        .align 256, $00
ENTRY:
        NOP
        .byte $CB
        BRK
        RTS
