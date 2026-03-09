; NOP_1CYCLE_SYNC_03_65C02.asm
;
; Real-hardware friendly test using the same sync flow:
; 1) Align to deterministic VBL phase.
; 2) Measure baseline loop iterations in one OFF->ON window.
; 3) Re-align and measure loop iterations with opcode $03 in-loop.
; 4) Store delta = baseline - test.
;
; ZP outputs:
;   $08      align status (SEC on success, CLC on fallback after retries)
;   $0A/$0B  baseline count (lo/hi)
;   $0C/$0D  test count with $03 in loop (lo/hi)
;   $0E/$0F  delta baseline-test (lo/hi)

        * = $3000
        .align 256, $00

ENTRY:
        JSR ALIGN_VBL_PHASE
        JSR MEASURE_BASELINE

        JSR ALIGN_VBL_PHASE
        JSR MEASURE_WITH_03

        ; delta = baseline - test
        SEC
        LDA $0A
        SBC $0C
        STA $0E
        LDA $0B
        SBC $0D
        STA $0F

        RTS

MEASURE_BASELINE:
        LDA #$00
        STA $0A
        STA $0B

WAIT_BASE_OFF:
        BIT $C019
        BMI WAIT_BASE_OFF

BASE_LOOP:
        CLC
        LDA $0A
        ADC #$01
        STA $0A
        LDA $0B
        ADC #$00
        STA $0B
        BIT $C019
        BPL BASE_LOOP
        RTS

MEASURE_WITH_03:
        LDA #$00
        STA $0C
        STA $0D

WAIT_TEST_OFF:
        BIT $C019
        BMI WAIT_TEST_OFF

TEST_LOOP:
        CLC
        LDA $0C
        ADC #$01
        STA $0C
        LDA $0D
        ADC #$00
        STA $0D
        .byte $03
        BIT $C019
        BPL TEST_LOOP
        RTS

; -----------------------------------------------------------------------------
; ALIGN_VBL_PHASE
; Tries up to 7 passes, with slightly-shorter-than-frame cadence to walk phase.
; -----------------------------------------------------------------------------
ALIGN_VBL_PHASE:
WAIT_ON:
        BIT $C019
        BPL WAIT_ON

WAIT_OFF:
        BIT $C019
        BMI WAIT_OFF

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

; -----------------------------------------------------------------------------
; WAIT_17009
; Exact 17009-cycle delay from entry to RTS.
; -----------------------------------------------------------------------------
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
