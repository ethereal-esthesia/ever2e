; VBL_ALIGN_17029_65C02.asm
;
; Goal:
; 1) Poll retrace (VBL) ON->OFF with a 7-cycle taken-loop check:
;      BIT $C019   (4)
;      BMI loop    (3 taken, 2 final)
;    => 7 cycles/iteration while VBL is ON.
;
; 2) After OFF edge, wait exactly 17029 cycles and test for VBL ON again.
;    Retry up to 7 times to force phase alignment.
;
; Apple IIe NTSC frame = 17030 CPU cycles.
;
; Load address: $3000
; Entry: JSR $3000
; Return:
;   C=1 if aligned (VBL ON observed within 7 retries)
;   C=0 if not aligned after 7 retries

        * = $3000

ALIGN_VBL_PHASE:
; Optional pre-sync: ensure we start from VBL ON.
WAIT_ON:
        BIT $C019               ; 4
        BPL WAIT_ON             ; 3/2

; ON->OFF edge detector, 7 cycles per taken iteration.
WAIT_OFF:
        BIT $C019               ; 4
        BMI WAIT_OFF            ; 3 taken while ON

        LDA #$07                ; up to 7 frame-1 waits
        STA $08                 ; retry counter in ZP

RETRY_LOOP:
        JSR WAIT_17029
        BIT $C019
        BMI ALIGNED             ; saw retrace ON again -> aligned

        DEC $08
        BNE RETRY_LOOP

        CLC                    ; failed to reacquire ON within 7 tries
        RTS

ALIGNED:
        SEC                    ; aligned
        RTS

; -----------------------------------------------------------------------------
; WAIT_17029
; Exact 17029-cycle delay from entry to RTS.
;
; Built from DLY_DELAY2 plus fixed tail:
;   Preload/stores             10
;   JSR DLY_DELAY2             6
;   DLY_DELAY2 body            16676   (LO=255, HI=13)
;   Tail (LDX/DEX/BNE/BIT/NOP) 331
;   RTS                         6
; Total                     = 17029
; -----------------------------------------------------------------------------
WAIT_17029:
        LDA #$FF
        STA $06
        LDA #$0D
        STA $07
        JSR DLY_DELAY2

        LDX #$41              ; 65
W17029_TAIL_LOOP:
        DEX
        BNE W17029_TAIL_LOOP
        BIT $00
        NOP
        RTS

; Shared exact-cycle delay routine (aligns to 256-byte boundary and BRK-pads page).
.include "EXACT_CYCLE_DELAY_ROUTINE_65C02.inc"
