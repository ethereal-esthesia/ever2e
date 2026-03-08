; VBL_WAIT_COUNT_65C02.asm
;
; Baseline retrace wait/count harness:
; 1) Align to a deterministic frame phase.
; 2) Wait for current VBL ON interval to end.
; 3) Count loop iterations until next VBL ON.
;
; Counter is 16-bit at ZP $0A/$0B and increments with fixed cycle cost:
;   CLC / LDA / ADC #1 / STA / LDA / ADC #0 / STA
; This avoids variable carry-branch timing.

        * = $3000
        .align 256, $00

ENTRY:
        JSR ALIGN_VBL_PHASE

        LDA #$00
        STA $0A
        STA $0B

; Start counting from a stable OFF window.
WAIT_VBL_OFF:
        BIT $C019
        BMI WAIT_VBL_OFF

COUNT_TO_VBL_ON:
        CLC
        LDA $0A
        ADC #$01
        STA $0A
        LDA $0B
        ADC #$00
        STA $0B
        BIT $C019
        BPL COUNT_TO_VBL_ON

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
