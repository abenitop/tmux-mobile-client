# Visual redesign (design)

**Date:** 2026-09-24
**Status:** awaiting review
**Parent doc:** `docs/reference/product-spec.md`
**Builds on:** P1–P5 (`docs/specs/2026-09-23-phase1-chat-view-simple-design.md`,
`docs/specs/2026-09-23-phase1-terminal-rendering-design.md`,
`docs/specs/2026-09-24-host-manager-design.md`)

## Purpose

The app currently has a functional but not-yet-distinctive look: Material 3 seeded
`#005C4B`, a dark theme, and WhatsApp-style bubbles that were added for UX familiarity.
This sub-project gives the whole app a coherent visual identity — designed in Penpot,
then ported to the Android app — covering the UI screens, the logo, and the icon. It
does **not** re-lay-out the screens; it re-skins them and refines the brand mark.

**Why:** the WhatsApp-style chat is the key differentiator against other tmux/terminal
clients. The redesign keeps that familiarity front-and-center and stamps it with the
product's own identity, so it reads as "a chat app for your server", not a WhatsApp skin.

## Design direction — "Bubbles, but mine"

Chat-forward, terminal-flavored. The bubble chat is the hero. Terminal identity is
carried through the accent color and monospace accents, not through chrome.

## Visual foundation

### Color
- **Accent (send, links, active states):** terminal-green `#7EE787` (kept — the
  product's signature; already the icon green).
- **Neutrals:** WhatsApp-like off-white light surfaces and near-black dark surfaces,
  but **green-tinted** (not WhatsApp blue) so the app reads as its own product.
- **Material 3 seed:** stays `#005C4B` (drives containers/secondaries — `Color.kt`).
- Delivery ticks / status stay in the accent/surface system, no new colors.

### Typography
- **System sans** for bubbles, chrome, and labels.
- **Monospace (JetBrains Mono)** *only* for terminal output, shell prompts, host
  addresses, and ports — never for UI copy.

### Theme
- **Default: follow the OS setting** (no in-app preference in scope). Both light and
  dark variants ship; the app opens in whichever the device uses.

## Logo & icon

Refine the existing framed-terminal "tm" monogram rather than replace it:
- Keep the concept (monogram in a terminal frame, green-on-dark) so it stays
  recognizable on the Play Store.
- Cleaner grid: consistent stroke weight, tighter kerning, better optical centering.
- Regenerate the adaptive (foreground/background) and legacy densities from the refined
  master, plus `icon-512.png` and `feature-graphic.png` in `docs/store/`.

## Screens (restyle, no re-layout)

1. **Chat screen (hero).** WhatsApp-style bubbles: sender = green tint, receiver =
   surface, rounded, delivery ticks + timestamp. Composer = rounded input bar with green
   send button and attachment chip. Header = host name, live connection dot, monospace
   host label.
2. **Hosts screen.** Saved-server card list + add-host FAB, in the new palette.
3. **Host form.** Labeled fields; monospace for host/port; password field with
   show/hide.
4. **Host-key TOFU.** Diff card + permission prompt (P4), restyled to the new palette;
   monospace for key fingerprints.

## Deliverables

1. **Penpot file(s):** one team/project for Tmux-Max Mobile containing — a style guide
   page (color tokens, type scale), a logo/icon page, and one frame per screen in both
   light and dark.
2. **Android port:** apply the palette and typography to `ui/theme/`, re-skin the four
   screens, and swap in the refined icon assets.
3. **Store assets:** refreshed `icon-512.png` and `feature-graphic.png`.

## Non-goals

- No screen re-layout or new navigation.
- No new in-app features (no settings screen, no theme toggle UI).
- No change to SSH/connection behavior.

## Exit criteria

- Penpot has the style guide, logo/icon, and all four screens in light + dark.
- The Android app builds, and the four screens render in the new palette/type with the
  WhatsApp-style chat unchanged in behavior.
- Refined icon installs on device; Play Store assets updated.
