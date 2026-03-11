# AWT Removal Plan (ever2e-jvm)

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
- `java.awt.image.BufferedImage` for frame storage/blit source
- `java.awt.Color` for palette generation
- `java.awt.event.KeyEvent` and `java.awt.Event` keycode/modifier bridge

### 2. `KeyboardIIe`
File: `src/device/keyboard/KeyboardIIe.java`
- AWT clipboard/toolkit (`Toolkit`, `Clipboard`, `DataFlavor`, etc.)
- `java.awt.event.KeyEvent`

### 3. Keyboard interfaces/types
Files:
- `src/device/keyboard/Keyboard.java`
- callers that provide AWT key codes/modifiers/chars

## Current AWT Usage (non-critical / legacy)
- `src/device/display/Display32x32.java` (AWT windowing)
- `src/test/VideoTest.java`, `src/test/KeyboardTest.java` (AWT test harness)

These should not block runtime SDL migration.

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

---

## Risk Areas
- Key mapping regressions (F-keys, ctrl/cmd combos, reset shortcuts).
- Paste behavior differences by platform/security model.
- Visual color differences after removing `Color.HSBtoRGB` path.
- Timing sensitivity in startup/fullscreen flows while touching display internals.

## Suggested Validation Matrix
- 20x cold starts windowed (no `--start-fullscreen`) on macOS.
- Fullscreen enter/leave via green button and F12.
- Paste smoke test (`Shift+Insert`) with BASIC text.
- Headless run with `--steps` and bounded exit.
- Existing opcode/memory regression tests.

## Rollback Strategy
- Keep each phase in separate commits.
- Do not combine input + clipboard + render rewrites in one change.
- If regressions appear, revert only the affected phase commit.

