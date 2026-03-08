# Ever2e

JVM reference implementation and trace source for Apple IIe / 65C02 behavior.

## What this repo is for

- Run the JVM Apple IIe emulator from `.emu` machine configs.
- Generate per-step CPU traces.
- Compare behavior against the Python port and MAME traces.
- Experiment with soft-switch, slot ROM, and reset behavior.

## Repo layout

- `src/core/emulator/machine/machine8/Emulator8Coordinator.java`: main runner
- `src/core/cpu/cpu8/Cpu65c02.java`: 65C02 core
- `src/core/memory/memory8/MemoryBusIIe.java`: Apple IIe memory map + soft switches
- `ROMS/`: machine configs and ROM binaries

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

Windowed run (LWJGL):

```bash
./gradlew run
```

Windowed run (SDL test backend):

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
- `--keylog`
  - Enable keyboard input logging to stderr (`[lwjgl-key]` and key probe events).
- `--window-backend lwjgl|sdl`
  - Select window backend (default `lwjgl`, optional `sdl` for MAME-style fullscreen testing).
- `--start-fullscreen`
  - Start directly in fullscreen mode.
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

The 32k opcode long-run scenario now runs under `gradlew test` via
`test.cpu.Cpu32kLongRunIntegrationTest` (default halt list `0x6A45,0x6A33`).
It reads `ever2e.long32k.*` system properties (`emu`, `pasteFile`, `steps`, `haltExecution`)
and also accepts legacy `ever2e.smoke32k.*` names for compatibility.

Override example:

```bash
./gradlew smoke -PsmokeSteps=120000000 -PsmokePasteFile=/Users/shane/Project/ever2e-jvm/ROMS/opcode_smoke_loader_hgr_mem_16k.mon -PsmokeHaltExecution=0x33D2,0x33C0 -PsmokeRequireHaltPc=0x33D2
```

## Known gaps

- Expansion ROM behavior is still under active parity work.
- Full peripheral fidelity and cycle-accurate parity are incomplete.

## macOS Caps Lock popup workaround

On some macOS versions, the system Caps Lock HUD can still appear over fullscreen emulator output.
If needed, an OS-level workaround from community reports is:

```bash
sudo defaults write /Library/Preferences/FeatureFlags/Domain/UIKit.plist redesigned_text_cursor -dict-add Enabled -bool NO
```

Then reboot macOS.

To revert:

```bash
sudo defaults delete /Library/Preferences/FeatureFlags/Domain/UIKit.plist redesigned_text_cursor
```

Then reboot macOS again.
