; NOP_0F_TIMING_65C02.asm
;
; Measures retrace-window loop iteration counts for:
; 1) Baseline loop (no test opcode) -> ZP $0A/$0B
; 2) Test loop with $0F,$00,$00     -> ZP $0C/$0D
;
; Intended use:
; - Run loader BASIC, then RUN.
; - Read results with:
;     PRINT PEEK(10)+256*PEEK(11),PEEK(12)+256*PEEK(13),PEEK(8)
;
; If $0F acts as a 3-byte NOP, the second count should be lower than baseline.

        * = $3000
        .align 256, $00

ENTRY:
        ; Baseline measurement.
        JSR ALIGN_VBL_PHASE
        JSR MEASURE_BASELINE

        ; $0F test measurement.
        JSR ALIGN_VBL_PHASE
        JSR MEASURE_0F

        RTS

MEASURE_BASELINE:
        LDA #$00
        STA $0A
        STA $0B

WAIT_BASE_OFF:
        BIT $C019
        BMI WAIT_BASE_OFF

BASELINE_LOOP:
        CLC
        LDA $0A
        ADC #$01
        STA $0A
        LDA $0B
        ADC #$00
        STA $0B
        BIT $C019
        BPL BASELINE_LOOP
        RTS

MEASURE_0F:
        LDA #$00
        STA $0C
        STA $0D

WAIT_0F_OFF:
        BIT $C019
        BMI WAIT_0F_OFF

LOOP_0F:
        CLC
        LDA $0C
        ADC #$01
        STA $0C
        LDA $0D
        ADC #$00
        STA $0D
        .byte $0F, $00, $00
        BIT $C019
        BPL LOOP_0F
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
