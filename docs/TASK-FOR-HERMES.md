# Task: build Phase 0 of the tmux Mobile Client Android app

**From:** Claude Code (this box's `agent-stack:0.0` session)
**Date:** 2026-09-23

## Scope — in

- This repo only: `/home/agent-stack/projects/tmux-mobile-client` (private GitHub repo
  `abenitop/tmux-mobile-client`, remote `origin` already configured).
- Read, in order: `docs/reference/product-spec.md` (full product vision, for context only —
  don't build beyond Phase 0), `docs/specs/2026-09-23-phase0-spike-design.md` (what and why),
  then `docs/plans/2026-09-23-phase0-spike-implementation.md` (the actual build plan).
- Execute the plan's 5 tasks **in order**, exactly as written — each task has its own
  files, code, and a verification step. Don't skip a verification step.
- Commit after each task as the plan instructs, and `git push` after each commit so
  progress is visible on GitHub as you go.
- All dependency/tool versions in the plan were verified against primary sources
  (Google Maven metadata, GitHub releases, AGP docs) on 2026-09-23 — use exactly those,
  don't substitute newer ones without re-verifying, don't use older ones from training
  memory. Same docs-first rule as everything else on this box.

## Scope — out

- Nothing in `/opt/agent-stack` changes for this task. This is a fully separate project.
- No Phase 1+ features (terminal emulation, real input UI, hosts/session list,
  auto-reconnect, agent layer). The plan's "Global Constraints" section lists the
  explicit non-goals — stay inside them even if the product spec makes a fuller
  version tempting.
- Don't touch any other service on this box (litellm-router, WhatsApp bridge, open-webui,
  wg-easy, upload-inbox, qdrant, calibre-web, rag-api, Caddy, or anyone else's tmux
  session) while provisioning Task 3's SSH key/session.

## Judgment you have

- If a pinned version has gone stale by the time you build (unlikely, same day, but
  possible), re-verify against the primary source and note what changed — don't silently
  reuse a broken pin.
- If an sshj/Compose/Gradle API detail in the plan turns out wrong once you actually
  compile against it, fix it and note why — the plan's author (me) verified the API
  shapes against sshj 0.41.1's source before writing this, but verify-by-compiling beats
  verify-by-reading if the two disagree.
- Don't deviate from the design's core decisions (single screen, raw text only, dedicated
  disposable test session, no terminal emulation this phase) without flagging it back to
  me first — those were deliberate scope cuts agreed with the operator, not oversights.

## Where you'll need to stop and wait

Task 5's steps 4-5 (install over wireless ADB, the actual phone-in-hand verification)
need the operator's physical Android phone with wireless debugging enabled and joined to
the Tailscale tailnet. You can't do those steps alone. Get through Task 5 step 3 (built
APK, `BUILD SUCCESSFUL`), then stop and report back — don't guess at a phone IP or wait
indefinitely. I'm watching this session and will relay the phone's Tailscale IP once the
operator has it ready.

## Everything else

Work through it. If you get well and truly stuck (not just "this needs a docs lookup"),
say so clearly rather than pushing forward on a guess.
