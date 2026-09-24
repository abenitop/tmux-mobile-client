# tmux-mobile-client — project context for agents

Native Android tmux client (SSH → tmux control mode → real terminal rendering), with an optional
Chat view that renders Claude Code / Hermes sessions as chat bubbles.

## State (HEAD `43475ce`)

- Phase 0 spike, Chat view (simple), real terminal rendering, idle-snapshot fix, chat-freeze fix,
  and the MVP connect flow are all merged.
- MVP connect flow (commits `295a098..43475ce`): real `ConnectScreen` (hostname, port, username,
  password, session name → `EncryptedSharedPreferences`). SshSpikeSession's tmux command is now
  `tmux -CC new-session -A -s <name>` (create-or-attach), replacing the old `attach -t`.
- Password auth is additive to the original key-based path (`assetKeyName` still works).

For the full chronological story and the non-obvious ordering requirements in the terminal
rendering path (e.g. `setTerminalViewClient` + `setTextSize` must both precede `attachSession`),
read before touching `TerminalHost.kt` / `SshSpikeSession.kt`:
`docs/plans/2026-09-23-phase1-terminal-rendering-implementation.md` (Task 1 write-up).

## Known gaps (do not "fix" unless asked — but know them)

1. **Host-key verification: `PromiscuousVerifier` (accepts any server key).** TOFU hardening is
   designed but not built — see `docs/plans/2026-09-24-host-manager-implementation.md` Task 4.
   **Priority 1** now that the app handles a real password to a real server.
2. Multi-host list / Room storage / add-edit-delete UI — deferred, same host-manager plan.
3. Real end-user server run never verified (only a disposable stand-in).
4. Chat view richness (diff cards, permission prompts) — deferred, see Chat view spec.
5. Android 16KB page-size alignment (`libtermux.so` unaligned) — blocks Play Store on Android 15+.
6. Termux terminal-view/terminal-emulator licensing ambiguity — resolve before Play Store.

## Design principles (hard constraints)

- **Keyboard:** use the real Android system keyboard. **Never a custom on-screen keyboard.**
  Gestures (long-press = Esc, swipe = arrows) and plain-text tokens (`^c`, `:esc`) are the only
  acceptable ways to handle special keys. Surface the tension explicitly if a design risks this.
- **Chat UI:** WhatsApp-style bubbles (colored/rounded, tight spacing, tails) is the operator's
  top visual priority for the whole app. Current bubbles are plain/unstyled.

## Visual TODO flagged by operator

- `MainActivity` uses the default light `MaterialTheme`, producing a stark white frame around
  `TerminalHost`'s explicitly-black terminal background. Apply a dark theme at the
  `MainActivity` level.

## Dev-testing note (infra)

- The app's dev VPS uses a forced-command SSH dispatcher (`/home/agent-stack/.ssh/phase0-chat-dispatcher`)
  that allowlists exact byte-compared commands. On-device testing against `phase0-test` is **unblocked**
  as of 2026-09-24: the allowlist now permits both `tmux -CC attach -t phase0-test` and
  `tmux -CC new-session -A -s phase0-test`. Editing that allowlist is still a shared-infra security
  change — do not do it yourself.

## Pointers

- Product vision: `docs/reference/product-spec.md`
- Plans: `docs/plans/` (phase0-spike, phase1-terminal-rendering, phase1-chat-view-simple,
  mvp-connect, host-manager)
- Design specs: `docs/specs/`
