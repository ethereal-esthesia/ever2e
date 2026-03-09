; NOP_SYNC_ALL79_65C02.asm
;
; Sync-based timing compare with all 79 CMD/G65SC02 NOP opcodes encoded back-to-back
; as full instructions (including dummy operand bytes for 2/3-byte forms).
;
; ZP outputs:
;   $FB/$FC baseline count
;   $FD/$FE test count (all-79 stream)
;
; Reference (MAME-alt G65SC02, no cards):
;   baseline = 463
;   test     = 1

        * = $3000
        .align 256, $00

ENTRY:
        JSR ALIGN_VBL_PHASE
        JSR PRIME_ON_TO_OFF
        JSR MEASURE_BASELINE

        JSR ALIGN_VBL_PHASE
        JSR PRIME_ON_TO_OFF
        JSR MEASURE_WITH_ALL79

        RTS

MEASURE_BASELINE:
        LDA #$00
        STA $FB
        STA $FC

WAIT_BASE_ON:
        BIT $C019
        BPL WAIT_BASE_ON

BASE_LOOP:
        BIT $C019
        BPL BASE_DONE
        CLC
        LDA $FB
        ADC #$01
        STA $FB
        LDA $FC
        ADC #$00
        STA $FC
        JMP BASE_LOOP
BASE_DONE:
        RTS

MEASURE_WITH_ALL79:
        LDA #$00
        STA $FD
        STA $FE

WAIT_TEST_ON:
        BIT $C019
        BPL WAIT_TEST_ON

TEST_LOOP:
        BIT $C019
        BMI TEST_BODY
        JMP TEST_DONE
TEST_BODY:
        CLC
        LDA $FD
        ADC #$01
        STA $FD
        LDA $FE
        ADC #$00
        STA $FE

        .byte $02,$00,$03,$07,$00,$0B,$0F,$00,$00,$13,$17,$00
        .byte $1B,$1F,$00,$00,$22,$00,$23,$27,$00,$2B,$2F,$00
        .byte $00,$33,$37,$00,$3B,$3F,$00,$00,$42,$00,$43,$44
        .byte $00,$47,$00,$4B,$4F,$00,$00,$53,$54,$00,$57,$00
        .byte $5B,$5C,$00,$00,$5F,$00,$00,$62,$00,$63,$67,$00
        .byte $6B,$6F,$00,$00,$73,$77,$00,$7B,$7F,$00,$00,$82
        .byte $00,$83,$87,$00,$8B,$8F,$00,$00,$93,$97,$00,$9B
        .byte $9F,$00,$00,$A3,$A7,$00,$AB,$AF,$00,$00,$B3,$B7
        .byte $00,$BB,$BF,$00,$00,$C2,$00,$C3,$C7,$00,$CB,$CF
        .byte $00,$00,$D3,$D4,$00,$D7,$00,$DB,$DC,$00,$00,$DF
        .byte $00,$00,$E2,$00,$E3,$E7,$00,$EA,$EB,$EF,$00,$00
        .byte $F3,$F4,$00,$F7,$00,$FB,$FC,$00,$00,$FF,$00,$00 

        JMP TEST_LOOP
TEST_DONE:
        RTS

PRIME_ON_TO_OFF:
PRIME_WAIT_ON:
        BIT $C019
        BPL PRIME_WAIT_ON
PRIME_WAIT_OFF:
        BIT $C019
        BMI PRIME_WAIT_OFF
        RTS

ALIGN_VBL_PHASE:
; 1) Force a known starting phase: retrace OFF.
WAIT_OFF_START:
        BIT $C019
        BMI WAIT_OFF_START

; 2) Backoff loop: probe for retrace ON at deterministic cadence.
        LDA #$07
        STA $08

RETRY_LOOP:
        JSR WAIT_17009
        BIT $C019
        BMI ALIGNED
        DEC $08
        BNE RETRY_LOOP
        CLC
        RTS

ALIGNED:
        SEC
        RTS

WAIT_17009:
        LDA #$FF
        STA $06
        LDA #$0D
        STA $07
        JSR DLY_DELAY2

        LDX #$3D
W17009_TAIL_LOOP:
        DEX
        BNE W17009_TAIL_LOOP
        BIT $00
        NOP
        RTS

.include "EXACT_CYCLE_DELAY_ROUTINE_65C02.inc"
