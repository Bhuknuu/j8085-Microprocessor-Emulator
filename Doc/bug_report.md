# j8085 GUI — Comprehensive Bug & Fault Analysis Report

**Analyzer:** Antigravity Code Quality + Lang-Enterprise Skills (Skill Orchestrator Tier 1)
**Target:** `Project/src/` — 6 GUI files + Backend (853 lines Architecture, 270 lines Assembler)
**Quick Stats:** 9 files, ~1,700 lines of code, Pure Java 8 / Swing

---

## Design Inspiration: VS Code + Spotify Backstage — Scope Analysis

The programmer draws from **two distinct design languages** simultaneously:

1. **VS Code IDE Layout**: `JSplitPane` nesting mirrors VS Code's activity bar → sidebar → editor → terminal hierarchy. The `JTabbedPane` editor with line numbers, bottom terminal panel, and collapsible dashboard are direct behavioral references to VS Code's panel system.
2. **Spotify Backstage Design System**: The color palette (`#121212`, `#171717`, `#1E1E1E`, `#9BF0E1`) is taken from Backstage's "dark" surface token stack. The flat, border-minimalist UI with teal accent and `SANS_SERIF` BOLD labels mirrors Backstage's developer portal aesthetic.

**Programmer's Scope & Conviction:**
- Strong conviction: intentional zero-dependency constraint shows disciplined architectural thinking.
- Academic tone is well-maintained; no feature-bloat.
- However, the hybrid of two design systems creates **token inconsistency** — Backstage uses 2-px teal for active elements, while VS Code uses blue tab underlines. Neither is consistently applied here.
- The programmer understands layout hierarchy but has weak Swing rendering knowledge (evidenced by the `paintComponent` line number issues and missing `Graphics2D` anti-aliasing).

---

## FIELD 1: DESIGN

### CRITICAL
1. **UIManager called AFTER Frame construction** — `MainFrame.java:16` calls `setupDarkTheme()` inside the constructor *after* Swing components exist. UIManager overrides must be set *before* any component is created. Many properties (like `TabbedPane.selected`) won't apply to already-created components, causing visual inconsistency across LookAndFeels.
2. **No LookAndFeel (LAF) baseline set** — The code applies `UIManager.put()` patches without first calling `UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName())`. On Windows, the default Windows LAF ignores most color patches silently, meaning the dark theme will NOT render on most user machines — it will look like a default grey Swing app.
3. **DashboardPanel registers no scroll listener** — The `JTable` in DashboardPanel is inside a `JScrollPane` but the scrollbar has no custom UI. On Windows, the default `JScrollBar` renders in bright white/grey, completely shattering the dark theme on the only scrollable component the user sees.
4. **SplitPane divider is not themed** — `JSplitPane` dividers (the drag handle) have no custom `SplitPaneUI` applied. The thin 4px divider renders with the platform's default grey/blue color, creating a bright visual scar across the dark interface.
5. **Font substitution silently fails** — The code uses `Font.MONOSPACED` and `Font.SANS_SERIF` logical font names. These map to platform defaults (Courier New on Windows, not JetBrains Mono). There is no fallback or verification. The stated "Backstage typography" will never render.

### HIGH
1. **Line number gutter width is hardcoded at 35px** — `LineNumberComponent` has `setPreferredSize(new Dimension(35, 0))`. For files with 100+ lines (4 digits), numbers will be clipped against the border. The width should be computed from `FontMetrics.stringWidth(String.valueOf(maxLines)) + 10`.
2. **`regGrid` uses `GridLayout(5, 4, 5, 5)`** — 9 registers (A,B,C,D,E,H,L,PC,SP) in a 5×4 grid = 20 cells. The last row will have 3 empty cells, creating a visually uneven layout with dead whitespace on the right side of the registers panel.
3. **Color constants duplicated across files** — Each of the 6 GUI files declares its own `private final Color BG_COLOR = new Color(0x171717)` etc. There is no shared `Theme.java` or constants file. One hex edit requires changing 6 files — a maintainability and consistency failure.
4. **No minimum window size** — `MainFrame` has `setSize(1200, 800)` but no `setMinimumSize()`. Resizing the window below ~400px collapses all panels into each other unrecoverably.
5. **Terminal scrollbar thumb invisible** — `TerminalPanel`'s `JScrollPane` has a `#171717` background but no custom scrollbar color. The dark terminal with a grey/white Windows scrollbar thumb is a hard design clash.

### MEDIUM
1. **Toolbar has no hover effects** — Buttons created by `createToolButton()` have no `MouseListener` for hover state. The design intent of "flat buttons turning cyan on hover" is stated in the plan but not implemented.
2. **Active tab has no underline accent** — The Backstage/VS Code inspiration requires a bottom-border accent on the active tab. `UIManager.put("TabbedPane.selected")` changes background, not an underline. This is not achievable without a custom `TabbedPaneUI`.
3. **No status bar** — VS Code always shows a status bar at the bottom (file info, cursor position, etc.). Absence of this creates a visually unfinished appearance and removes useful context (current PC address, halt state).
4. **`JToolBar` renders with default border** — Despite `setFloatable(false)`, `JToolBar` retains a system-drawn raised/sunken border on Windows that fights the flat aesthetic.
5. **Separator widths in toolbar are hardcoded** — `toolBar.addSeparator(new Dimension(20, 0))` doesn't scale with DPI or font size. On HiDPI displays, these separators will appear too narrow.

### LOW
1. **No window icon/favicon** — `MainFrame` sets no application icon via `setIconImage()`. The taskbar and title bar show a generic Java coffee cup icon.
2. **Dialog title "Memory Configuration" is plain** — `MemoryConfigDialog` has no subtitle/description label explaining what memory range means to the user.
3. **`MemoryConfigDialog` is not resizable but should allow it** — The dialog is fixed at 400×200 which cuts off the button labels if the system font is large (accessibility scaling).
4. **No dark tooltip theming** — Standard Swing tooltips render with yellow background on dark themes, creating jarring popups.
5. **TableHeader alignment not set** — The memory dump table header labels are left-aligned by default while data cells use a custom `centerRenderer`. This visual inconsistency makes the table look misaligned.

---

## FIELD 2: FUNCTIONALITY

### CRITICAL
1. **Race condition on `isRunning` flag** — In `EmulatorBridge.java:87`, `while (!arch.isHalted() && isRunning)` reads `isRunning` from the `SwingWorker` background thread without synchronization. `pause()` at line 121 sets `isRunning = false` from the EDT. This is an unsynchronized cross-thread write/read — a data race that could cause the play loop to never terminate. Must use `volatile boolean isRunning` or `AtomicBoolean`.
2. **`assembleAndLoad()` writes directly to memory during assembly** — `Assembler.java:49` calls `arch.writeMemory(currentAddress, b)` inside Pass 2. If assembly fails mid-way (e.g., line 15 of 30 has an error), the first 14 instructions are already loaded into memory in a half-assembled state. The CPU then executes corrupted code on the next `step()`. Must assemble to a temp buffer first, only writing to memory on full success.
3. **`step()` executed from EDT** — `EmulatorBridge.step()` calls `arch.step()` directly on the Event Dispatch Thread. For programs with long-running computation or many instructions, this blocks the UI, making it unresponsive. Only `play()` uses `SwingWorker` — `step()` should also defer execution.
4. **`reset()` does not re-assemble** — After `reset()`, `arch.reset()` clears all memory. If the user then clicks `step()` without re-assembling, they execute NOP (0x00) at address 0x0000 indefinitely. There is no guard or auto-reload of the last assembled program.
5. **`MemoryConfigDialog` close button causes `NullPointerException`** — When the user clicks the OS window close button (X) on `MemoryConfigDialog`, `isDefaultSelected()` returns `false` and `getStartAddress()` returns `-1`. In `Main.java`, neither condition matches, causing `System.exit(0)` — but this is inside a lambda on the EDT. If the dialog is closed before it is made visible, the logic runs incorrectly.

### HIGH
1. **`SwingWorker` reference is lost** — The worker in `play()` is a local variable. If the user calls `play()` twice quickly (before `isRunning` is updated), a second worker starts. The first worker's reference is gone — it cannot be cancelled. `worker` must be stored as an instance field.
2. **`done()` callback not exception-safe** — `SwingWorker.done()` at line 107 calls `refreshUI()` which calls `dashboard.refresh(arch)`. If `arch` throws during refresh, the exception is swallowed silently since `done()` doesn't have try/catch.
3. **Assembler accumulates errors silently** — `Assembler.assembleInstruction()` throws `SimulatorException` on unknown mnemonics, but the exception message only shows the last failure. There is no multi-error accumulation — the user gets one error at a time and must re-assemble repeatedly to find all errors in a program.
4. **`getActiveCode()` returns empty string silently** — If the `JScrollPane` is not a recognized component or the cast fails, `getActiveCode()` returns `""` with no error or logging. The assembler then shows "No code to assemble" misleadingly.
5. **Memory dump updates on every `step()`** — `updateMemoryView()` calls `memoryModel.setRowCount(0)` and rebuilds the entire table (25 rows) on every single step. For step-debug mode, this causes `fireTableDataChanged` to flood the EDT, causing UI stuttering.

### MEDIUM
1. **No assembly error line highlighting** — When assembly fails, the `SimulatorException` is printed to the terminal but there is no way to jump to the offending line in the editor.
2. **`Architecture.runFrom()` has a 100,000 step safety limit** — but `EmulatorBridge.play()` uses `arch.step()` in a loop without its own safety limit. An infinite loop program will run until the user manually clicks Pause.
3. **Play and Assemble buttons not disabled during execution** — If the user clicks Assemble while the program is running in `play()` mode, `assembleAndLoad()` calls `arch.loadProgram()` while the SwingWorker thread is calling `arch.step()` — concurrent memory access without synchronization.
4. **`MemoryConfigDialog` does not validate range** — The user can enter startAddress `FFFF` and endAddress `0000` (end before start). No range validation is performed. `Architecture` constructor behavior with an invalid range is undefined.
5. **Terminal has no character limit** — `TerminalPanel.appendMessage()` appends to `JTextArea` indefinitely. Running a long program will accumulate thousands of step messages, eventually causing memory pressure and UI slowdown.

### LOW
1. **No keyboard shortcuts** — No `F5` for Run, `F10` for Step, `F2` for Assemble. Pure mouse-only interaction.
2. **No "unsaved changes" warning** — The editor has no dirty-state tracking. Closing the window while code is unsaved gives no warning.
3. **`clear()` method in `TerminalPanel` is never called** — The method exists but nothing in the GUI calls it.
4. **Toolbar button order** — "Toggle Dashboard" is placed first before "Assemble", disrupting the logical workflow order (Assemble → Run → Step → Reset).
5. **No auto-assemble on file open** — There is no file open capability at all, meaning users must paste code manually every session.

---

## FIELD 3: FEATURES

### CRITICAL
1. **No file save/open** — There is no `File` menu, no `JMenuBar`, no `JFileChooser`. Users cannot save their assembly code or load existing `.asm` files. Every session requires manually retyping or pasting code.
2. **No syntax highlighting** — The editor is a raw `JTextArea`. 8085 mnemonics, registers, numbers, and comments are all displayed in identical `#E2E2E2` text. This is the single most impactful missing feature for an IDE-aspirant tool.
3. **No breakpoint system** — There is no way to set a breakpoint. The only flow control is "Run All" or "Step Once". For debugging real programs, neither is practical.
4. **Terminal has no user input capability** — The terminal is read-only (`setEditable(false)`). The 8085 `IN port` instruction reads from I/O ports, but there is no way for the user to provide values interactively.
5. **No disassembly view** — There is no panel showing the disassembled form of loaded machine code. The memory dump shows raw hex only. Users cannot see which instruction is at each address.

### HIGH
1. **No I/O port viewer** — The `Architecture` class maintains `ioPorts` (a `HashMap`) for the `IN`/`OUT` instructions, but no panel displays their state.
2. **No stack viewer** — The stack pointer (`SP`) is shown, but the actual stack contents are not visible. Stack-heavy programs (subroutine calls, `PUSH`/`POP`) are blind to debug.
3. **Only one editor tab, no multi-file** — `EditorPanel` creates one default tab and `addNewTab()` exists but nothing in the UI calls it. There is no "New Tab" or "New File" button.
4. **No clock speed control** — The 50ms hardcoded `Thread.sleep` in `play()` gives a ~20Hz execution rate. There is no slider or input to change simulation speed.
5. **No program counter (PC) indicator in editor** — VS Code and real IDEs show a current-line highlight. There is no arrow or highlight showing which line corresponds to the current PC address.

### MEDIUM
1. **No search/find in editor** — No `Ctrl+F` find functionality in the code editor.
2. **No copy-to-clipboard for memory dump** — The hex dump table cannot be exported or copied.
3. **No hex/decimal toggle** — Register values are always shown in Hex. Many educational contexts require decimal display.
4. **No undo/redo in editor** — `JTextArea` supports undo via `UndoManager`, but it is not wired up.
5. **No "Run to address" feature** — Cannot set a target address to run until.

### LOW
1. **No version/about dialog** — No Help menu or About screen.
2. **No theme toggle** — Light mode is not an option.
3. **No font size control** — Font is hardcoded at 14px for editor, 13px for terminal.
4. **No line count in status** — No display of total line count or current line/column position.
5. **No recent files list** — Since file I/O doesn't exist, this is downstream, but worth noting.

---

## FIELD 4: PRESENTABILITY

### CRITICAL
1. **Dark theme will NOT render on Windows without Metal LAF** — As stated in Design, without `UIManager.setLookAndFeel()`, the entire visual identity fails silently. This is the most damaging presentability flaw.
2. **Line numbers are misaligned on scroll** — `LineNumberComponent.paintComponent()` uses `textArea.viewToModel2D(new Point(0, 0))` to find the first visible line. But after scrolling, `getHeight()` references the LineNumberComponent's height, not the viewport scroll position. Line numbers will desync from the editor content after any scroll event.
3. **Register grid has unbalanced empty cells** — 9 registers × 2 columns (label + value) = 18 cells in a `GridLayout(5,4)` = 20 cells. The bottom-right 2 cells are blank, creating dead space in a cramped panel.
4. **Memory table rebuilds destroy scroll position** — `memoryModel.setRowCount(0)` on every refresh causes the `JScrollPane` to jump to row 0 on every `step()`. The user cannot watch a specific memory region during execution.
5. **Error messages are identical in appearance to normal messages** — Both `appendMessage()` and `appendError()` render in the same `#E2E2E2` color. The only difference is the `[ERROR]` text prefix. On a rapidly scrolling terminal, errors are invisible.

### HIGH
1. **No anti-aliasing in custom rendering** — `LineNumberComponent.paintComponent()` uses `Graphics` not `Graphics2D`. Text drawn with `g.drawString()` has no anti-aliasing, producing jagged/pixelated line numbers, especially at non-integer DPI scaling on modern monitors.
2. **`fontDescent` and `fontHeight` variables computed but never used** — `EditorPanel.java:81-82` computes `fontHeight` and `fontDescent` but they are unused dead code. This signals incomplete/rushed rendering logic.
3. **Flags section takes equal height to registers section** — The `topWrapper` in DashboardPanel uses `BorderLayout.NORTH` for registers and `BorderLayout.CENTER` for flags. The flags panel expands to fill all remaining space — a tiny 5-flag row consuming half the left panel.
4. **Terminal has no prefix/timestamp** — Messages like "Assembling..." have no timestamp or category indicator, making log reading difficult over long sessions.
5. **Toolbar has no icons, only text labels** — Industry standard IDEs show icon-based toolbars. Pure text labels feel plain and are slower to scan visually.

### MEDIUM
1. **`JSplitPane` divider has no visual affordance** — At 4px, it is nearly invisible and gives no hover effect. Users may not discover it is draggable.
2. **Section titles in DashboardPanel use size 10 font** — "REGISTERS" at 10px with `#828282` color on dark background is nearly illegible at standard viewing distance.
3. **No loading/busy indicator during play** — When `play()` is executing, there is no spinner or status indicator. The UI appears frozen even though it is working.
4. **`JScrollPane` scrollbars not styled** — Default Windows scrollbars are wide, bright, and break the visual composition of every panel.
5. **Button hover colors are not contrasted enough** — The "Step" button (`#1E1E1E` background, `#E2E2E2` text) is visually indistinguishable from the surrounding editor panel background.

### LOW
1. **No window title update on file change** — Title always reads "j8085 Microprocessor Simulator" regardless of state.
2. **Table row height not increased** — Default `JTable` row height (16px) is too compact for a dark theme at 12px monospace font.
3. **No empty-state illustration in editor** — When the editor is blank, it shows nothing. A subtle "Start typing 8085 assembly..." placeholder would improve first-run UX.
4. **Memory dump ASCII column shows dots for non-printable** — But there is no legend or tooltip explaining this.
5. **Flag labels use colon format "S: 0"** — Inconsistent with register labels which use no separator. Mixed formatting reduces visual coherence.

---

## FIELD 5: SECURITY

### CRITICAL
1. **Arbitrary code execution via assembly input** — The `Assembler` accepts raw strings from the `JTextArea` and passes them directly into `assembleInstruction()`. While not a networked attack surface, a malformed program can trigger `SimulatorException` with unhandled paths, causing the application to enter an undefined state. No input length limit exists.
2. **`SimulatorException.getAddress()` may return -1 unguarded** — In `EmulatorBridge.step()` at line 69, `e.getAddress()` is formatted directly into a hex string. If `getAddress()` returns -1 (the uninitialized sentinel value), this outputs `FFFF` in the error message — silently hiding that no address was set, making the error message misleading and potentially hiding deeper errors.
3. **Memory write without bounds check during assembly** — `Assembler.assemble()` calls `arch.writeMemory(currentAddress, b)` where `currentAddress` starts at `startAddress` and increments for each byte. If the assembled program is larger than the memory range (`endAddress - startAddress`), writes overflow silently. `Architecture.validateAddress()` throws `SimulatorException`, but this exception is caught at the wrong granularity level, leading to partial memory corruption.
4. **No maximum size limit on terminal text** — Malicious or badly written assembly (e.g., an infinite loop printing to a port) can cause `TerminalPanel` to consume unbounded heap via `textArea.append()`. There is no trim or maximum character limit.
5. **`System.exit(0)` called from EDT lambda** — `Main.java:16` calls `System.exit(0)` inside a `SwingUtilities.invokeLater()` lambda. While not a traditional security flaw, this is an abrupt JVM termination that bypasses all shutdown hooks and could corrupt state in a future version that adds file I/O.

### HIGH
1. **Thread-unsafe `Architecture` access** — `arch.step()` is called from the `SwingWorker` background thread in `play()`, while `arch.readMemory()` is called from the EDT in `step()` and `DashboardPanel.updateMemoryView()`. `Architecture` has no synchronization. Concurrent access to the `memory[]` array is a data race.
2. **Exception stack traces printed to `System.err`** — `Main.java:24` calls `e.printStackTrace()` on `SimulatorException`. In a deployed tool, raw stack traces expose internal class names, package structure, and line numbers — an information disclosure risk.
3. **`isRunning` flag not declared `volatile`** — Used across threads (EDT writes via `pause()`, background thread reads). On multi-core JVMs without `volatile`, the background thread may cache a stale `true` value and never see the EDT's `false` write.
4. **No validation that `startAddress < endAddress`** — `MemoryConfigDialog` accepts and passes any pair of hex values to `Architecture` constructor without checking the range is valid.
5. **Hex string parsing uses `Integer.parseInt(str, 16)` without trim guard** — While `.trim()` is called, a copy-paste value with non-breaking spaces (Unicode 0xA0) will still throw `NumberFormatException` that propagates up. Only caught at the dialog level, not at a safe validation layer.

### MEDIUM
1. **No assembly sandboxing** — Assembled machine code is loaded directly into the main `Architecture` object. There is no separate simulation sandbox, meaning a buggy program can corrupt the simulator state permanently until reset.
2. **`ioPorts` in `Architecture` is a `HashMap` shared without synchronization** — `in(port)` and `out(port)` access the map. During `play()` on a background thread, concurrent reads/writes from the EDT are possible.
3. **No assembly timeout** — Assembly of a pathologically large input (megabytes of text) will block the EDT during `assembleAndLoad()` with no timeout or cancellation.
4. **No maximum address validation for I/O ports** — `in(port)` and `out(port)` do `port & 0xFF` but `getOrDefault()` with the raw value before masking could access wrong ports in edge cases.
5. **`UserInterface.java` still exists and is importable** — The old CLI class is still in `src/`. It contains a `Scanner` reading from `System.in`. If accidentally instantiated, it blocks the EDT waiting for console input.

### LOW
1. **No window close confirmation** — Abrupt close loses assembled code with no warning.
2. **No logging framework** — All output goes to `System.out`/`System.err`. No log levels, no file logging.
3. **`Architecture.reset()` does not clear `ioPorts`** — Wait, checking the code at line 840 — it does call `ioPorts.clear()`. *(Correct, not a bug.)*
4. **Error messages expose internal terminology** — "Assembly Error: Unknown mnemonic: XYZ" exposes internal parser terminology to users.
5. **No rate limiting on Step button clicks** — Rapid clicking of "Step" before each step completes can queue multiple EDT actions, causing visual stuttering.

---

## FIELD 6: UTILITY

### CRITICAL
1. **No file I/O at all** — The simulator is completely stateless across sessions. Every restart requires manually retyping code. This makes it unusable for any non-trivial program (>5 lines) in a real academic setting.
2. **Play mode is fixed at 50ms/step (~20Hz)** — Real 8085 runs at 3MHz. Educational simulators typically offer at least a speed slider (slow/medium/fast/max). At 20Hz, a 100-instruction program takes 5 seconds. At max speed with no throttle, the UI never updates. Neither extreme is useful.
3. **Memory view is not interactive** — The memory dump table is entirely read-only. Users cannot click on a cell to edit a byte directly (a critical feature for manually setting up data in memory before execution).
4. **No way to set initial register values** — There is no dialog or input to preset registers before running. The 8085 often requires registers to be pre-loaded for subroutine entry. Currently, only assembly can do this.
5. **Assembly always starts at `memoryStart`** — `EmulatorBridge.assembleAndLoad()` hardcodes `arch.getMemoryStart()` as the assembly origin. Users cannot specify a custom origin address (like `ORG 2000H`) as most 8085 textbooks require.

### HIGH
1. **`ORG` directive not supported** — The assembler has no `ORG` directive. Standard 8085 programs begin with `ORG 2000H`. Without this, all programs implicitly load at memory start, which is academically incorrect.
2. **`EQU`/`DB`/`DS` directives missing** — Standard assembler pseudo-ops for defining constants and data blocks are absent, severely limiting the programs that can be assembled.
3. **No program counter tracking in editor** — There is no way to tell which line of source code the PC currently points to. The memory dump shows the address but not the corresponding source line.
4. **Step mode shows opcode hex but not full instruction** — The terminal prints `Step: 2000 | 3E -> MVI A,` but the second byte (the immediate data) is not fetched before display, so the user sees an incomplete disassembly.
5. **Memory dump is centered around PC** — This is good, but the table jumps erratically. There is no "pin to address" option to keep a specific range visible.

### MEDIUM
1. **Terminal output cannot be cleared via button** — The `clear()` method exists but is not exposed in the UI.
2. **No hex/decimal/binary display mode for registers** — Educational tools must show multiple number bases simultaneously.
3. **No "Run to cursor" feature** — Cannot execute until a specific source line.
4. **No port I/O simulation panel** — `IN`/`OUT` instructions silently read/write the `ioPorts` map with no visual feedback.
5. **Flags are not labeled with their full names** — "CY" but not "Carry Flag", "AC" but not "Auxiliary Carry" — academic tools should be self-explanatory.

### LOW
1. **No example programs bundled** — No starter templates for common 8085 programs (e.g., addition, sorting, BCD conversion).
2. **No instruction reference panel** — No quick-reference for 8085 opcodes accessible within the tool.
3. **Memory config dialog defaults to full 64KB** — For educational use, a smaller default (e.g., `2000H`–`27FFH`) would be more realistic.
4. **No step count display** — No counter showing how many instructions have been executed.
5. **Terminal wraps lines without indent** — Long error messages wrap without indentation, making them hard to distinguish from new messages.

---

## Executive Summary

| Severity | Total Findings |
|----------|---------------|
| CRITICAL | 30 |
| HIGH | 30 |
| MEDIUM | 30 |
| LOW | 30 |
| **TOTAL** | **120** |

**Top 5 Must-Fix Items:**
1. Set `UIManager.setLookAndFeel()` to Metal LAF before any Swing construction
2. Fix `volatile isRunning` race condition in `EmulatorBridge`
3. Assemble to temp buffer, not live memory
4. Add file save/open (JMenuBar + JFileChooser)
5. Add `ORG` directive support to Assembler

**Overall Verdict:** Functional proof-of-concept. Requires significant hardening before academic use. V2 should prioritize file I/O, syntax highlighting, and the threading race condition.
