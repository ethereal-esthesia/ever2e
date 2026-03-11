# AWT Removal Plan (ever2e-jvm)

## Status Snapshot (2026-03-11)
- Phase 2 (clipboard/toolkit removal): complete in runtime path.
- Phase 3 (display pixel/color path): complete in runtime path.
- Phase 1 (input decoupling): complete in runtime path via internal key model (`EmuKey`).
- Phase 4 (runtime AWT-free verification): complete for active SDL runtime classes.
- Phase 5 (legacy/test cleanup): complete (`Display32x32` SDL migration + removal of AWT test harness files).
- Headless property now uses `ever2e.headless` only.
- Fullscreen hotkey behavior was intentionally simplified to `F12` only (`F11` no-op).
- `smoke` task default machine was changed to `ROMS/Apple2eNoSlots.emu`.

## Post-Migration Notes (2026-03-11)
- Fixed: PB2 default line now boots low in `MemoryBusIIe` to avoid unintended ROM self-test (`System OK`) paths at startup.
- Verified: PB1/PB2 modifier reads (`C062/C063`) sample keyboard state on each access.
- Known issue: no-slots smoke still queues `--paste-file` text but may not consume it during bounded runs (`basic_queue consumed=0`) depending on startup path/state.
- Current recommendation: use explicit Apple IIe slot/disk machine profiles for paste-loader opcode smoke flows until no-slots paste consumption is stabilized.

## Goal
Remove runtime dependency on `java.awt` from the SDL emulator path, while preserving current behavior for:
- input handling (keyboard + paste)
- display rendering
- startup/headless behavior
- fullscreen/window stability

This plan targets the active SDL runtime first. Legacy/test-only AWT classes can be removed or migrated in a later phase.

## Current AWT Usage (runtime-relevant)

### 1. `DisplayIIe`
File: `src/device/display/DisplayIIe.java`
- Status: complete for display and direct key/event imports.
- Remaining: none for display pixel pipeline (`BufferedImage`/`Color` removed).

### 2. `KeyboardIIe`
File: `src/device/keyboard/KeyboardIIe.java`
- Status: complete for runtime path.
- Complete: AWT clipboard/toolkit removed; paste now uses SDL clipboard path.
- Complete: internal `EmuKey` constants replace AWT key constants/interfaces.

### 3. Keyboard interfaces/types
Files:
- `src/device/keyboard/Keyboard.java`
- callers that provide AWT key codes/modifiers/chars
- Status: complete in runtime path.
- Complete: keyboard base no longer implements AWT `KeyListener`; SDL path feeds raw internal key values.

## Current AWT Usage (non-critical / legacy)
- None in active source sets.

---

## Migration Principles
- Keep behavior identical at each phase.
- Land in small, reversible commits.
- Avoid introducing new user flags unless required.
- Prefer adapters first, then internal type replacement.

---

## Phase 1: Introduce Non-AWT Input Types

### Objective
Decouple emulator input pipeline from AWT key constants/modifiers.

### Tasks
1. Add internal key event model (e.g. `EmuKeyEvent`):
   - logical key enum
   - modifier bitmask
   - optional text/char payload
   - repeat flag
2. Add SDL-to-internal mapper in `DisplayIIe`.
3. Add temporary AWT-to-internal mapper for any remaining callers.
4. Update keyboard ingestion methods to accept internal type instead of AWT constants.

### Acceptance
- `DisplayIIe` no longer imports `java.awt.Event` or `java.awt.event.KeyEvent`.
- Keyboard input behavior unchanged for normal typing, modifiers, and F-key shortcuts.
- Status
- Complete: `DisplayIIe` no longer directly imports AWT key/event classes.
- Complete: runtime keyboard interfaces and key constants are internalized.

---

## Phase 2: Remove AWT Clipboard Dependency

### Objective
Replace AWT clipboard access in `KeyboardIIe`.

### Tasks
1. Introduce clipboard abstraction (`ClipboardProvider`).
2. Provide SDL/platform-native implementation (or JNA-based macOS provider if needed).
3. Keep a fallback no-op/disabled provider for headless mode.
4. Route paste functionality through abstraction.

### Acceptance
- `KeyboardIIe` no longer imports AWT clipboard/toolkit classes.
- Shift+Insert paste behavior remains functional on macOS.
- Headless mode remains stable.
- Status
- Complete in runtime path.

---

## Phase 3: Remove BufferedImage/Color From Display Path

### Objective
Convert rendering pipeline to non-AWT pixel buffers.

### Tasks
1. Replace `BufferedImage` frame buffers with `int[]`/`IntBuffer` backing stores.
2. Convert palette generation from `java.awt.Color` to manual RGB math/helpers.
3. Update display update and blit code to operate directly on pixel arrays.
4. Keep output visually equivalent (color modes, mono, scanline behavior).

### Acceptance
- `DisplayIIe` no longer imports `java.awt.image.BufferedImage` or `java.awt.Color`.
- SDL frame output matches baseline screenshots/known test ROM behavior.
- Status
- Complete in runtime path.

---

## Phase 4: Remove Remaining Runtime AWT Imports

### Objective
Ensure runtime SDL emulator path is AWT-free.

### Tasks
1. Verify `src/core`, `src/device/display/DisplayIIe`, `src/device/keyboard` have no AWT imports.
2. Keep legacy AWT classes isolated or remove them if obsolete.
3. Update docs/CLI notes for clipboard/headless behavior changes.

### Acceptance
- `rg '^import java\.awt|java\.awt\.' src` returns no hits in runtime paths.
- Emulator runs windowed/fullscreen/headless without AWT dependency.
- Status
- Complete for active runtime SDL path.
- Headless mode is controlled via `ever2e.headless`.

---

## Phase 5: Cleanup Legacy/Test AWT Artifacts

### Objective
Optional cleanup after runtime migration.

### Tasks
1. Migrate/remove `Display32x32` AWT code if still needed.
2. Replace AWT-based test harnesses with SDL/headless tests.
3. Remove dead interfaces only needed for AWT bridge.

### Acceptance
- Whole repo either AWT-free, or AWT usage is explicitly test-only and documented.
- Status
- Complete: `Display32x32` migrated to SDL and AWT harness files removed.

---

## Risk Areas
- Key mapping regressions (F-keys, ctrl/cmd combos, reset shortcuts).
- Paste behavior differences by platform/security model.
- Visual color differences after removing `Color.HSBtoRGB` path.
- Timing sensitivity in startup/fullscreen flows while touching display internals.

## Suggested Validation Matrix
- 20x cold starts windowed (no `--start-fullscreen`) on macOS.
- Fullscreen enter/leave via green button and `F12` (`F11` intentionally disabled).
- Paste smoke test (`Shift+Insert`) with BASIC text.
- Headless run with `--steps` and bounded exit.
- Existing opcode/memory regression tests.

## Rollback Strategy
- Keep each phase in separate commits.
- Do not combine input + clipboard + render rewrites in one change.
- If regressions appear, revert only the affected phase commit.
