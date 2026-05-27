# Concentric 2.0 → WFF4 replica — deltas (Dials & Dials+Arcs)

Scope: only the two layouts that exist in both projects.

- **Dials** (clean clock, no complications) — original CSS `@media COMPLICATIONS_LAYOUT_STYLES = Dials`, replica `z1_mode = 0`.
- **Dials+Arcs** (4 corner arc complications) — original default, replica `z1_mode = 1` (default).

Half Dial I is in `HALF_DIAL_REPLICATION.md`. This document is not concerned with it.

Sources:
- Original: `Pixel Watch Face/app/src/main/assets/concentric20/{main.view, main.css, theme.css, symbols/, widgets/}` + `app/src/main/res/xml/watch_face_concentric20.xml` + `defpackage/egg.java` (palettes).
- Replica: `concentric-watch-face/app/src/main/res/raw/watchface.xml` + `xml/watch_face_shapes.xml`.

All original coords are on a **456 × 456** canvas; replica is **450 × 450** (scale `0.9868`).

---

## 1. Canvas, fonts, type

| Property | Original | Replica | Status |
|---|---|---|---|
| Logical canvas | 456 × 456 | 450 × 450 | ✓ (apply 0.9868 scaling) |
| Hour text font | `GoogleSans-Clock-500` | `inter_medium` | ⚠ different family — Inter reads thinner |
| Hour text size | 112 sp | 90 sp | ⚠ ~20% smaller |
| Hour text anchor | x:48%, y:58%+2 | x:0, y:0 inside time-group at (150,175) → ~(150,175) | check alignment after font swap |
| Minute (pill) font | `GoogleSans-Clock-600` | `inter_medium` | ⚠ different family |
| Minute (pill) size | 36 sp | 38 sp | ✓ ~match |
| Rotating digits ("00"…"55") font | `GoogleSans-Clock-600` | `inter_regular` | ⚠ different family |
| Rotating digits size | 20 sp | 24 sp | ⚠ ~25% larger |
| Complication value font | `GoogleSansFlex-600` | `roboto_mono_sb` | ⚠ different family + mono |
| Complication value size | 20 sp | 24 sp | ⚠ ~25% larger |

**Fix priority:** Decide whether to chase the GoogleSans family or adopt Inter/Roboto Mono as the replica's own identity. If you want the original's wide block feel, swap the clock to a Roboto Flex 500 wdth 100 (or similar) at ~108 sp. The rotating digits and complication text are fine at 24 sp if the clock matches.

---

## 2. Color system

### 2.1 Architecture

| | Original | Replica |
|---|---|---|
| Approach | One colorway → fixed 4-color tuple | 4 independent ColorConfigurations |
| Roles | `color-a`, `color-b`, `color-c`, `color-d` | `a0PrimaryColor`, `a1AccentColor`, `a2CompBaseColor`, `a3CompFgColor` |
| Picker | 56 prebuilt colorways (set_b_colorway.xml) | 60 ColorOption × 4 roles = independent mix |
| Fallback | `#9da8b2 / #b6d7fc / #5a636b / #6e869c` | per-role defaultValue=6 (`#a7a7a7` "cloud") |

### 2.2 Role mapping

| Element | Original role | Replica role |
|---|---|---|
| Hour text | `color-a` | `a0PrimaryColor` |
| Minute text (pill) | `color-b` | `a0PrimaryColor` |
| Rotating minute digits | `color-b` | `a0PrimaryColor` |
| Rotating second digits | `color-a` | `a0PrimaryColor` |
| Minute tick ring | `color-b` | `a1AccentColor` |
| Second tick ring | `color-a` | `a1AccentColor` |
| Minute pill border | `color-b` | `a1AccentColor` |
| Slot 4 (pill) border | — | `a1AccentColor` |
| Complication arc track | `color-c` | `a2CompBaseColor` |
| Complication progress indicator | `color-d` | `a3CompFgColor` |
| Complication value text (mask) | `color-a` | `a0PrimaryColor` |
| Slot 4 title text | — | `a3CompFgColor` |

**Semantic drift:** the original assigns *minute ticks → color-b, second ticks → color-a* (two different roles). The replica puts both rings on `a1AccentColor`. This is a deliberate simplification; it does mean a user who picks two different ring colors in the original can't do so in the replica. Accept or extend with a separate `a4SecondRingColor` if you care.

### 2.3 Flavor palette parity (both layouts)

Each row lists the original 4-color tuple (from `defpackage/egg.java`) and the replica's `(a0, a1, a2, a3)` indices into the 60-option palette.

| Flavor | Original (a, b, c, d) | Replica indices → resolved | Status |
|---|---|---|---|
| `glacier_dials` | `#7CB3E5, #4B5EA3, #A3BDFF, #2295FF` | `39, 48, 46, 42` → `#7cb3e5, #4b5ea3, #a3bdff, #2295ff` | ✓ exact |
| `pebble_dials` | `#DCD7CA, #B2AB97, #9A9A8B, #C6BAB2` | `2, 7, 8, 3` → `#dcd7ca, #b2ab97, #9a9a8b, #c6bab2` | ✓ exact |
| `ooze_dials_and_arcs` | `#C5FF97, #72F95C, #84D5C1, #FF774D` | `31, 26, 34, 11` → `#c5ff97, #72f95c, #84d5c1, #ff774d` | ✓ exact |
| `jester_dials_and_arcs` | `#C4C6C1, #7092EA, #FFE454, #CE7FB3` | `5, 41, 22, 56` → `#c4c6c1, #7092ea, #ffe454, #ce7fb3` | ✓ exact |
| `porcelain_dials_and_arcs` | *(no original)* | `1, 4, 9, 19` → `#e8e2d9, #c2beb9, #73665e, #efd0ba` | replica-only addition |

All flavors faithful to the original where they overlap. `porcelain_dials_and_arcs` is the replica's own creamy palette and looks deliberately chosen — keep it.

---

## 3. Layouts (Dials & Dials+Arcs only)

| Layout | Original trigger | Replica trigger | Effect |
|---|---|---|---|
| Dials | `COMPLICATIONS_LAYOUT_STYLES = Dials` | `z1_mode = 0` | Hide arc complications; switch second ticks to dense set; inward second-text positions |
| Dials+Arcs | `COMPLICATIONS_LAYOUT_STYLES = Dials and Arcs` (default) | `z1_mode = 1` (default) | Enable 4 corner arc complications; coarse second ticks; outward second-text positions |

Both are wired correctly in the replica:
- `z1_mode == 0` collapses corner slot alpha via the `Variant mode="AMBIENT"` and the main `Transform target="alpha"` checks (`watchface.xml:3210`, `4361`, `5516`, `6671`).
- Second-tick image and number positions branch on `z1_mode == 1` via `Compare expression="small"` (lines `1678`, `1734`, `1799`, `2493`, `2545`, `2610`).

⚠ **Bug in this branch:** at lines `1686` and `2501`, the `Compare expression="small"` (i.e. `z1_mode == 1`) loads `seconds_indices_2`, which is the *empty* asset. That means in Dials+Arcs mode with `z0_index = 0`, the seconds ring renders blank. The original always shows ticks in Dials+Arcs (just the coarser variant). Two possibilities:
- Intentional (you wanted Dials+Arcs option 0 to be tickless) — fine, but document it.
- Copy/paste from the `index_forced` block — fix by using `seconds_indices_0` in both Compare and Default of those two locations.

---

## 4. Dial geometry

### 4.1 Minute number ring (rotating "00..55")

- **Group bounds.** Original: 308 × 308 centered, anchored at canvas center (`main.css:241`). Replica: 320 × 320 at `x=65 y=65` with `scaleX/Y = 0.95` when `z1_mode != 0` (`watchface.xml:1241-1252`). The 0.95 scale on Dials+Arcs is a *replica addition* — original kept the minute ring at 1.0 in both layouts. Accept as a stylistic choice or remove the `!= 0 ? 0.95 : 1.0` transform if you want strict parity.
- **Counter-rotation pattern.** Both use `[MINUTE] * 6` on the parent and `[MINUTE] * (-6)` on each digit text. ✓ identical concept.
- **Digit positions (456² → 320-local frame):**

| Digit | Original (456²) | Original → 320-local | Replica position | Δ |
|---|---|---|---|---|
| 00 | (344, 228) | (263, 146) | (261, 145) | 2 px |
| 05 | (329, 173) | (249, 95) | (245, 85) | small |
| 10 | (287, 127) | (208, 51) | (201, 40) | small |
| 15 | (228, 112) | (149, 36) | (140, 24) | small |
| 20 | (170, 127) | (91, 51) | (80, 40) | small |
| 25 | (128, 173) | (49, 95) | (35, 85) | small |
| 30 | (112, 228) | (33, 146) | (19, 145) | small |
| 35 | (128, 287) | (49, 205) | (35, 206) | ✓ |
| 40 | (170, 328) | (91, 246) | (80, 250) | small |
| 45 | (228, 344) | (149, 262) | (140, 266) | ✓ |
| 50 | (287, 328) | (208, 246) | (201, 250) | small |
| 55 | (329, 287) | (249, 205) | (245, 206) | ✓ |

Mostly within 4-10 px of the converted original. Minor — leave unless you want pixel-exact replication, in which case adjust each PartText `x`/`y` to the "Original → 320-local" column.

### 4.2 Second number ring (rotating)

Replica uses the **outward** position table when `z1_mode == 0` (Dials) and the same table inside Dials+Arcs — but the original switches positions based on layout. In the original:
- **Dials**: inward positions (digits-00 x=91%+1, digits-15 y=9%, etc., from `main.css:278-289`).
- **Dials+Arcs**: outward positions (digits-00 x=97%, digits-15 y=3%+1, etc., from `main.css:120-131`).

Replica's positions in the `seconds` group (lines 1859-2451) match the **outward** Dials+Arcs values:
- digits-00 at (392, 210) → 392/450 = 87.1% (close to original 97% but inward of it — actually matches the *inward* table at 91%, not the outward at 97%). Looks like the replica only ships one set of seconds positions and they're closer to the inward (Dials) table.

This is a meaningful mismatch in Dials+Arcs: the seconds in the original sit visibly further out (right at the 3% / 97% extremes). If you want parity, add a second positions table gated on `z1_mode`. Otherwise the current layout is "always inward" which reads cleaner anyway.

### 4.3 Indices PNG assets

| | Original | Replica |
|---|---|---|
| Minute ticks asset | `minutes_indices_qr.png` (150×158, one quarter, rotated 4×) | `minutes_indices_0.png`, `minutes_indices_1.png` (full 320 strip) |
| Minute ticks AOD | `minutes_indices_qr_aod.png` | `minutes_indices_aod.png` |
| Second ticks Dials+Arcs | `seconds_indices_0_qr.png` (coarse, quartered) | `seconds_indices_0.png` (full) |
| Second ticks Dials | `seconds_indices_1_qr.png` (dense, quartered) | `seconds_indices_1.png` (full) |
| Empty / no-ticks | n/a | `seconds_indices_2.png` |
| Second ticks AOD | `_aod.png` variants | `seconds_indices_aod.png` |

Quartering is a Koru-only memory trick; full strips are the right WFF approach. Asset size increase is negligible.

### 4.4 `z0_index` user setting (replica-only extra)

| Option | Behavior |
|---|---|
| `0` | "Default" minute indices (`minutes_indices_0`) |
| `1` | "Alternate" minute indices (`minutes_indices_1`) |
| `2` | No ticks (empty rectangle) |

The original has no equivalent — its minute ticks are always the same image. Keep this as a replica extra.

---

## 5. Minute pill (right-side stadium capsule)

| Property | Original | Replica | Status |
|---|---|---|---|
| Shape (interactive) | 2 semicircle arcs + 2 stroke rects (manual stadium) | Single `RoundRectangle` stroke | ✓ visually equivalent |
| Stroke width | 4 | 4 | ✓ |
| Fill | `color-b` at 0.5 opacity | tintColor-only (no fill) | ⚠ original has translucent fill |
| Width interactive → AOD | 29% → 0 over 0.4 s `cubic-bezier(0.37,0.54,0.02,1)` | 195 → 76 with `EASE_IN_OUT` | ✓ in spirit |
| Shape (AOD) | `#container-aod` 4 px-stroke full circle | Inner black `RoundRectangle` 76×76 with cornerRadius **20×45** | ⚠ corner radii asymmetric |
| Masks | `#rect-mask` + `#circle-mask` (background-colored, hide ticks behind) | Single inner black RoundRectangle | ✓ |

**Fixes:**
1. **AOD pill should be a true circle.** In `watchface.xml:3110-3111`, change `cornerRadiusX="20" cornerRadiusY="45"` to `cornerRadiusX="38" cornerRadiusY="38"`. The outer stroke uses 38/38 correctly already (line 3131-3132).
2. **Translucent fill (optional).** Add a `<Fill color="...">` with alpha ~128 tinted by `a1AccentColor` inside the outer pill RoundRectangle to match the original's `color-b @ 0.5α`. Only do this if the current "border only" look doesn't match the design intent.

---

## 6. Corner arc complications (Dials+Arcs only)

### 6.1 Sweep angles — main mismatch

Original (`main.css:189-196`):
| Quadrant | Position | start | sweep | total span |
|---|---|---|---|---|
| q1 | NE (top right) | 15° | **55°** | 15-70 |
| q2 | SE (bottom right) | 23° | **55°** | 23-78 |
| q3 | SW (bottom left) | 15° | 63° | 15-78 |
| q4 | NW (top left) | 15° | 63° | 15-78 |

Replica (`watchface.xml` `<BoundingArc>` per slot):
| Slot | Position | startAngle | endAngle | sweep |
|---|---|---|---|---|
| 1 (top_right) | NE | 9 | 72 | **63°** ⚠ |
| 3 (bottom_right) | SE | 108 | 171 | **63°** ⚠ |
| 2 (bottom_left) | SW | 189 | 252 | 63° ✓ |
| 0 (top_left) | NW | 288 | 351 | 63° ✓ |

The original deliberately makes the **right-side** arcs narrower (55°) than the left. The replica equalizes all four at 63°, so the right arcs reach closer to the 12/6 o'clock cardinals than the original. Fix:

```xml
<!-- slot 1, line ~4349 -->
<BoundingArc centerX="0" centerY="225" endAngle="64" height="388"
             isRoundEdge="TRUE" startAngle="9" thickness="34" width="388" />
<!-- slot 3, line ~6659 -->
<BoundingArc centerX="0" centerY="0" endAngle="163" height="388"
             isRoundEdge="TRUE" startAngle="108" thickness="34" width="388" />
```

(Numbers above use a 55° sweep starting at the same `startAngle` the replica chose. If you want to match the original's `23°` SE offset literally, use `startAngle="113" endAngle="168"`.)

You also need to propagate the new sweep to every internal `Arc` inside slots 1 and 3 — there are many `endAngle="351"` and `endAngle="171"` references in those slot blocks that need to track the new boundary. Search-and-replace within each slot's range. Specifically the placeholder bar, the value bar, and the value-mask `TextCircular`.

### 6.2 Arc track + indicator visuals

| Element | Original | Replica |
|---|---|---|
| Track (background) arc | `color-c`, arc-width 4, round cap | `a2CompBaseColor`, stroke 4-5, ROUND cap |
| Progress arc (ranged-value) | `color-d`?? (engine-tinted), arc-width 4 | none — replica draws an *indicator dot arc* instead |
| Indicator (rating) | Circle dot r=6 with `color-d` fill, on a `r=10/12` background | Thick arc segment (stroke 12) at the value angle, with background stroke 20 | ⚠ different visual style |
| Text | `textArc` curved, GoogleSansFlex-600 20sp, color-a | `TextCircular` curved, roboto_mono_sb 24sp, tinted via mask |

The replica's "indicator as a fat dot" reads differently than the original's "progress arc filling from start". Two design choices:
- **Match original exactly.** Convert the indicator-arc to a continuous "progress" arc from `startAngle` to the value angle, using stroke thickness 4-5 (not 12-20). Indicator at start angle only when `value_type = rating`.
- **Keep current style.** Document that you've intentionally swapped the rendering: the dot indicator gives a sharper read of "this is exactly the value" rather than the gradual fill. Trade-off: harder to read on small ranges, easier on big.

If you keep the current style, also keep `z4_placeholder_bar` — it gives users a way to draw the background track even when there's no ranged value.

### 6.3 Text on arc

Original wraps text along the arc itself with `textArc` (in the symbol). Replica does the same with `TextCircular` (lines ~3382, 3570, etc.), masked through a `Rectangle` filled with `a0PrimaryColor`. Functionally equivalent.

The `MASK` renderMode trick is essential because WFF doesn't let you tintColor a TextCircular directly — the mask + filled rectangle is the standard workaround. Keep it.

---

## 7. AOD & animations

| Aspect | Original | Replica |
|---|---|---|
| AOD modes | One — global cross-fade between interactive and ambient | Four (`z1_aod = 0/1/2/3`) — user picks the look |
| Minute ring fade | opacity 1↔0 over 0.333 s `cubic-bezier(0.47, 0.17, 0.43, 1)` | snap-switch via `<Variant>` |
| Minute ring scale | 1↔0.97↔0.90 over 0.4 s | none |
| Second ring fade | identical pattern, delayed | snap-switch |
| Pill width | 29%↔0 over 0.4 s | 195↔76 with EASE_IN_OUT ✓ |
| Mask reveal/hide | timed crossfade with bezier | not needed (replica uses inner black) |
| Show complications in AOD | always hidden | toggleable via `z3_aod_compl` |
| Aod pill state | shape-morph to circle | toggleable via `z2_aod_pill` |
| Seconds hide entirely | n/a | toggleable via `z5_hide_seconds` |

**`z1_aod` semantics (replica):**
- `0` — minimal: AOD ticks hidden, numbers ring hidden (minute ticks alpha = 0).
- `1` — default: AOD ticks shown, numbers ring shown.
- `2` — clock-only-with-ticks: numbers hidden, AOD ticks visible.
- `3` — clock-only: everything except time text hidden (minutes group alpha = 0).

**WFF ceiling:** keyframed `cubic-bezier` per-property animations don't exist in WFF v4. The smooth original fade/scale crossfades are not reproducible — snap-switch is honest. Don't try to fake it with multiple staggered Variants; it always looks worse than a clean snap.

---

## 8. Provider defaults (Dials+Arcs slots only)

Original:
| Slot | Position | Primary | Secondary | Fallback | Default type |
|---|---|---|---|---|---|
| 3 | NE | Pixel Weather (`CWComplicationService`) | Wear Weather | WATCH_BATTERY | RANGED_VALUE |
| 4 | SE | Fitbit HR offloadable | Fitbit HR standard | WATCH_BATTERY | RANGED_VALUE / SHORT_TEXT |
| 5 | SW | Fitbit Distance offloadable | Fitbit Distance standard | WATCH_BATTERY | GOAL_PROGRESS / RANGED_VALUE |
| 6 | NW | Fitbit AZM offloadable | Fitbit AZM standard | WATCH_BATTERY | GOAL_PROGRESS / RANGED_VALUE |

Replica (per `porcelain_dials_and_arcs` flavor, system providers only):
| Slot | Position | Provider | Type |
|---|---|---|---|
| 0 | NW (top_left) | NEXT_EVENT | SHORT_TEXT |
| 1 | NE (top_right) | WATCH_BATTERY | RANGED_VALUE |
| 2 | SW (bottom_left) | STEP_COUNT | RANGED_VALUE |
| 3 | SE (bottom_right) | HEART_RATE | RANGED_VALUE |
| 4 | (bottom pill, replica-only) | DATE | SHORT_TEXT |

Third-party providers can't be hard-coded from an external APK, so the replica using system providers is correct. The position mapping is reasonable — note slot 2 defaults to STEP_COUNT here vs Fitbit Distance in the original. Slot IDs differ (0-3 vs 3-6) — that's a fresh namespace, not a mismatch.

---

## 9. Replica-only extras (not in original)

Keep all of these — they extend the watch face cleanly:

| Configuration id | What it does |
|---|---|
| `a0PrimaryColor` / `a1AccentColor` / `a2CompBaseColor` / `a3CompFgColor` | Independent 4-axis color picking (vs original's all-or-nothing colorway) |
| `z0_index` | Minute index style: default / alternate / empty |
| `z1_aod` | 4 distinct AOD looks (minimal / default / clock+ticks / clock-only) |
| `z2_aod_pill` | Collapse pill to circle in AOD (vs always-on stadium) |
| `z3_aod_compl` | Show complications in AOD |
| `z4_placeholder_bar` | Render background track for SHORT_TEXT complications |
| `z5_hide_seconds` | Hide the entire seconds ring |
| slot 4 (`left_pill_complication`) | Extra SHORT_TEXT pill at bottom (no equivalent in original) |
| `flavor_porcelain_dials_and_arcs` | Replica-only cream palette |

---

## 10. Things WFF v4 cannot reproduce

These are format limitations, not bugs:

- **Per-layer keyframed cubic-bezier animations** (smooth ring fade+scale on AOD transitions). Only LINEAR/EASE_IN/OUT/IN_OUT on `<Variant>`.
- **Color-ramp / weighted-elements gradient arcs.** WFF `<Arc>` only supports a single `Stroke color=`. Provider's `segment_colors` payload is rendered as a single solid arc in the replica.
- **Auto-class-driven layout** (data-type → CSS class swap). Replaced verbosely by `<Condition>` and `<Compare>` blocks. Replica handles this correctly — it's just longer.
- **Image quartering** (1 PNG rotated 4× to save memory). Replica ships full strips. Negligible APK growth.
- **Hardcoded Fitbit / Pixel Weather providers.** First-party only.

Stop trying to match the smooth AOD transitions; you'll always lose.

---

## 11. Prioritized fixes (Dials & Dials+Arcs scope)

1. **Slot 1 (top_right) and slot 3 (bottom_right) BoundingArc sweep — change from 63° to 55°.** Match the original's NE/SE asymmetry. Update internal arc `endAngle`/`startAngle` references inside those slots to track the new envelope. §6.1.

2. **AOD pill corner radii — fix asymmetric `20×45` to `38×38`.** Line `3110-3111` in `watchface.xml`. §5.

3. **Verify (or fix) `seconds_indices_2` inside `z1_mode == 1` Compare branches.** Lines `1686`, `2501` — currently render empty ticks in Dials+Arcs mode 0. §3.

4. **Hour font/size — decide direction.** Inter Medium 90 vs original's GoogleSans-Clock-500 112. Either keep replica identity or bump to a heavier 100-108 sp Roboto Flex. §1.

5. **(Optional) Outward second-text positions for Dials+Arcs.** Replica uses one position table that's closer to the inward (Dials) original. Add a second branch gated on `z1_mode == 1` if you want strict parity. §4.2.

6. **(Optional) Translucent fill on the minute pill.** Adds `color-b @ 0.5α` look from the original — only if "border only" doesn't match the intended design. §5.

7. **(Optional) Disable `0.95` scale on the minute ring in Dials+Arcs.** Original keeps it at 1.0 in both layouts. §4.1.

Everything else is either an intentional replica extension, a faithful port, or a WFF ceiling. The replica is structurally close where the format allows and broader where user options are concerned.
