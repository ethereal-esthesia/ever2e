; NOP_1CYCLE_03_65C02.asm
; 1-cycle NOP probe for opcode $03 (CMD profile in current MAME-alt build)

        * = $3000
        .align 256, $00

ENTRY:
        .byte $03,$03,$03,$03,$03,$03,$03,$03,$03,$03
        .byte $03,$03,$03,$03,$03,$03,$03,$03,$03,$03
        RTS
