# Design spec — Tmux-Max Mobile (v2)

Source of truth for the visual design, built in Penpot (`TmuxApp` file). Direction: a plain
SSH→tmux client, "cockpit not workstation". The defining feature is the **compose bar**:
the phone keyboard types into a WhatsApp-style input bubble and sends `send-keys` into the
live tmux pane. No agent-specific UI — anything running *inside* a session is just terminal
content.

## Palette (Penpot token set "TmuxMax")

- page `#FAF9F5` · surface `#EDE8DF` · surface-alt `#F0EEE6` · border `#D8D2C6`
- ink `#1A1916` · ink-text `#2C2A26` · ink-mid `#3A3730` · ink-muted `#A39D92`
- terminal bg `#0F0E0C` · terminal green `#70E080`
- status green `#7BC96F` · amber `#F2A93B` · pink `#E87AA5` · blue `#6FB3F2`
- approve `#9FDC92` · deny `#F29A8F` · danger `#B00020`
- logo: `#70E080` glyph on `#005040` frame / black (`C-framed-terminal.png`)

## Fonts

- UI / display: **Bricolage Grotesque** (200–800)
- Terminal, process labels, host/path strings: **JetBrains Mono**
- (Source Serif 4 available for editorial accents if ever needed)

## Screens (7)

1. **Hosts** — saved servers, connected/offline status dot, `user@host`, auth method
   (ed25519 key / password). `+ Add server` button.
2. **Add server** — SSH form: hostname/IP, port (22), username, auth (password·key),
   password; "Generate Ed25519 key"; Connect.
3. **Sessions (home)** — tmux session list after connect: session name, process label
   (bash/vim/htop/git…), `N windows · <activity>`; `+ New session`.
4. **Terminal** — session name + attached status; window tabs (`0: bash`, `1: vim`…);
   dark terminal pane; **compose bar** "Message to pane…" + green ➤ send; read-only
   toggle (safe-by-default); sent lines render as bubbles. Caption: phone keyboard = the
   only input (no custom on-screen keys).
5. **New session** — name + start directory + optional start command; shows the raw
   `tmux new-session -A -d -s <name> -c <dir>` equivalent.
6. **Macros & templates** — saved commands (send on tap) + session presets (dir + cmd).
7. **Settings** — theme (light/dark/system); input & gestures toggles (swipe scroll,
   long-press Esc, volume keys, Enter sends, read-only attach, biometric lock).

## Key interactions (from product-spec)

- Compose mode default: multi-line field above IME, normal input type, send =
  `send-keys -t <pane> -l "<text>"` + Enter.
- Raw mode toggle for vim/htop/TUI.
- Read-only attach by default; tap to enable input.
- Special keys via tokens (`^c`, `^d`, `:esc`, `:tab`) and swipe/volume gestures.
