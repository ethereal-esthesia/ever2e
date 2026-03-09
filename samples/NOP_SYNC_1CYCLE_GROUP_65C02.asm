; NOP_SYNC_1CYCLE_GROUP_65C02.asm
;
; Sync-based timing compare for all 1-cycle CMD/G65SC02 implied NOP-family opcodes.
;
; Verified in current mame-alt G65SC02 profile: $CB and $DB retire as 1-cycle
; compatibility NOPs in this path, so they are included in the group stream.
;
; Run flow:
;   1) ./scripts/recompile_all_sample_asm.sh
;   2) ./scripts/run_mame_autoboot_bas.sh samples/generated/basic/NOP_SYNC_1CYCLE_GROUP_65C02.bas
;   3) At BASIC prompt:
;        PRINT PEEK(251)+256*PEEK(252)
;        PRINT PEEK(253)+256*PEEK(254)
;
; ZP outputs:
;   $FB/$FC baseline count
;   $FD/$FE test count (1-cycle group stream)

        * = $3000
        .align 256, $00

ENTRY:
        JSR ALIGN_VBL_PHASE
        JSR PRIME_ON_TO_OFF
        JSR MEASURE_BASELINE

        JSR ALIGN_VBL_PHASE
        JSR PRIME_ON_TO_OFF
        JSR MEASURE_WITH_GROUP

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

MEASURE_WITH_GROUP:
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

        .byte $03,$07,$0B,$0F,$13,$17,$1B,$1F,$23,$27,$2B,$2F,$33,$37,$3B,$3F
        .byte $43,$47,$4B,$4F,$53,$57,$5B,$5F,$63,$67,$6B,$6F,$73,$77,$7B,$7F
        .byte $83,$87,$8B,$8F,$93,$97,$9B,$9F,$A3,$A7,$AB,$AF,$B3,$B7,$BB,$BF
        .byte $C3,$C7,$CB,$CF,$D3,$D7,$DB,$DF,$E3,$E7,$EB,$EF,$F3,$F7,$FB,$FF 

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
WAIT_OFF_START:
        BIT $C019
        BMI WAIT_OFF_START

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
