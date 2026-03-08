; EXACT_CYCLE_DELAY_ROUTINE_65C02.asm
;
; Parent wrapper for include-based delay routine.
; - Include file owns alignment/page padding policy.
;
; Load address default: $3000
; Call entry label: DLY_DELAY2

        * = $3000
        .include "EXACT_CYCLE_DELAY_ROUTINE_65C02.inc"
