# Design tokens — Tmux-Max Mobile

Direction (from operator's `tmux Mobile Client UI`): warm editorial "paper + ink" chrome,
dark terminal panels, context-colored status accents. Source of truth: the operator's
5-screen artifact + `C-framed-terminal.png` logo.

## Brand / logo

| Token | Value | Use |
|-------|-------|-----|
| terminal-green | `#70E080` | logo glyph, "on" states |
| frame-green | `#005040` | logo frame |
| ink-black | `#000000` | logo background |

## Fonts

| Role | Family |
|------|--------|
| Display / UI | Bricolage Grotesque |
| Editorial accent | Source Serif 4 |
| Terminal / mono | JetBrains Mono |

## Surfaces (light, warm)

| Token | Value |
|-------|-------|
| page | `#FAF9F5` |
| surface | `#EDE8DF` |
| surface-alt | `#F0EEE6` |
| border | `#D8D2C6` |
| border-light | `#EDEDEB` |

## Ink (text)

| Token | Value |
|-------|-------|
| ink-strong | `#0F0E0C` |
| ink | `#1A1916` |
| ink-text | `#2C2A26` |
| ink-mid | `#3A3730` |
| ink-muted | `#A39D92` |

## Dark terminal panel

| Token | Value |
|-------|-------|
| terminal-bg | `#0F0E0C` |
| terminal-panel | `#1A1916` |
| terminal-border | `#2C2A26` |

## Status accents (context-colored)

| Token | Value | Meaning |
|-------|-------|---------|
| green | `#7BC96F` | done / working / live |
| amber | `#F2A93B` | needs attention / working |
| pink | `#E87AA5` | — |
| blue | `#6FB3F2` | — |

## Semantic

| Token | Value |
|-------|-------|
| approve | `#9FDC92` on `#142514` |
| deny | `#F29A8F` on `#2A1413` |
| danger | `#B00020` |

## Theme

Light-first (cream). Dark terminal panels are used inside the light chrome (mixed), not a
full dark mode. Follows the operator's artifact.
