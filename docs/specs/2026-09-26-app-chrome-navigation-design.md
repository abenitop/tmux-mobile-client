# App-wide chrome & navigation (design)

**Date:** 2026-09-26
**Status:** awaiting review — operator-specified, not yet built
**Parent doc:** `docs/reference/product-spec.md`
**Builds on:** `docs/specs/2026-09-24-visual-redesign-design.md` (v2 palette),
the terminal-screen chrome built 2026-09-25 (`TerminalChrome.kt`)

## Purpose

The hamburger + 3-dot chrome built on 2026-09-25 exists **only inside `SpikeScreen`** (the
terminal screen). The app's launch destination is `hosts`, which has no chrome at all —
so from the operator's entry point the whole redesign is invisible. Confirmed on device
(Pixel 8 Pro, 2026-09-26): the Hosts screen renders title + host card + Connect/Edit/Delete
+ "Add server", with no top bar, no hamburger, no 3-dot.

This sub-project hoists the chrome out of `SpikeScreen` to wrap every destination, and
replaces both menus with the operator-specified structure below.

**Why:** the terminal screen is two taps from launch. Chrome that only exists there is
chrome the operator never sees.

## Decisions (operator-specified)

### Hamburger → drawer

Two labelled sections, replacing the current 5-item drawer (Hosts / Sessions / New session /
Macros & templates / Settings — the three "coming soon" stubs are dropped):

```
SSH connect
  ├─ Saved hosts      → existing `hosts` route
  └─ New host         → host form: new IP + user + password
TMUX
  └─ Tmux sessions    → host-picker screen → then that host's tmux session list
```

- **TMUX is sequential, not a nested tree.** The drawer holds one entry; tapping it opens
  a **host picker**, and choosing a host yields the **tmux session list** on the next screen.
- **The host picker connects on its own** — picking a host establishes its own SSH
  connection (password prompt as required) and lists that host's tmux sessions. It does not
  require an existing connection.
- **Grey-out:** drawer entries disable when their destination is unavailable (no saved hosts
  → Tmux sessions disabled; etc.).

### 3-dot → overflow

Six new items plus the retained red Forget host:

| # | Item | Spec |
|---|------|------|
| 1 | **Split right** | `split-window -h` on the attached pane |
| 2 | **Split down** | `split-window -v` on the attached pane |
| 3 | **Font size** | Submenu, sizes **8–24 in steps of 2** (8, 10, …, 24) |
| 4 | **Download file** | Operator types the **remote path**; saved to the phone's **Downloads** folder |
| 5 | **Dense top bar** | Three modes: **Auto (landscape)** / **Always** / **Never** |
| 6 | **Keyboard toolbar** | Row of extra Ctrl / Esc / Tab / arrow keys above the keyboard. Three modes: **Auto (with keyboard)** / **Always** / **Never** |
| 7 | **Clear** | Clears **both** the terminal screen and the chat history |
| — | **Forget host** | Retained, red (unchanged) |

**Removed:** View mode (Terminal / Chat: Claude / Chat: Hermes) and Input mode (Compose /
Raw) leave the overflow. Their functionality is not deleted — see Open items.

**Persistence:** font size and dense-top-bar are **app-wide, remembered across restarts**.

## ⚠️ Constraint override — keyboard toolbar

`AGENTS.md` and the Phase 1 terminal-rendering spec carry a hard constraint:

> "use the real Android system keyboard. **Never a custom on-screen keyboard.** Gestures
> (long-press = Esc, swipe = arrows) and plain-text tokens (`^c`, `:esc`) are the only
> acceptable ways to handle special keys."

The operator has **explicitly overridden this** for the keyboard toolbar (extra Ctrl / Esc /
Tab / arrow keys above the system keyboard). This is recorded as a deliberate amendment, not
an oversight. `AGENTS.md`'s constraint text needs updating to reflect it — **not done
unilaterally; needs operator approval.**

## What this touches (implementation surface)

- `MainActivity.kt` — chrome hoisted from `SpikeScreen` (~line 406) to wrap the `NavHost`
  in `setContent` (~lines 52–131). `SpikeScreen`'s session state (`connected`,
  `attachedSession`, `paneId`, `viewMode`, `inputMode`) is currently `remember`-local and
  must be lifted to a shared holder so app-level chrome can read it for grey-out decisions.
- `TerminalChrome.kt` — rework from terminal-screen composable to app-level, nav-aware
  scaffold; both menus replaced per above.
- **New:** a host-picker screen + tmux-session-list step for the TMUX drawer entry.
- **New:** an app-wide preferences store. **None exists** — the only persistence today is
  `PasswordStore.kt` (Keystore, secrets). Font size + dense-bar need a real prefs store.
- **New:** SFTP download. **No SFTP code exists anywhere in the repo** (verified); sshj
  0.41.1 supports it, so this is new code, not a wire-up.
- `TerminalHost.kt:56` — `setTextSize(30)` is **hardcoded**; the font-size submenu has to
  make it dynamic.

## Non-goals

- No change to the SSH/tmux attach path, TOFU verification, or host storage.
- No re-skin — v2 palette and typography are settled and stay.
- Chat view, diff cards, permission prompts: untouched.

## Exit criteria

- Hamburger + 3-dot visible on **every** destination, from the Hosts launch screen onward.
- Drawer matches the two-section structure; TMUX host picker connects and lists sessions.
- All seven overflow items behave as specified, with grey-out on unavailable actions.
- Font size and dense-top-bar survive an app restart.
- Download writes an operator-specified remote path into the phone's Downloads folder.
- Builds green; unit tests pass; verified on device with screenshots.

## Open items — need operator input before/while building

1. **View mode / Input mode relocation.** Both leave the overflow. Are they deleted, or moved
   to the drawer / elsewhere? The chat view becomes unreachable otherwise.
2. **Download destination details.** Fixed filename from the remote path's basename?
   Collision behaviour on re-download? Failure messaging (bad path, permissions)?
3. **Keyboard toolbar modes.** Three modes (Auto with keyboard / Always / Never) — settled.
   Open: is the mode remembered app-wide like the others (assumed yes), and the exact key set
   beyond Ctrl / Esc / Tab / arrows (e.g. Ctrl+C, Home, End, pipe).
4. **Dense top bar semantics.** What "dense" changes precisely (height, title size, subtitle
   visibility). Its three modes (Auto landscape / Always / Never) are settled.
5. **Split right/down grey-out.** Only meaningful with a live attached pane — confirm it
   greys out otherwise, consistent with the drawer rule.
6. **Icons** for the new items (not operator-specified).
