# Sizes, alphas, and tints — line-by-line audit

Companion to `REPLICA_DELTAS.md`. The main doc covers thicknesses in §5 and §6 but doesn't audit every alpha/opacity/brightness systematically. This file does.

All original values from `Pixel Watch Face/app/src/main/assets/concentric20/{main.css, theme.css}` and `widgets/complication.css`. All replica values from `concentric-watch-face/app/src/main/res/raw/watchface.xml`.

Two unit conventions:
- Koru CSS uses `0.0–1.0` for `opacity` and `fill-opacity`.
- WFF uses `0–255` for `alpha` and `#AARRGGBB` for `color`.
- `alpha="128"` ≈ `fill-opacity: 0.5`; `color="#33xxxxxx"` ≈ `opacity: 0.2` (`0x33 / 0xFF = 0.20`); `color="#ff…"` is fully opaque; `color="#00…"` is fully transparent.

---

## 1. Indices (minute & second tick rings)

| Property | Original (`main.css`) | Replica (`watchface.xml`) | Match? |
|---|---|---|---|
| Minute ticks interactive fill | `fill-opacity: 0.5` (`:18`) | `alpha="128"` on PartImage (`:1271, 1300`) | ✓ exact |
| Minute ticks AOD | `opacity: 0` + `.aod` swap (`:19, 33-38`) | `alpha="0"` base + AOD Variant `128 * z1_aod gate` (`:1284, 1291-1294`) | ✓ same intent |
| Second ticks interactive fill | `fill-opacity: 0.5` (`:20`) | `alpha="128"` on PartImage (`:1680, 1691, 1736, 1748`) | ✓ exact |
| Second ticks AOD | `opacity: 0` + `.aod` image swap | `alpha="0"` base + AOD Variant (`:1703-1713, 1763-1773`) | ✓ same intent |
| Indices tint | Filled with `color-b` (minutes) / `color-a` (seconds) | Both tinted by `[CONFIGURATION.a1AccentColor]` | ⚠ collapsed onto one role (already noted in §2.2 of the main deltas doc) |

The opacity translation is faithful. The 128/255 value gives `0.5019` which is within rounding of the original's `0.5`.

---

## 2. Minute pill (stadium capsule, right side)

| Property | Original | Replica | Match? |
|---|---|---|---|
| Fill | `color-b @ fill-opacity: 0.5` (`:145`) | None — stroke only (`:3119-3141`) | ⚠ no translucent fill |
| Stroke thickness | `arc-width: 4` (`:146-147`) | `thickness="4"` (`:3139`) | ✓ |
| Stroke alpha | inherited from fill-opacity 0.5 | `alpha="128"` (`:3120`) | ✓ |
| Stroke color | `color-b` (engine-bound) | `tintColor=[CONFIGURATION.a1AccentColor]` | ✓ role-equivalent |
| Stroke outer color | (no separate stroke) | `color="#ffffffff"` then alpha=128 (`:3138`) | ✓ |
| AOD circle fill | None — separate `#container-aod` 4px-stroke (`:151-152`) at `color-b @ 0.5` | Inner black RoundRectangle 76×76 with corner radius 20×45 (`:3104-3118`) — black fills the hole, doesn't visualize a circle | ⚠ different visual approach + asymmetric corners |
| AOD circle stroke | `arc-width: 4` | n/a (no inner stroke) | ✗ |
| Width morph | `width: 29% ↔ 0` over 0.4s with cubic-bezier (`:167, 175`) | `width: 195 ↔ 76` with EASE_IN_OUT (`:3125-3129`) | ⚠ different easing — but per `REPLICA_DELTAS_SCHEMA_NOTES.md` §1, CUBIC_BEZIER is available; can be matched |

**Fixes (sizes-focused):**
1. **Add a translucent fill** to match the original's filled stadium look. In `watchface.xml:3130-3141` add a sibling RoundRectangle with `<Fill color="#80ffffff">` (50% white) tinted by `a1AccentColor`, drawn before the stroke.
2. **AOD corner radius** to `38/38` (already in main deltas §5).
3. **AOD inner shape** — if you want to mirror the original's "outline-only AOD circle", swap the inner black RoundRectangle for an Arc with a 4-thick stroke instead of a filled rect. Lower battery cost too.

---

## 3. Corner arc complications (Dials+Arcs)

### 3.1 Track ("background") arc

| Property | Original (`widgets/complication.css` + `concentric20/main.css`) | Replica | Match? |
|---|---|---|---|
| Concentric override | `arc-width: 4` (`main.css:182`) | thickness 4 (`:3255, 3427` placeholder) / 5 (`:3285, 3457` with progress) | ⚠ replica draws 4 OR 5 depending on layer |
| Default arc cap | `cap: round` (`widgets/complication.css:159`) | `cap="ROUND"` | ✓ |
| Default arc-alignment | `arc-alignment: center` | (WFF default — stroke straddles the geometric arc) | ✓ |
| Track fill (no provider) | `color-c` (engine, full opacity) | `tintColor=[a2CompBaseColor]` × `color="#33ffffff"` = ~20% strength | ⚠ |
| Track fill (with progress) | `color-c` (engine, full opacity) | `tintColor=[a2CompBaseColor]` × `color="#ffffff"` = full | ✓ |
| Placeholder bar (no original equivalent) | n/a — `#arc-fill-background` is `display: discard` | gated by `z4_placeholder_bar`, drawn at `#33ffffff` (`:3254, 3426`) | replica extension; reasonable |

The placeholder bar at `#33` (~20% alpha) ends up reading even fainter once it's tinted by a mid-saturation theme color. On dark complication base colors (espresso `#73665e`, ash `#c2beb9`) it almost disappears. Consider bumping to `#55ffffff` (~33%) if it's not legible on light wrist-shots.

### 3.2 Progress arc / value indicator

| Property | Original | Replica | Match? |
|---|---|---|---|
| Progress arc thickness | `arc-width: 4` (single line filling from start) | `thickness="5"` (`:3361` default branch) | ⚠ slightly heavier |
| Progress fill color | `color-d` (engine) | `tintColor=[a2CompBaseColor]` × `color="#ffffff"` — same as track! | ⚠ progress is indistinguishable from track when no custom range |
| Rating indicator dot | circle `r:6` filled with `color-d` (`main.css:186`) | thick arc segment, `thickness="12"` (`:3336, 3508`) | ⚠ size matches (6r = 12 diameter) but shape differs |
| Rating indicator background | circle `r:10` (background-color, hides what's behind) (`main.css:185`) | arc segment `thickness="20"` at `color="#000000"` (`:3310, 3482`) | ⚠ diameter 20 matches `r:10` × 2; black instead of background-color |
| Indicator color (active value) | `color-d` | `tintColor=[a3CompFgColor]` | ✓ role-equivalent |

The replica swaps the original's "circle + filled background ring" for thick arc segments at the value angle. Diameters match. The big visual delta is that **the value-fill arc reads at the same color as the track** (both come through `a2CompBaseColor`) when there's no custom range — so on a battery-style complication you can't see the value. That looks intentional only if `z4_placeholder_bar` is off and the value bar uses the indicator instead. Worth verifying on device.

### 3.3 Highlight / tap region

| Property | Original | Replica |
|---|---|---|
| Editor highlight | `arc-width: 40, stroke 4.75, stroke #ffffff opacity 0.5` (`widgets/complication.css:283`) | n/a — WFF auto-renders selection highlight from BoundingArc |
| Tap region | invisible 90px arc (`widgets/complication.css:286-288`) | `<BoundingArc thickness="34">` (`:3205, 4069, 4937, 6659`) | ⚠ thinner tap target |

The replica's 34px tap region is narrower than the original's 90px. If users complain about hard-to-tap arcs, increase the BoundingArc `thickness` (it only affects the touch target, not the visible arc).

---

## 4. Slot 4 (bottom pill, replica-only)

| Property | Replica |
|---|---|
| Pill outer stroke | thickness 4, `tintColor=[a1AccentColor]`, `alpha="128"` (`:7929-7934, 7864`) | matches the minute-pill spec |
| Inner black fill | `color="#ff000000"` (`:7844, 7924, 8008, 8055`) on RoundRectangle 76 high × 195 wide, corner 38×38 | ✓ symmetric corners (unlike the minute pill) |
| Icon HsbFilter | `saturate="0"` (`:7882, 8093`) — desaturates before tinting | ✓ correct per WFF antipatterns §4 |
| Icon tint | `tintColor=[a3CompFgColor]` (`:7871, 8082`) | ✓ |
| Text alpha | `alpha="255"` | ✓ full opacity |

Slot 4 is consistently built. No deltas here other than "doesn't exist in original."

---

## 5. Hour & minute text

| Property | Original | Replica | Match? |
|---|---|---|---|
| Hour fill alpha | inherits `color-a` fully opaque | `alpha="255"`, `color="#ffffffff"`, tint `[a0PrimaryColor]` (`:3165-3180`) | ✓ |
| Hour size | 112sp | 90sp | ⚠ (already noted §1) |
| Hour weight | `GoogleSans-Clock-500` (~500) | `inter_medium` (~500 weight, lighter face) | ⚠ family swap reads thinner |
| Minute fill | inherits `color-b` fully opaque | `tintColor=[a0PrimaryColor]` (`:3147`) | ⚠ role drift (minute was `color-b` not `color-a` in original) |
| Rotating digit fill | `color-b` fully opaque | `tintColor=[a0PrimaryColor]` (`:1346, 1370, ...`) | ⚠ role drift |
| Rotating digit alpha | implicit 1.0 | `alpha="255"` | ✓ |

The "alpha is full everywhere" is true on both sides. The deltas are font family/size/role, already covered.

---

## 6. Background colors

| Layer | Original | Replica |
|---|---|---|
| Scene background interactive | `theme.css:5`: `background-color { fill: #000000; }` | `<Scene backgroundColor="#ff000000">` (`:1240`) | ✓ |
| Scene background ambient | `theme.css:6`: `background-color-ambient { fill: #333333; }` | (WFF doesn't expose ambient bg directly — relies on per-element AOD Variants over black) | ⚠ replica AOD is on pure black; original lifts to `#333333` in AOD |
| Complication background fill | `theme.css:7`: `complication-background-color { fill: #333333; }` | n/a (replica doesn't draw a complication background fill) | ✓ — original used this only for bulbs and disc backgrounds, which are out of scope for Dials+Arcs |
| Background-disc (short-text bulb) | `opacity: 0.2` (`complication.css:25`) | n/a (no bulbs) | out of scope |

**Notable:** in AOD the original's pure-black scene becomes `#333333` (a dark grey). This isn't catastrophic to ignore, but it means the original's AOD has slightly more depth than a true-black replica AOD. WFF v4 doesn't have a direct equivalent — you'd need to render a `<PartDraw>` rectangle at the back of the scene with an AMBIENT Variant flipping the fill color. Low priority.

---

## 7. Brightness / HSB effects

| Effect | Original | Replica |
|---|---|---|
| `brightness: 1.5` on `#progress-arc-overlap` (goal-progress halo when over 100%) | `widgets/complication.css:206` | not implemented |
| `HsbFilter saturate="0"` on monochromatic-image icons | n/a (engine handles fill) | `:7882, 8093` for slot 4 icon | ✓ matches the "tint a monochromatic image" idiom |
| HSB on AOD | Koru runtime auto-dims | snap-switch via Variant alpha | snap is harsher than the original's gradient — see schema notes for cubic-bezier fix |

Adding a "value over 100%" halo for goal-progress complications: gate a second progress arc by `<Condition>` on `[COMPLICATION.RANGED_VALUE_VALUE] > [COMPLICATION.RANGED_VALUE_MAX]` and increase its stroke `thickness` / shift its tint. Low priority unless users care.

---

## 8. Stroke caps and joins

| Element | Original | Replica |
|---|---|---|
| Arc cap | `cap: round` everywhere | `cap="ROUND"` everywhere | ✓ |
| Pill cap | (semicircle arcs, no caps visible) | RoundRectangle has no cap concept | n/a |

No deltas.

---

## 9. Z-order / layering subtleties

When the replica draws a corner arc with `customRange` + ranged value:
1. faint placeholder bar `#33ffffff @ a2` thickness 4 (full sweep)
2. full track `#ffffff @ a2` thickness 5 (from start to end-of-track)
3. indicator background `#000000` thickness 20 (slim slice at value angle)
4. indicator `#ffffff @ a3` thickness 12 (same slice at value angle)
5. value text rendered through MASK + filled rect tinted by `a0`

That layering is correct and matches the original's intent (track → progress → indicator → text). The thickness ladder 4 / 5 / 20 / 12 is wider than the original's 4 / 4 / 20 / 12, but the difference is 1px and unlikely to be visible.

The biggest visible delta is that **the replica's full track and the value-fill arc are the same color** (both `a2CompBaseColor` over white). When `z4_placeholder_bar` is off and the value is mid-range, the bar looks "all dim" instead of showing a value/track contrast like the original (which would render the track in `color-c` and the progress in `color-d`).

**Fix:** color the value-fill arc with `a3CompFgColor` (or `a0PrimaryColor`) instead of `a2CompBaseColor`. In `watchface.xml:3362-3365` (and the matching `customRange` defaults `:3553-3556`, `:4231-4234`, `:5394-5397`, `:6549-6552` etc.) — change `tintColor` from `a2CompBaseColor` to `a3CompFgColor`. That gives the original's two-tone track/progress look.

---

## 10. Summary additions to the main deltas doc

These items should be folded into `REPLICA_DELTAS.md` if/when you refresh it:

- **§5 (pill):** add "missing translucent fill — original is `color-b @ 0.5` filled stadium, replica is border-only." Add to optional fix list.
- **§6.2 (arc track + indicator):** add the **two-tone bug** — the value-fill arc shares `a2CompBaseColor` with the track, making them indistinguishable when there's no custom range. Recommend swapping to `a3CompFgColor`.
- **§11 priorities:** insert "color the value-fill arc with `a3CompFgColor` so it contrasts with the track" — high impact, low cost. Probably #2.5 between the existing arc-sweep fix and the AOD pill corner fix.

Everything else (alphas, indices opacity, slot 4 sizes, hours/minutes alpha) is already correct — the alpha translation 128/255 ≈ 0.5 matches the original's `fill-opacity: 0.5` exactly.
