; NOP_SYNC_5C_0000_65C02.asm
;
; Sync-based timing compare:
;   baseline loop vs loop containing opcode bytes $5C,$00,$00
;
; ZP outputs (requested range):
;   $FB/$FC  baseline count
;   $FD/$FE  test count with $5C,$00,$00 in loop

        * = $3000
        .align 256, $00

ENTRY:
        JSR ALIGN_VBL_PHASE
        JSR MEASURE_BASELINE

        JSR ALIGN_VBL_PHASE
        JSR MEASURE_WITH_5C

        RTS

MEASURE_BASELINE:
        LDA #$00
        STA $FB
        STA $FC

WAIT_BASE_OFF:
        BIT $C019
        BMI WAIT_BASE_OFF

BASE_LOOP:
        CLC
        LDA $FB
        ADC #$01
        STA $FB
        LDA $FC
        ADC #$00
        STA $FC
        BIT $C019
        BPL BASE_LOOP
        RTS

MEASURE_WITH_5C:
        LDA #$00
        STA $FD
        STA $FE

WAIT_TEST_OFF:
        BIT $C019
        BMI WAIT_TEST_OFF

TEST_LOOP:
        CLC
        LDA $FD
        ADC #$01
        STA $FD
        LDA $FE
        ADC #$00
        STA $FE
        .byte $5C,$00,$00
        BIT $C019
        BPL TEST_LOOP
        RTS

ALIGN_VBL_PHASE:
WAIT_ON:
        BIT $C019
        BPL WAIT_ON

WAIT_OFF:
        BIT $C019
        BMI WAIT_OFF

        LDA #$07
        STA $06

RETRY_LOOP:
        JSR WAIT_17009
        BIT $C019
        BMI ALIGNED
        DEC $06
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
