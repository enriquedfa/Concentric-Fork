# Concentric 2.0 — Full Research Report

## 1. What it actually is in this APK

It is **not** a Watch Face Format (WFF) face. It runs on Google's proprietary **"Pele/Koru" runtime** — an SVG-like view system that the Pixel Watch loads as a binary library (`pele-runtime`). Any port has to recreate the visuals from scratch in WFF; the existing files are just a reference.

| Source of truth | Path |
|---|---|
| Service class | `com.google.android.wearable.watchface.orbita.OrbitaWatchFaceService` (label "Concentric 2.0") |
| Layout XML (slots/styles) | `app/src/main/res/xml/watch_face_concentric20.xml` |
| Koru view tree | `app/src/main/assets/concentric20/main.view` |
| Koru styles | `app/src/main/assets/concentric20/main.css`, `theme.css` |
| Symbols | `assets/concentric20/symbols/analog_clock.defs`, `complications.defs` |
| PNG assets | `assets/concentric20/images-456x456/` (and `-408x408/`) |
| Slot IDs | `res/values/integers.xml:38-44` |
| Localized names | `res/values/strings.xml:152-159` |
| Shared colorway list | `res/xml/set_b_colorway.xml` |
| Preview | `res/drawable-nodpi/preview_concentric20.png` |
| Porting note | `GALAXY_WATCH_PORTING_NOTES.md:46, 137` |

## 2. Visual anatomy (456 × 456 reference canvas)

The face uses one cardinal canvas at 456×456 (`main.css:6, 241`) and a separate set of assets at 408×408 for smaller displays.

Layers stacked back-to-front:

1. **Black background** (`#000000`; ambient: `#333333`, theme.css:5-7).
2. **Outer second-tick ring** — rotates with seconds. Constructed from one PNG quarter (`seconds_indices_0_qr.png` 226×230) repeated 4 times at 0/90/180/270° to save memory. Two variants:
   - `seconds_indices_0_qr.png` — coarser, used in **DialsAndArcs**.
   - `seconds_indices_1_qr.png` — denser, used in **Dials** and **Half Dial I**.
3. **Inner minute-tick ring** — same trick with `minutes_indices_qr.png` (150×158, 308×308 logical). Rotates with minutes; opacity → 0 in AOD.
4. **12 rotating minute labels** ("00", "05", … "55") laid on a sweep-minute hand, each in its own counter-rotating text widget so they stay upright. Positions hard-coded per label (`main.css:94-105`).
5. **12 rotating second labels** ("00".."55") with two position tables — one for the default radius (`main.css:120-131`) and one slightly inward when the denser tick image is used (`main.css:278-289`).
6. **Big hour digit** — 112sp, font `GoogleSans-Clock-500`, format `%02d`, anchored at 48% / 58%+2 (`main.css:135, 253`).
7. **Minute pill (right side)** — a stadium-shaped capsule that contains the live minute digits.
   - Stadium is built from two semicircle arcs (`#left-arc` start 180° sweep 180°, `#right-arc` start 0° sweep 180°) plus two horizontal stroke rects on top/bottom; `arc-width: 4`, fill = `color-b` at 0.5 opacity.
   - Width animates open/close on AOD↔interactive transitions (29% → 0 going to AOD, reverses going back). Easing `cubic-bezier(0.37,0.54,0.02,1)`.
   - In AOD a separate **circle pill** (`#container-aod`, just one full-circle 4px arc) takes over.
   - `#rect-mask` and `#circle-mask` (background-colored) sit underneath the pill so the minute ticks/labels don't bleed through.
8. **Complications** — one of three layouts, picked by user style.

## 3. Three complication layouts

User-style id `COMPLICATIONS_LAYOUT_STYLES` (default index = 1) toggles the three options. The mapping between the watch-face XML and the CSS `@media` selectors is sensitive: the CSS converts spaces to underscores, so `"Half Dial I"` → `Half_Dial_I` in the media query.

### 3.1 DialsAndArcs (default, 4 corner arcs)
- 4 EDGE complications, one per quadrant.
- Each is a thin arc that hugs the inside edge of the second-ring; arc-width 4.
- Per-quadrant geometry (`main.css:189-196`):
  - q1 (NE): `start-angle 15° sweep 55°`
  - q2 (SE): `start-angle 23° sweep 55°`
  - q3 (SW): `start-angle 15° sweep 63°`
  - q4 (NW): `start-angle 15° sweep 63°`
- Highlight (when an editor selects one) uses `arc-width: 40, sweep 63°` (q1/q2 → 55°), `main.css:323-325`.
- Slot bounds in the XML are 228×228 quadrant rectangles inside the 456×456 canvas (NE = top-right quadrant, etc.).

### 3.2 Dials (clean, no complications)
- `ComplicationArc { display: discard }` and bulbs already discarded → only the clock.
- Switches the seconds image to the denser version (`seconds_indices_1_qr.png`) and uses the inward second-label position table.

### 3.3 Half Dial I (3 round bulbs on the right)
- The whole `AnalogClockContainer` shifts left by 33% (`main.css:294`).
- 3 ROUND_RECT bulb complications stacked along the right side at 75%/22%, 89%/50%, 75%/78%, each 22%×22% (100×100 px) (`main.css:198-202, 304`).
- Pill capsule narrows from 27% → 22% width.
- Hour digit shrinks from 112 → 100sp.
- Same denser seconds image as Dials.

## 4. Complication slots (full table)

`watch_face_concentric20.xml:95-219` and `integers.xml:38-44`.

| Slot ID | Name (string) | Bounds type | Bounds (l,t,r,b on 456²) | Default data source | Default type | Used in layout |
|---|---|---|---|---|---|---|
| 3 | Northeast | EDGE | 228,0,456,228 | `…weather…CWComplicationService` (Pixel Weather), fallback weather, then watch battery | RANGED_VALUE | DialsAndArcs |
| 4 | Southeast | EDGE | 228,228,456,456 | Fitbit heart rate (offloadable → standard fallback) | RANGED_VALUE / SHORT_TEXT | DialsAndArcs |
| 5 | Southwest | EDGE | 0,228,228,456 | Fitbit distance | GOAL_PROGRESS / RANGED_VALUE | DialsAndArcs |
| 6 | Northwest | EDGE | 0,0,228,228 | Fitbit Active Zone Minutes | GOAL_PROGRESS / RANGED_VALUE | DialsAndArcs |
| 0 | Top bulb | ROUND_RECT | 290,50,390,150 | (system DATE) | SHORT_TEXT | Half Dial I |
| 1 | Middle bulb | ROUND_RECT | 340,176,440,276 | Fitbit Steps | GOAL_PROGRESS | Half Dial I |
| 2 | Bottom bulb | ROUND_RECT | 290,306,390,406 | (system WATCH_BATTERY) | RANGED_VALUE | Half Dial I |

Supported types come from shared strings (`strings.xml:89, 125`):
- Arc slots: `WEIGHTED_ELEMENTS|GOAL_PROGRESS|RANGED_VALUE|SHORT_TEXT`
- Bulb slots: same plus `MONOCHROMATIC_IMAGE|SMALL_IMAGE`

All slots ship as `initiallyEnabled="false"` and the appropriate `ComplicationSlotOverlay` blocks in each `ComplicationSlotsOption` flip them on for the matching layout (XML lines 16-92). Editor scale is `complicationScaleX/Y = 456`.

## 5. Color system

Each colorway exposes **four roles**: `color-a`, `color-b`, `color-c`, `color-d`. They are bound at runtime by the Pele/Koru theme system — they are *not* declared in any XML in this APK; the swatch drawables (`ic_colorwaybulb_b##.xml`) are single-color preview dots only. The theme.css fallback (used when no theme matches) is:

```
color-a #9da8b2   color-b #b6d7fc   color-c #5a636b   color-d #6e869c
```

Roles by element:
| Element | Color role |
|---|---|
| Hour text, second tick ring | `color-a` |
| Minute tick ring, minute pill, minute text | `color-b` |
| Complication arc/bulb body, frame | `color-c` |
| Complication value text, indicators, image fill | `color-d` |

The 6 named flavors in `watch_face_concentric20.xml:220-269` (preview-swatch hex shown for reference only — the real 4-tone palette lives in the runtime):
| Flavor | Color id | Swatch hex | Layout |
|---|---|---|---|
| flavor_glacier_dials | B05 Glacier | `#2295ff` | Dials |
| flavor_pebble_dials | B02 Pebble | `#878378` | Dials |
| flavor_ooze_dials_and_arcs | B20 Ooze | `#84d5c1` | DialsAndArcs |
| flavor_jester_dials_and_arcs | B18 Jester | `#7092ea` | DialsAndArcs |
| flavor_campari_half_dial | B29 Campari | `#ff5e3b` | Half Dial I |
| flavor_unicorn_half_dial | B28 Unicorn | `#fff6e2` | Half Dial I |

The full B-series colorway list (B01–B48 minus a few gaps, plus C01–C08) is defined once in `res/xml/set_b_colorway.xml` and shared across many faces.

## 6. Animations

Both rings and the pill morph between AOD and interactive states. Durations and easings, summarized:
- Minute ring: opacity 0.333s `cubic-bezier(0.47,0.17,0.43,1)`, scale 1→0.97 / 0.90→1 over 0.4s.
- Seconds ring: identical pattern, slightly delayed.
- Pill (`#container`): width 29% ↔ 0 over 0.4s; opacity 1↔0; AOD uses circular pill instead.
- Masks (`#rect-mask`, `#circle-mask`) toggle so digits don't bleed through.

These are Koru-specific animation tags (`begin: toAmbient` / `toInteractive`) — WFF has its own AOD machinery so you'll re-implement, not port.

## 7. Mapping to Watch Face Format v4

Recommended structure for a clean WFF v4 rebuild:

```
WatchFace width="450" height="450"
└── Scene
    ├── Group ("background")              ← black fill rect
    ├── Group ("ticks")
    │   ├── PartImage of seconds_indices_0/1_qr.png (full 450)
    │   │   └── Transform target=ROTATE expression="[SECOND] * 6"
    │   └── PartImage of minutes_indices_qr.png  (≈304-scaled)
    │       └── Transform target=ROTATE expression="[MINUTE] * 6 + [SECOND]/10"
    ├── Group ("rotating-minute-labels")  ← AnalogClock OR a Group of 12 PartTexts
    │   each PartText counter-rotated so labels stay upright
    ├── Group ("rotating-second-labels")  ← same trick for seconds
    ├── PartText (hour)                   ← format %02d, 112sp, GoogleSans-Clock-500
    ├── Group ("minute-pill")
    │   ├── PartDraw stadium (two arcs + two rects, stroke 4) using <ArcShape>
    │   └── PartText (minute)
    └── Group ("complications") with ConfigurationOptionId switch on layout
        ├── option=DialsAndArcs:  4 × ComplicationSlot with EDGE bounds (RANGED_VALUE arc style)
        ├── option=Dials:         no complication content
        └── option=HalfDialI:     3 × ComplicationSlot with ROUND_RECT bounds, clock translated by ≈-66 px
```

WFF-specific notes:
- Convert canvas to **450×450** (WFF requires this) and rescale all coordinates by `450/456 ≈ 0.9868`. Slot bounds become e.g. NE = `225,0,450,225`.
- WFF declares complications inside `<ComplicationSlots>`/`<ComplicationSlot>` with `boundsType="EDGE"` or `"ROUND_RECT"`. Arc-style rendering is provided automatically when `defaultProviderPolicy` returns RANGED_VALUE/GOAL_PROGRESS — but you can also wrap a slot in a `<BoundingArc>` (WFF v3+) to render it along a real arc instead of a quadrant rectangle.
- The 4-color theme maps cleanly to a `UserStyleSetting` of type `ListConfiguration` whose options each hold four `<ColorConfiguration>` entries (color-a..d). Reference them in styles via `[CONFIGURATION.colorA]` placeholders.
- The dial/half-dial/dials+arcs toggle is a second `ListConfiguration` whose options gate `<Variant>` blocks (or visibility expressions).
- AOD is handled by `<Variant mode="AMBIENT">` — recreate the opacity/scale crossfades there. WFF doesn't have keyframed cubic-beziers for layer animations; you'll lose the polished open-pill morph and just snap-swap.
- Reuse the existing PNG ticks in `assets/concentric20/images-456x456/` (rescale to 450) — they're already alpha-only so you can tint them with `tintColor`.
- The "12 rotating digits" effect can be done two ways in WFF:
  1. 12 individual `PartText` elements positioned around the dial, each with a `Transform target=ROTATE` whose expression is `[MINUTE]*6` and a counter-rotate on the text inside, OR
  2. a single `PartImage` of pre-rendered numbers as a circular strip rotated by the minute (cheaper).

## 8. Things to double-check before shipping

- **License/IP** — `theme.css` references "color-a..color-d" sourced from a Google color system; copying the exact 4-tone palettes from a flavor named "Glacier"/"Campari"/etc. could be a trademark issue. Pick your own palettes or treat the names as placeholders.
- **Fonts** — `GoogleSans-Clock-500` and `GoogleSans-Clock-600` are Google-internal; substitute a permissibly licensed font (Roboto Flex with the same weight/letter-spacing reads similarly).
- **Tick PNGs** — they live in `app/src/main/assets/concentric20/images-456x456/` and are reusable from a layout standpoint, but the artwork is Google's; safer to redraw.
- **Half_Dial_I id** — keep the option's `app:id` value as the literal string `"Half Dial I"` only if your WFF parser tolerates spaces; cleaner to use `half_dial_i`.
- **Slot 0 (top bulb) and 2 (bottom bulb)** ship with no `primaryDataSource`/`secondaryDataSource` — they fall back to the system providers (DATE, WATCH_BATTERY). Mirror that in WFF with `<DefaultProviderPolicy systemProvider="DATE"/>` etc.

That's the entire face. Start from `assets/concentric20/main.view` + `main.css` as the geometric spec and `watch_face_concentric20.xml` as the slot/style spec — those two files together are the complete blueprint.
