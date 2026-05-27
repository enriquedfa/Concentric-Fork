# Half Dial I — Replication Notes

Self-contained reference for porting Concentric 2.0's third layout (`Half_Dial_I`) to this WFF v4 project. Sourced from `Pixel Watch Face/app/src/main/assets/concentric20/main.css` and `app/src/main/res/xml/watch_face_concentric20.xml`.

All numeric coordinates below are given for the original **456 × 456** canvas. To use in this project's **450 × 450** canvas, multiply by `450/456 ≈ 0.9868`.

---

## 1. Visual summary

The Half Dial layout shifts the analog clock left and stacks three round bulb complications down the right side of the watch. Concretely:

- `AnalogClockContainer` translates by `x: -33%` (clock pivots at the original center; the whole minute ring + hour digit + pill move with it).
- Hour text shrinks from **112sp** to **100sp**.
- Minute pill shrinks: width animates `0 ↔ 22%` (vs `0 ↔ 29%` in default Dials+Arcs).
- Pill in this layout uses `.minute-container { fill: color-b; fill-opacity: 0.5; }`.
- Seconds use the **dense** ticks (`seconds_indices_1`) and the **inward** second-text position table — the same as Dials.
- Three ROUND_RECT bulbs sit on the right edge.

CSS evidence:

```css
/* main.css:292-301 */
@media (COMPLICATIONS_LAYOUT_STYLES = Half_Dial_I){
    ComplicationBulb           { display: inline; }
    AnalogClockContainer       { x: -33%; y: 0; px: 50%; py: 50%; }
    #hour-text                 { font-size: 100; }
    #digital-time-container    { y: 6; }
    #container                 { x: 73%+2; y: 50%; width: 22%;
                                  anchor: center-left; fill-opacity: 0.5; }
    #container .anim-hipower.anim-width
        { begin: toInteractive; from: 0;   to: 22%; dur: 0.400;
          easing: cubic-bezier(0.26,0.54,0.06,1); end: toAmbient; }
    #container .anim-lopower.anim-width
        { begin: toAmbient;     from: 22%; to: 0;   dur: 0.400;
          easing: cubic-bezier(0.37,0.54,0.02,1); end: toInteractive; }
    .minute-container { fill: color-b; fill-opacity: 0.5; }
}
```

Half Dial also inherits everything from the shared `(Dials) or (Half_Dial_I)` block (`main.css:270-290`):
- `ComplicationArc { display: discard; }` — no corner arc complications.
- Seconds image swapped to `seconds_indices_1_qr.png` / `_aod.png`.
- Twelve `SweepSecondHand.digits-NN` positions reset to the inward table (listed in §3 below).

---

## 2. Bulb complications

### 2.1 Slot definitions (`watch_face_concentric20.xml:171-219`)

| Slot id (original) | Name | Bounds (l,t,r,b on 456²) | Default provider | Default type |
|---|---|---|---|---|
| 0 | top bulb | `290, 50, 390, 150` | system **DATE** (no primary) | SHORT_TEXT |
| 1 | middle bulb | `340, 176, 440, 276` | Fitbit Steps offloadable → Fitbit Steps standard → system WATCH_BATTERY | GOAL_PROGRESS (fallback RANGED_VALUE) |
| 2 | bottom bulb | `290, 306, 390, 406` | system WATCH_BATTERY (no primary) | RANGED_VALUE |

All ship as `initiallyEnabled="false"` and are flipped on by the `Half_Dial_I` overlay block in `watch_face_concentric20.xml:62-92`.

`supportedTypes` for bulbs (`strings.xml`, `bulb_supported_types`):
`WEIGHTED_ELEMENTS | GOAL_PROGRESS | RANGED_VALUE | SHORT_TEXT | MONOCHROMATIC_IMAGE | SMALL_IMAGE`

### 2.2 Bounds converted to 450² canvas

| Slot | Center (456²) | Size | Center (450²) | Bounds (450², l,t,r,b) |
|---|---|---|---|---|
| top    | (340, 100) | 100×100 | (335.5, 98.7)  | `286, 49, 385, 148` |
| middle | (390, 226) | 100×100 | (384.9, 223.0) | `335, 173, 434, 273` |
| bottom | (340, 356) | 100×100 | (335.5, 351.4) | `286, 302, 385, 401` |

Vertical layout is fixed (top/center/bottom are at ~22%, ~50%, ~78% of canvas height in the original — `main.css:198-201`).

### 2.3 Bulb symbol — what it renders

From `widgets/complication.css:1-12, 30-55` and the symbol in `widgets/complication.defs:246-577`:

| Element | Default | When visible |
|---|---|---|
| `#background-disc` (full circle, opacity 0.2) | hidden | `short-text`, `monochromatic-image` |
| `#background-arc` (ring) | 360° track, arc-width 6 | always except text/image/empty/no-data/not-configured |
| `#progress-arc` | start/sweep set by code | `ranged-value`, `goal-progress`, not `comp-w-ramp` |
| `#progress-arc-overlap` | brightness 1.5, black stroke 4 | `goal-progress` only |
| `#progress-indicator` (dot) | center-left, indicator r=4, bg r=8 | `ranged-value.comp-rating` |
| `#segments` (7 gradientArc strips + start-cap) | hidden | `weighted-elements`, `comp-w-ramp` |
| `#texture-type1` | discarded everywhere | (never in Half Dial) |

Geometry (in the bulb's local frame, 92×92 default — overridden to 100×100 for Concentric in `concentric20/main.css:304`):
- `arc { arc-width: 6; width: 100%-6; height: 100%-6; x:+3; y:+3; cap: round; arc-alignment: center; }`
- Background and progress arc both `Complication.start-angle: 180, sweep-angle: 360` (with-icon: start 210, sweep 300).

**Concentric overrides** for the bulb (`concentric20/main.css:198-209, 304-306`):
- `ComplicationBulb { anchor: center-middle; width: 22%; height: 22%; }` (22% of 456 = ~100 px ✓)
- `ComplicationBulb.small arc { arc-width: 4; }` (overrides default 6)
- `ComplicationBulb.small gradientArc { arc-width: 4; }`
- `ComplicationBulb.small text { fill: color-d; }`
- `ComplicationBulb #indicator { fill: color-d; }`
- `ComplicationBulb.monochromatic-image .comp-image { fill: color-d; }`
- `ComplicationBulb.ranged-value.comp-rating #progress-indicator { x: 50%+2; y: 2; px: -2; py: 50%-2; }` (rating dot offset by +2,+2)

### 2.4 Bulb text sizes (`concentric20/main.css:308-318`)

Sized for the 100 px bulb:
- `#comp-text` (value): 32sp default, 29 with title or with icon, 19 with both title and icon, 32 for short-text with icon, 23 with `comp-layout-icon-title.short-text`.
- `#title`: 23 default, 30 in short-text with icon+title.

Font: `GoogleSansFlex-600` (replace with Roboto Flex 600 or similar in the WFF port).

### 2.5 Color roles (Half Dial bulb)

- Arc track + frame: `color-c`
- Value text, indicator dot, monochromatic image fill: `color-d`
- Title text: inherits `foreground-color` (effectively `color-d` for Concentric)

---

## 3. Second-text positions for Half Dial

The seconds layout uses the **inward** position table (shared with Dials, `main.css:270-290`). Listed in 456² values:

| Digit | x (456) | y (456) |
|---|---|---|
| 00 | 91%+1 = 415.96 | 50%   = 228   |
| 05 | 85%+3 = 390.6  | 29%+2 = 134.24 |
| 10 | 70%+3 = 322.2  | 14%+3 = 66.84  |
| 15 | 50%   = 228    | 9%    = 41.04  |
| 20 | 29%+3 = 135.24 | 14%+3 = 66.84  |
| 25 | 14%+3 = 66.84  | 29%+3 = 135.24 |
| 30 | 9%    = 41.04  | 50%   = 228   |
| 35 | 14%+3 = 66.84  | 70%+3 = 322.2  |
| 40 | 29%+2 = 134.24 | 85%+3 = 390.6  |
| 45 | 50%   = 228    | 91%+1 = 415.96 |
| 50 | 70%+4 = 323.2  | 85%+2 = 389.6  |
| 55 | 85%+2 = 389.6  | 70%+4 = 323.2  |

Convert to 450² with `* 0.9868`.

This is the same table the **Dials** layout uses; the replica already implements it as `seconds_indices_1` + the inward number positions inside `z1_mode == 1` Compare branches. **In Half Dial these inward positions are the only ones used** (since the layout is mutually exclusive with Dials+Arcs).

---

## 4. Flavors that drive this layout

From `watch_face_concentric20.xml:220-269`:

| Flavor id | Colorway | Layout id |
|---|---|---|
| `flavor_campari_half_dial` | `B29` Campari | `id_half_dial` |
| `flavor_unicorn_half_dial` | `B28` Unicorn | `id_half_dial` |

**Campari B29 palette** (`defpackage/egg.java:262-263`):
- `color-a = #FF96A9` (light pink)
- `color-b = #FF3C6B` (deep pink)
- `color-c = #FF8469` (orange-pink)
- `color-d = #FF5E3B` (orange)

**Unicorn B28 palette** (`defpackage/egg.java:260-261`):
- `color-a = #FFF6E2` (cream)
- `color-b = #D5BEAD` (tan)
- `color-c = #3BAFF0` (azure)
- `color-d = #FF5EEF` (magenta)

The replica's current 60-color palette does not contain exact matches for several of these (e.g. `#FF96A9`, `#FF3C6B`, `#FF5EEF`, `#FFF6E2`). When adding Half Dial flavors, either:
1. Extend `a0PrimaryColor` / `a1AccentColor` / `a2CompBaseColor` / `a3CompFgColor` ColorOption lists with those hex values (assigning new ids), or
2. Snap to the nearest existing palette entries and accept the drift.

---

## 5. Animations (lost in WFF, document for completeness)

`#container` (the minute pill in Half Dial mode):
- to-AOD: width `22% → 0` over 0.4s, easing `cubic-bezier(0.37, 0.54, 0.02, 1)`
- to-Interactive: width `0 → 22%` over 0.4s, easing `cubic-bezier(0.26, 0.54, 0.06, 1)`

WFF v4 only supports `LINEAR | EASE_IN | EASE_OUT | EASE_IN_OUT` interpolation on `<Variant>` width changes — exact easing is not reproducible. Use `EASE_IN_OUT` and accept a slight feel difference (same compromise as the default-mode pill).

The bulbs themselves do not animate scale/opacity differently from interactive vs ambient beyond standard hipower/lopower opacity show/hide on `#text-content`, `#text-content-aod`, `#progress-arc`, `#background-arc`. In WFF this is just `<Variant mode="AMBIENT" target="alpha" value="...">` snap-switches.

---

## 6. Suggested WFF v4 implementation sketch

1. **Add `z1_mode = 2`** to the `z1_mode` ListConfiguration with icon `mode_icon_2` (asset to create).
2. **Wrap the clock group** (the `minutes` group at `x=65 y=65 w=320 h=320` plus the `time` group with hour/minute and pill) in a parent Group whose `x` is transformed:
   ```xml
   <Transform target="x" value="[CONFIGURATION.z1_mode] == 2 ? -149 : 0" />
   ```
   (`-149 ≈ 450 × -0.33` rounded). Pivot center via the parent's own anchor.
3. **Gate the corner arc slots (0/1/2/3) off when `z1_mode == 2`.** Their existing `Variant` already checks `z1_mode == 0`; extend to `z1_mode != 1 ? 0 : 255`.
4. **Add three ROUND_RECT ComplicationSlots** (new slot ids 5/6/7, since 0-4 are taken):
   ```xml
   <ComplicationSlot slotId="5" displayName="top_bulb"
       width="100" height="100" x="285" y="49"
       supportedTypes="RANGED_VALUE GOAL_PROGRESS WEIGHTED_ELEMENTS SHORT_TEXT MONOCHROMATIC_IMAGE SMALL_IMAGE">
       <BoundingBox width="100" height="100" x="0" y="0" />
       <Variant mode="AMBIENT" target="alpha"
                value="[CONFIGURATION.z1_mode] == 2 ? ([CONFIGURATION.z3_aod_compl] ? 255 : 0) : 0" />
       <Transform target="alpha"
                  value="[CONFIGURATION.z1_mode] == 2 ? 255 : 0" />
       <!-- Complication branches: SHORT_TEXT, RANGED_VALUE, GOAL_PROGRESS, MONOCHROMATIC_IMAGE, ... -->
   </ComplicationSlot>
   ```
   Repeat for middle (`x=335 y=173`) and bottom (`x=285 y=302`).
5. **Bulb render** — inside each Complication branch, draw:
   - background circle Arc: `centerX=50, centerY=50, width=96, height=96, startAngle=180, endAngle=540` (sweep 360), stroke `a2CompBaseColor` thickness 4, cap ROUND.
   - For RANGED_VALUE/GOAL_PROGRESS: progress Arc with `endAngle` driven by `180 + 360 * (value-min)/(max-min)`, stroke `a3CompFgColor` thickness 4.
   - For SHORT_TEXT: omit the background-arc; show a filled background-disc `Ellipse` with alpha ~50 instead (matches `background-disc { opacity: 0.2 }` in the original).
   - Center text: PartText 32sp `inter_medium` (or pick a flex font), `a0PrimaryColor`.
   - With-icon variant: omit the 60° top slice (`startAngle=210, endAngle=510` ≈ sweep 300) and render the monochromatic image at the top.
6. **Pill width** — add a Variant on the pill `width` driven by both `z2_aod_pill` and `z1_mode`:
   - `z1_mode == 2` ? (interactive `22% = 99`, ambient `0`) : existing logic.
   The pill is also `fill-opacity: 0.5` filled with `color-b` in Half Dial — set the pill's inner RoundRectangle Fill to `a1AccentColor` at alpha 128 in that mode.
7. **Hour size shrink** — wrap the hour PartText size in a Configuration-aware expression, or duplicate two PartText branches inside a `<Condition>`: 90sp default vs 80sp (90 × 100/112) for `z1_mode == 2`.
8. **Add `flavor_campari_half_dial` and `flavor_unicorn_half_dial`** to the `<Flavors>` block, pointing at `z1_mode = 2` and the new palette indices.

---

## 7. Things to verify on device after porting

- Bulb sizing — original ships at 100 px on 456 canvas. After 0.9868 scaling, the bounding boxes are 98.7×98.7 — round to 99 or 100 and visually check.
- Tap regions — `ROUND_RECT` boundsType auto-generates a circular tap shape on round watches; confirm bulb taps hit the right slot.
- Pill animation feel — width-only animation reads differently than the original's combined width+opacity crossfade; consider adding an `opacity` Variant alongside if it looks abrupt.
- AOD behavior of bulbs — original's `#background-arc` fades out and `#text-content-aod` fades in; in WFF you'll typically just snap-show the text-only version.
- Color drift from palette gaps (Campari `#FF96A9`/`#FF3C6B`/`#FF5EEF` and Unicorn `#FFF6E2`/`#FF5EEF` are not present in the current 60-option list). Decide whether to extend the palette or accept the closest neighbors.

---

## 8. Files that need touching

- `app/src/main/res/raw/watchface.xml` — add `z1_mode` option 2, wrap clock in transform group, gate slots 0-3 by `z1_mode != 1`, add slots 5-7, add two flavors.
- `app/src/main/res/values/strings.xml` — add `mode_2`, `flavor_campari_half_dial`, `flavor_unicorn_half_dial`, bulb slot display names.
- `app/src/main/res/drawable-nodpi/` — add `mode_icon_2.png` for the editor.
- (Optional) Extend the 60-option ColorConfiguration lists with the Campari/Unicorn hex values that don't already have a match.
