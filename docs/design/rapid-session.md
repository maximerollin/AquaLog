# Rapid session — design recommendations

Source: throwaway browser prototype on branch `prototype/rapid-session`
(`prototype/rapid-session/`, run with `npm run prototype`). This file captures
its conclusions so they survive independently of that branch.

**Question tested:** can a user record a routine session in under 30 seconds
without losing the useful information?

## Variants explored

Three disposable Android / Material 3 flows, no backend, no persistence:

| Variant | Routine (3 measures + water change) | Full session | Screens (incl. confirmation) |
|---|---:|---:|---:|
| **A — Continuous form** | 5 significant actions | 8 | 2 |
| B — Guided wizard | 10 | 12 | 7 |
| C — Compact checklist | 11 | 14 | 2 |

The prototype's automated timings (< 3 s) only prove the flows work; they are
**not** a usability measurement. The 30-second human threshold still has to be
verified with real testers on a mid-range Android phone.

## Verdict

**Keep variant A (continuous form) as the base of the future Compose flow.** It
completes the standard routine without a screen change, makes skipped measures
obvious through their empty field, and keeps maintenance actions in the same
context. The wizard (B) adds a disproportionate navigation cost; the checklist
(C) gives a good completeness overview but its repeated bottom-sheet open/confirm
slows every measure.

Worth borrowing from the others:
- from B: a readable recap — but shown **after** saving, in the chronology card,
  never as a mandatory step;
- from C: a discreet visual marker of which fields are filled, folded into the
  continuous view without a per-measure bottom sheet.

## Recommendations to carry into the technical spec

1. A rapid session fits on a single scrolling view; no mandatory verification
   step precedes saving.
2. The first numeric field gets focus on open, and the keyboard **Next** action
   moves focus without dismissing the keyboard.
3. The previous session seeds the parameter order and the maintenance-action
   details, but **never pre-fills a new measurement** — the last value stays as
   discreet visual context only.
4. An empty measurement field explicitly means "not taken" and does not block
   saving.
5. A usual action reuses its last quantity in one tap; a secondary action opens
   the detailed edit.
6. Observation and incident stay folded and optional.
7. An unusual value is flagged immediately without blocking the save.
8. The fixed **Save session** button is disabled only while the session is
   empty. A single press produces one atomic local session and shows its card
   in the chronology right away.
9. The acceptance test measures routine and full session separately. The
   30-second criterion counts as met only after human trials on a common
   Android phone.

## Next step

Test `?variant=A&scenario=routine` first, then `?variant=A&scenario=complete`,
with no prior demo; record time, hesitations and corrections. When an
implementation ticket exists, link `prototype/rapid-session` as the primary
source and rewrite the chosen flow cleanly in Jetpack Compose rather than
reusing the prototype code.
