; EXACT_CYCLE_DELAY_ROUTINE_65C02.inc
;
; Include-only delay routine body (no origin set here).
; This include enforces page alignment and reserves exactly one 256-byte page.
; Inputs:
;   $06 = LO (recommended 1..255 when HI>0)
;   $07 = HI (0..255)
;
; Cycles (ENTRY->RTS, RTS included):
;   HI = 0                 -> 12
;   HI > 0, LO in 1..255   -> HI*(5*LO + 7) + 10
;
; Cycles (JSR->RTS, includes caller JSR):
;   HI = 0                 -> 18
;   HI > 0, LO in 1..255   -> HI*(5*LO + 7) + 16

        .align 256, $00
DLY_PAGE_START:
DLY_DELAY2:
        LDX $07         ; 3
        BEQ DLY_DONE    ; 2/3

DLY_OUTER:
        LDY $06         ; 3
DLY_INNER:
        DEY             ; 2
        BNE DLY_INNER   ; 3 taken, 2 final
        DEX             ; 2
        BNE DLY_OUTER   ; 3 taken, 2 final
        RTS             ; 6

DLY_DONE:
        RTS             ; 6

DLY_PAGE_END:
        .assert (DLY_PAGE_END - DLY_PAGE_START) <= 256, error, "delay routine exceeds one 256-byte page"
        .res 256 - (DLY_PAGE_END - DLY_PAGE_START), $00
