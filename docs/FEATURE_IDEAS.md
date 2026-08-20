# MediSafe — 20 Feature Ideas

Ordered roughly by *value per unit of effort*, given what the codebase already has
(Room + Compose + AlarmManager + widget + GitHub self-update, all local-only).

Effort is a rough guide: **S** ≈ a few hours, **M** ≈ a day or two, **L** ≈ multi-day.

---

## Tier 1 — Highest impact, builds directly on what exists

### 1. Alarm reliability self-test ("Will my alarm actually ring?") — S
One button that fires a real test alarm 10 seconds later, and a checklist showing
notification permission, exact-alarm permission, battery-optimisation exemption,
DND policy access, and whether the channel was muted by the user. Medication apps
fail silently and users only discover it after missing a dose. This makes the
failure visible *before* it matters.

### 2. Snooze limits + "don't let me snooze this" — S
Currently every alarm snoozes 15 min forever. Add per-item max snooze count and
configurable snooze length (5/10/15/30). After the limit, the alarm only offers
Take or Skip. Directly protects adherence for critical meds.

### 3. Take confirmation with proof-of-wake — S
For urgent meds, require a small interaction (type the dose, solve 2+3, or scan the
pill bottle's barcode) before "Take" registers. Stops the half-asleep dismiss-and-
forget that quietly breaks adherence data.

### 4. Caregiver alert / missed-dose escalation to a contact — M
If a high/urgent dose is missed by N minutes, send an SMS or WhatsApp intent to a
nominated contact. This is *the* killer feature for elderly parents and the most
requested capability in this category. Stays local-only — no server needed, just
`SmsManager` or an `ACTION_SENDTO` intent.

### 5. Adherence report export (PDF/CSV) for doctor visits — M
You already log TAKEN/MISSED/SKIPPED with timestamps. Render a clean date-ranged
summary with per-med adherence % and a chart, shareable via `ACTION_SEND`. Turns
existing data into something a doctor actually wants to see.

---

## Tier 2 — Strong quality-of-life wins

### 6. Drug interaction & duplicate-ingredient warnings — L
Warn when two active meds interact or share an active ingredient. Ship a small
bundled dataset for common interactions to stay offline-first; optionally enrich
from RxNorm/openFDA when online. High clinical value, but needs care: must be
clearly advisory, never authoritative.

### 7. Refill forecasting with pharmacy hand-off — S
You track `pillsRemaining`. Compute the projected run-out *date* from actual dosing
frequency, show "runs out Tue 3 Sep", and offer a one-tap call/SMS to the stored
pharmacy number. Much more actionable than the current threshold alert.

### 8. Multi-profile support (family members / pets) — L
One phone often manages a whole household. Add a profile dimension to reminders and
logs with a switcher in the top bar and per-profile adherence. Touches the schema
broadly, so best done deliberately with a proper migration.

### 9. Smarter recurring schedules — M
Real prescriptions need: every-other-day, cycles (21 on / 7 off for contraceptives),
tapering doses (3 tablets → 2 → 1 over weeks), and "every N weeks" injections.
Extend `RecurrenceType` plus a small schedule-rule table.

### 10. Photo of the pill / medication — S
Attach an image to each med. Enormously helpful for elderly users and for anyone
with several similar white tablets, and it makes the alarm screen unmistakable.

### 11. Wear OS companion / notification bridging — M
Take, snooze and skip from the wrist. Even without a full Wear app, wearable-friendly
notification actions plus a data-layer sync covers most of the value — and a watch
buzz solves "phone was in another room".

### 12. Quick actions from the lock screen widget — S
The widget shows the next reminder; add TAKE and SNOOZE directly to it, plus a
compact "today's remaining doses" glance view.

---

## Tier 3 — Polish, insight, and retention

### 13. Adherence streaks and gentle gamification — S
"12-day streak", weekly goal ring, best-streak record. Proven to lift adherence,
and cheap to build on the existing `DayAdherence` model. Keep it encouraging rather
than shaming — guilt makes people delete health apps.

### 14. Time-of-day insights — S
"You miss evening doses 40% of the time." Surface patterns from existing logs and
offer to move or add a nudge for the problem slot.

### 15. Symptom & side-effect journal — M
Log how you felt alongside doses, then correlate: "headaches cluster on days you
skip the morning dose." Great input for a doctor's appointment.

### 16. Health vitals tracking — M
BP, glucose, weight, temperature with simple trend charts. Natural adjacency for a
med tracker and keeps the app relevant between doses.

### 17. Encrypted backups & optional cloud sync — M
Backups are currently plaintext JSON containing health data. Add passphrase-based
encryption (SQLCipher or AES over the export), then optional Drive/WebDAV sync.
Privacy-preserving and it fixes a real weakness in the current export.

### 18. Appointment & prescription-renewal reminders — S
The EVENT category already exists; add doctor-appointment fields (clinic, address,
"leave by" travel-time alert) and prescription-expiry warnings.

### 19. Accessibility & elderly-friendly mode — S
Large-text layout, very high contrast, simplified two-button alarm screen, and full
TalkBack labelling. A large share of the target users need this, and it is mostly
layout work rather than new logic.

### 20. Localisation, starting with Hindi and Marathi — M
Strings are currently hardcoded in Kotlin. Extract to `strings.xml` and translate.
Given the India-first user base, this meaningfully widens reach — and the extraction
is worth doing regardless for maintainability.

---

## Suggested order

If I were picking a next sprint: **1, 2, 7, 12** are quick and each removes a real
failure mode. Then **4** (caregiver alerts) and **5** (doctor report), which are the
two features most likely to make someone choose this app over the alternatives.

A note on **6** (drug interactions): it is the most clinically valuable idea here and
also the riskiest. If you build it, keep the wording advisory, cite the data source,
and never imply it is a substitute for a pharmacist.
