# Ever2e

JVM reference implementation and trace source for Apple IIe / CMD G65SC02 behavior.

## What this repo is for

- Emulate the beloved classic Apple IIe Platinum.
- Run the JVM Apple IIe emulator from `.emu` machine configs.
- Generate per-step CPU traces.
- Experiment with soft-switch, slot ROM, and reset behavior.

## Repo layout

- `src/core/`
  - Core emulator logic (CPU, memory bus, machine coordination).
  - Key files:
    - `src/core/emulator/machine/machine8/Emulator8Coordinator.java` (main run loop)
    - `src/core/cpu/cpu8/Cpu65c02.java` (65C02 core)
    - `src/core/memory/memory8/MemoryBusIIe.java` (IIe memory map + soft switches)
- `src/device/`
  - Host-facing devices (display, keyboard, speaker).
- `src/peripherals/`
  - Peripheral emulation (including 5.25" floppy support).
- `src/test/`
  - Unit/integration tests for CPU and device behavior.
- `src/tools/`
  - Utility code used by emulator tooling.
- `ROMS/`
  - Machine `.emu` configs and ROM binaries used at runtime.
- `DISKS/`
  - Disk images used for boot/test workflows.
- `samples/`
  - Sample assembly sources and generated loader outputs.
- `scripts/`
  - Trace, MAME comparison, and sample-build helper scripts.
- `docs/`
  - Datasheets, reverse-engineering notes, opcode/cycle analysis, and references.
- `gradle/`, `build.gradle`, `settings.gradle`
  - Build system wrapper and Gradle project config.
- `target/` and `out/`
  - Build/test output directories.

## Build

```bash
cd /Users/shane/Project/ever2e-jvm
./gradlew classes
```

## Run

Default machine:

```bash
cd /Users/shane/Project/ever2e-jvm
./gradlew runHeadless
```

Specific `.emu` file:

```bash
./gradlew runHeadless --args="ROMS/Apple2e.emu"
```

Windowed run (SDL):

```bash
./gradlew run
```

Windowed run (alias task):

```bash
./gradlew runSdl
```

## CLI args

- `--steps N`
  - Max CPU steps to execute (required for bounded trace runs).
- `--trace-file <path>`
  - Write CPU trace CSV.
- `--trace-phase pre|post`
  - Snapshot phase for instruction rows.
- `--post`
  - Shortcut for `--trace-phase post`.
- `--text-console`
  - Run with console text display (no GUI window).
- `--print-text-at-exit`
  - Print the active 40x24 text page on exit (headless/text-console useful).
  - Works without `--debug` (text dump remains visible even in quiet mode).
- `--dump-mapped <start:end>`
  - Dump mapped memory range using current softswitch/bank view (no side effects).
  - Example: `--dump-mapped 0xC000:0xC0FF`
- `--dump-all-64k`
  - Dump full mapped 64K view (`0x0000..0xFFFF`).
  - Aliases: `--dump-all`, `--dump-all-mapped`, `--dump-64k`
- `--dump-unmapped`
  - Dump full raw backing memory (`0x00000..0x1FFFF`) independent of softswitch mapping.
  - Aliases: `--dump-all-raw-ram-rom`, `--dump-all-raw-ram`, `--dump-all-128k`
- `--show-fps`
  - Print windowed display FPS once per second to stderr.
- `--trace-start-pc <addr>`
  - Do not emit trace rows until this PC is reached (inclusive), then continue tracing normally.
- `--reset-pflag-value <value>`
  - Override reset-time `P` policy (hex `0x..` or decimal). Bits `0x20` and `0x10` stay asserted.
- `--monitor-seq-write <addr:byte[,addr:byte...]>`
  - Apply monitor-style startup memory writes before execution. Useful for parity tests that need fixed bytes
    (for example `--monitor-seq-write 0x01FD:0xFF,0x01FF:0xFF`).
- `--floating-bus-phase-cycles <n>`
  - Override headless floating-bus phase offset (startup tracer pre-advance cycles, default `0`). Useful for MAME timing alignment sweeps.
- `--vblbar-offset-cycles <n>`
  - Alias for VBLBAR/read-phase offset tuning in headless mode (same startup tracer phase control as `--floating-bus-phase-cycles`, default `0`).
- `--display-phase-cycles <n>`
  - Alias for startup display beam phase offset in headless mode (same phase control as `--floating-bus-phase-cycles`/`--vblbar-offset-cycles`, default `0`).
- `--cpu-profile cmd|wdc`
  - Select CPU opcode/timing profile. Default is `cmd` (G65SC02-compatible wrapper); use `wdc` for W65C02 behavior.
- `--halt-execution <addr[,addr...]>`
  - Stop execution when PC reaches any provided address (hex `0x....` or decimal). May be repeated.
- `--paste-file <path>`
  - Queue BASIC source text into the keyboard input queue at startup (same CR conversion as paste).
- `--no-sound`
  - Disable speaker initialization and run without audio output.
- `--debug`
  - Enable emulator stdout logging (logging is quiet by default).
- `--no-logging`
  - Force quiet mode (kept for compatibility).
- `--keylog` / `--key-log`
  - Enable keyboard input logging to stderr (`[sdl-key]` and key probe events).
- `--debug-mouse`
  - Enable SDL mouse/cursor logging to stderr (`[debug] sdl_cursor ... x=.. y=..`).
- `--start-fullscreen`
  - Start directly in fullscreen mode.
- `--mac-allow-process-switching`
  - macOS fullscreen opt-out for process-switch lock.
  - By default, fullscreen disables process switching (Cmd+Tab) while focused.
  - Pass this flag to allow normal process switching even in fullscreen.
- `--text-input-mode off|offscreen|normal|center`
  - SDL text input behavior (`off` disables host text input, `offscreen` requests text input but moves caret area offscreen, `center` places caret area at window center, `normal` uses default text input area).
- `--sdl-fullscreen-mode exclusive|desktop`
  - SDL fullscreen style (`exclusive` uses a display mode, `desktop` uses borderless desktop fullscreen mode).

Startup behavior:
- With sound enabled, the emulator performs an internal silent JIT-prime pass (300000 steps) on the same object graph before normal logging begins, to reduce startup audio jitter.

## Keyboard shortcuts

- `Insert`
  - Clear queued keyboard input.
- `Shift+Insert`
  - Paste clipboard text into queued keyboard input.
- `Ctrl+F12`
  - Trigger reset interrupt sequence.
- `Ctrl+F11`
  - Ignored in-app (to avoid accidental non-reset actions when users intend `Ctrl+F12`).
  - Note: macOS often captures `F11` globally (Show Desktop), so the app may not receive it at all.
- `Ctrl+Cmd+F` or `Cmd+Enter`
  - Toggle fullscreen window mode (macOS).

## Trace output format

When `--trace-file` is used:

- Header:
  - `step,event_type,event,pc,opcode,a,x,y,p,s,mnemonic,mode`
- Reset row:
  - `event_type=event`, `event=RESET`
- Instruction rows:
  - `event_type=instr`, `event=` (empty)

Notes:

- `RESET` is a trace event, not a fetched opcode.
- Trace phase defaults to `pre`.
- Use `--trace-phase post` (or `--post`) for post-step rows.

## Examples

Sample tooling workflow:

```bash
cd /Users/shane/Project/ever2e-jvm
./scripts/recompile_all_sample_asm.sh
```

- Rebuilds all `.asm` files under `samples/` and regenerates:
  - `samples/generated/bin/*.bin`
  - `samples/generated/basic/*.bas`

Run any generated BASIC loader in MAME-alt (autotype + `RUN`):

```bash
./scripts/run_mame_autoboot_bas.sh samples/generated/basic/<NAME>.bas
```

Verify a sample by loading its BASIC and dumping memory for binary compare:

```bash
./scripts/run_mame_verify_asm_load.sh --asm samples/<NAME>.asm
```

## Script helpers

- `scripts/recompile_all_sample_asm.sh`
  - Rebuild all `samples/*.asm` and regenerate matching `samples/generated/bin/*.bin` and `samples/generated/basic/*.bas`.
- `scripts/run_mame_autoboot_bas.sh <file.bas> [mame args...]`
  - Launch MAME-alt, normalize BASIC text input (`CR/LF` + control-char cleanup), type it, and execute `RUN`.
  - Defaults to cardless slots (`-sl1 ""` through `-sl7 ""`).
- `scripts/run_mame_verify_asm_load.sh --asm samples/<NAME>.asm [options]`
  - End-to-end sample verifier: checks generated artifacts, launches via `run_mame_autoboot_bas.sh`, captures memory dump, and compares dump vs expected `.bin`.
  - Uses the same centralized BASIC input normalization path as `run_mame_autoboot_bas.sh`.
- `scripts/compare_mame_dump_to_bin.sh --expected <bin> --dump <dump> [--base <addr>]`
  - Byte-compare helper used by the verify script.
- `scripts/derive_mame_offsets.py`
  - Utility for deriving alignment offsets from trace/cycle data.
- `scripts/summarize_mame_trace_deltas.py`
  - Utility to summarize per-row/per-field differences across trace outputs.

Generate a pre-phase trace:

```bash
./gradlew runHeadless --args="ROMS/Apple2eMemCheck.emu --steps 5000 --trace-file /tmp/jvm_trace.csv"
```

Generate a post-phase trace:

```bash
./gradlew runHeadless --args="ROMS/Apple2eMemCheck.emu --steps 5000 --trace-file /tmp/jvm_trace_post.csv --trace-phase post"
```

Generate a trace that starts only at a target PC:

```bash
./gradlew runHeadless --args="ROMS/Apple2e.emu --steps 200000 --trace-file /tmp/jvm_trace_basic.csv --trace-phase pre --trace-start-pc 0xE000"
```

Run with reset `P` policy and halt point:

```bash
./gradlew runHeadless --args="ROMS/Apple2eMemCheck.emu --steps 200000 --trace-phase pre --reset-pflag-value 0x36 --halt-execution 0xC70B,0xC70C --trace-file /tmp/jvm_halt_trace.csv"
```

Queue a BASIC program file for typed input:

```bash
./gradlew runHeadless --args="ROMS/Apple2e.emu --steps 200000 --paste-file samples/VBL_TEST.BAS"
```

Queue the committed 16k paste-loader check and execute:

```bash
./gradlew runHeadless --args="ROMS/Apple2e.emu --steps 80000000 --text-console --paste-file /Users/shane/Project/ever2e-jvm/ROMS/opcode_smoke_loader_hgr_mem_16k.mon --halt-execution 0x33D2,0x33C0 --require-halt-pc 0x33D2 --print-text-at-exit --no-sound"
```

Force memtest long-run success checkpoint (`System OK` text + stable PC):

```bash
./gradlew runHeadless --args="ROMS/Apple2eForceMemtest.emu --steps 20000000 --halt-execution 0xC79D --require-halt-pc 0xC79D --print-text-at-exit --no-sound"
```

One-command smoke task:

```bash
./gradlew smoke
```

Defaults used by `smoke`:
- `smokeEmuFile=ROMS/Apple2e.emu`
- `smokeSteps=250000`
- `smokePasteFile` is optional (unset by default)
- `smokeHaltExecution` is optional (unset by default)
- `smokeRequireHaltPc` is optional (unset by default)
- `--no-sound` is enabled by default for silent headless smoke runs

The 32k opcode long-run CMD scenario now runs under `gradlew test` via
`test.cpu.Cpu32kLongRunCmdIntegrationTest` (default halt list `0x6A45,0x6A33`).
It reads `ever2e.long32k.*` system properties (`emu`, `pasteFile`, `steps`, `haltExecution`)
and also accepts legacy `ever2e.smoke32k.*` names for compatibility.

Override example:

```bash
./gradlew smoke -PsmokeSteps=120000000 -PsmokePasteFile=/Users/shane/Project/ever2e-jvm/ROMS/opcode_smoke_loader_hgr_mem_16k.mon -PsmokeHaltExecution=0x33D2,0x33C0 -PsmokeRequireHaltPc=0x33D2
```

## Known gaps

- Revamping sound routines
- The only card currently supported outside of the stock 64K expansion board is a virtualized 5.25" Floppy Controller Card
