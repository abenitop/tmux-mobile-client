# tmux-mobile-client

A native Android tmux client. Connect to any server over SSH, attach to a tmux
session in control mode, and get a real terminal on your phone — driven by the
standard Android system keyboard. An optional Chat view renders Claude Code /
Hermes sessions as WhatsApp-style chat bubbles (with diff cards and permission
prompts).

## Features

- **Real terminal rendering** — Termux `terminal-view` / `terminal-emulator`,
  with raw and compose input modes and a toggle between them.
- **System keyboard only** — no custom on-screen keyboard; special keys are
  gestures + text tokens (`^c`, `:esc`, long-press Esc, swipe arrows).
- **Trust-on-first-use host keys** — first connect pins the server key; a
  changed key is rejected into a mismatch screen.
- **Dark theme** — `TmuxMobileTheme` (Material 3 dark scheme).
- **Multi-host manager** — Room-backed host list with add/edit/delete, and
  Android-Keystore-backed per-host passwords.
- **Chat view** — WhatsApp-style tailed/rounded bubbles, run-grouping, inline
  diff cards for edits/writes, and detected permission prompts with options
  parsed from the pane.

## Build

```bash
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
export ANDROID_HOME=$HOME/android-sdk
export ANDROID_SDK_ROOT=$HOME/android-sdk

./gradlew :app:testDebugUnitTest :app:assembleDebug
```

The APK lands at `app/build/outputs/apk/debug/app-debug.apk`.

## Project layout

- `docs/reference/product-spec.md` — full product spec.
- `docs/plans/` — implementation plans (one per milestone).
- `app/src/main/java/com/tmuxmobile/phase0/` — source.
- `app/src/test/` — unit tests.

## Author

Ramlix — <abenitop@gmail.com>

## License

GPL-3.0. See [LICENSE](LICENSE).

Note: the terminal rendering depends on Termux's `terminal-emulator` /
`terminal-view`, whose licensing is GPLv3 (inherited from Termux's additions
over the Apache-2.0 Jackpal base). This repository is therefore distributed
under GPL-3.0.
