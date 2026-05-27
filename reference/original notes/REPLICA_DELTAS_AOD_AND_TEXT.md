# AOD arc behavior + text-rendering deltas

Third companion to `REPLICA_DELTAS.md`. Covers:
- What's actually happening when corner arcs appear to thin out in AOD.
- Text-rendering subtleties not covered in §5 of the main deltas doc: letter spacing, text length, auto-shrink, AOD-specific text, ellipsis, render mode.
- Anchor/pivot conventions.

---

## 1. "Arcs shrink to a fine line in AOD" — investigated

### 1.1 What the original does

The original Concentric does **not** intentionally animate `arc-width` between interactive and AOD.

The widget definitions in `Pixel Watch Face/app/src/main/assets/widgets/complication.defs` declare per-arc animation hooks:

```xml
<animate attributeName='arc-width' class='anim-hipower anim-arc-width'/>
<animate attributeName='arc-width' class='anim-lopower anim-arc-width'/>
```

These hooks become live animations only when a watch face's CSS provides `from`/`to` values for the matching `.anim-hipower.anim-arc-width` / `.anim-lopower.anim-arc-width` selectors. **None of `concentric20/main.css` does** (grepped — no `anim-arc-width` rules in concentric20, rushhour, modular, expedition, boldutility20, artsandculture, or reveal). Rushhour and modular animate scale and opacity, but not arc-width.

So in the original, the corner arc track stays at `arc-width: 4` in both interactive and AOD. Only opacity (`anim-opacity-show` / `anim-opacity-hide`) and indicator scale (`anim-indicator-scale`) change.

### 1.2 What you're seeing in the replica

I grepped for any `<Variant target="thickness">` in `app/src/main/res/raw/watchface.xml`. **No matches** — the replica doesn't explicitly animate stroke thickness on AOD either.

So neither side intentionally shrinks arcs. The visual "shrink to a fine line" you observe in AOD is one of these three things:

1. **Wear OS system burn-in protection.** Wear OS applies a low-color, low-luminance overlay to all watch faces in AOD by default. This dims and quantizes colors; thick antialiased strokes may appear thinner because their edge gradients get rounded down. Standard Wear OS behavior — not something the watch face XML can prevent.
2. **The indicator/halo merging into the background.** The replica's progress indicator at the value angle is a 12-thick colored arc *on top of* a 20-thick black halo (`indicator_background`, `color="#000000"`). In AOD, the black halo blends into the dark scene background (or `#333333` system AOD background), and the colored 12-thick indicator gets dimmed. Net visual: the thick "pill" reduces to a thin colored sliver while the underlying placeholder track (`#33ffffff` × `a2CompBaseColor`, thickness 4) remains. Looks like shrinkage.
3. **A WatchFace Studio AOD optimization layer.** WFS-exported faces sometimes wrap AOD elements in additional Variants that reduce strokes. None present in this replica's source, so this isn't the cause here.

### 1.3 What to do

If you want the AOD arc to keep the full interactive thickness — explicitly preserve it:

```xml
<!-- Inside the indicator PartDraw, force thickness in AOD -->
<Variant
    mode="AMBIENT"
    target="thickness"
    value="12" />
```

Wait — `Stroke.thickness` is not on the `<Variant>`-targetable list directly. `<Variant>` targets attributes on the *parent* element (e.g. `alpha`, `scaleX`, `width`, `x`, `y`, `angle`), not on nested children like `<Stroke>`. To force thicker arcs in AOD you have two real options:

- **Option A — Force AOD opacity high enough.** Add a `<Variant>` that bumps the indicator's parent PartDraw alpha to 255 in AOD (already 255 by inheritance — but verify no parent Variant is reducing it). This keeps the colored indicator at full opacity; perceived shrinkage is then purely the system overlay.
- **Option B — Duplicate the indicator as an AOD-only "thick" version.** Add a second PartDraw with `<Variant mode="AMBIENT" target="alpha" value="255">` and `<Transform target="alpha" value="0">` in interactive — drawing a heavier stroke (e.g. thickness 14) only in AOD. Costly in element count; only worth it if the system-overlay shrinkage is genuinely unreadable.

**Recommendation:** confirm on device that this isn't pure Wear OS AOD dimming. Take a screenshot in interactive and AOD; measure pixel widths. If the colored indicator pixel count is identical and only the brightness differs, you're seeing Wear OS dim — leave it alone, no XML can fix system behavior. If the pixel count actually drops, the cause is element-level (Variant or color blending).

### 1.4 What to add to the main deltas doc

Add to `REPLICA_DELTAS.md` §7 (AOD & animations):

> **"Arcs shrinking in AOD" is not faithful behavior of the original.** Original Concentric keeps `arc-width: 4` constant across interactive and AOD; it only fades opacity and scales the indicator dot (`anim-indicator-scale: 1 → 0.6` on bulbs, no equivalent on arcs). If the replica's arcs appear thinner in AOD, the cause is most likely Wear OS's system AOD overlay quantizing the thick stroke + black halo (`indicator_background`) blending into the dark background. Not a code defect; either accept or duplicate the indicator as a thicker AOD-only version per §1.3 above.

---

## 2. Text rendering — gaps not covered in earlier deltas

The main deltas doc treats hour/minute size and font family in §1 but doesn't audit letter spacing, text-length truncation, auto-shrink, AOD-specific text rendering, ellipsis, render mode, or text uppercasing. Below is the full audit.

### 2.1 Letter spacing

| Element | Original | Replica | Delta |
|---|---|---|---|
| Hour digit | `letter-spacing: -1` (`main.css:135`) | (none — default 0) (`watchface.xml:3172-3180`) | ⚠ replica is looser; original is tighter |
| Minute digit (pill) | `letter-spacing: 0` (`main.css:138`) | (none — default 0) (`watchface.xml:3152-3160`) | ✓ |
| Rotating minute "00..55" | `letter-spacing: 1` (`main.css:77`) | (none — default 0) (`watchface.xml:1357-1364` etc) | ⚠ replica is tighter; original is looser |
| Rotating second "00..55" | inherited from `.watchtext text` = `letter-spacing: 1` | (none) | ⚠ same |
| Complication value (arc) | (none on `textArc`) | (none on `TextCircular`) | ✓ |

WFF `<Font letterSpacing="...">` takes a float in **EM units** (not pixels — see schema docs at `fontElement.xsd:38-46`). To match the original's `letter-spacing: -1` (pixels) on a 112sp hour you'd compute `-1 / 112 ≈ -0.009` EM. So:

```xml
<Font family="inter_medium" size="90" letterSpacing="-0.009">
```

For the rotating digits at `letter-spacing: 1` (positive 1px on 20sp original = `1/20 = 0.05` EM):

```xml
<Font family="inter_regular" size="24" letterSpacing="0.05">
```

The visual delta isn't huge but it does affect kerning consistency with the original.

### 2.2 Text-length truncation

| Element | Original | Replica |
|---|---|---|
| Hour digit | `text-length: 4` (caps at 4 chars) | no equivalent |
| Minute digit | `text-length: 4` | no equivalent |
| Rotating "00..55" | `text-length: 4` | no equivalent |
| Complication value text | `text-length: 32` curved | no equivalent |
| Complication title text | `text-length: 28` (bulb) | no equivalent |

WFF doesn't have a generic "truncate at N chars" attribute. `<Text ellipsis="TRUE">` is the only related control and only applies when the rendered string overflows the PartText width. The replica uses `ellipsis="TRUE"` on slot 4 only (`watchface.xml:6747, 6807, 6829, 7895, 7955, 7977`); the corner arc complications don't ellipsize.

**Risk:** a complication provider returning a 50-character SHORT_TEXT will overflow the curved text and either get clipped at the BoundingArc edge or wrap. Test with a "Now Playing" complication providing a long song title to verify behavior.

### 2.3 Auto-shrink (font-step-size / min-font-size)

| Element | Original |
|---|---|
| `ComplicationBulb text` | `font-step-size: 1; min-font-size: 10;` (`complication.css:5`) |
| `ComplicationBulb #comp-text` (various sizes) | `min-font-size: 14/18` |

The original's bulb text auto-shrinks down to a per-element minimum if the string overflows. **WFF v4 has no equivalent** (grepped — no `minFontSize`, `stepSize`, `autoSize` attributes in any element schema). The closest is `ellipsis="TRUE"` which truncates instead of shrinking.

Since bulbs are out of scope for Dials/Dials+Arcs, this only matters if you ever do a third layout. Not actionable now.

### 2.4 AOD-specific text rendering

Original has separate `#text-content` and `#text-content-aod` containers per complication (`widgets/complication.defs:544-571, 695-722`), each with their own text element. The active one fades in via `anim-opacity-show`. They could in principle have different font sizes, fonts, or styling — but in Concentric they don't (both use `GoogleSansFlex-600`, same size).

The point: the original has a *plumbing* split between interactive and AOD text, so per-mode text styling is trivial to add. The replica renders one text element per complication and gates the whole thing via the slot-level AMBIENT Variant.

**If you ever want AOD text to use a thinner weight for power savings** (a common watch-face technique), you'd need to:
- Duplicate the text PartText into an interactive version + AOD version
- Add reciprocal Variants on each (`<Variant target="alpha" mode="AMBIENT" value="0">` on the interactive one, `value="255"` on the AOD one)

Not urgent but worth knowing the original supports this and the replica doesn't.

### 2.5 Text-transform

| Element | Original | Replica |
|---|---|---|
| Complication value (bulb) | `text-transform: uppercase` (`complication.css:5`) | n/a — bulb out of scope |
| Complication value (arc) | `text-transform: uppercase` (`complication.css:259`) | `<Upper>` element wrapper (`watchface.xml:3397, 3597, etc.`) | ✓ functionally equivalent |
| Slot 4 text/title | n/a in original | `<Upper>` (`watchface.xml:7900-7905, 7960-7965`) | ✓ |

The replica's use of `<Upper>` is the WFF idiom for uppercasing. Matches the original.

### 2.6 Ellipsis

| Element | Original | Replica |
|---|---|---|
| Complication value (arc) | `text-overflow: ellipsis` | no ellipsis attribute on `TextCircular` | ⚠ |
| Slot 4 text/title | n/a | `<Text ellipsis="TRUE">` | ✓ |

`TextCircular` may not support `ellipsis` attribute — confirm against schema before changing.

### 2.7 RenderMode MASK

The replica uses `renderMode="MASK"` on the curved complication text (`watchface.xml:3986, 4854, 5722, 6590`) — drawn through a `<PartDraw>` with a `Rectangle` filled by `[CONFIGURATION.a0PrimaryColor]`. This is the standard WFF trick to tint a `TextCircular` (which has no `tintColor`).

The original doesn't need this — Koru's `fill: color-a` works directly on `textArc`. The mask approach is correct and necessary in WFF.

**Quirk:** `renderMode="MASK"` is one of only two values in the schema (`MASK | SOURCE`), confirmed at `reference/wff-schema/v4/group/renderModeType.xsd:21-22`. No surprises.

---

## 3. Anchor / pivot semantics — silent delta

### 3.1 Original

CSS uses `anchor: center-middle` extensively (`main.css:6, 18, 20, 76, 145 (center-left), 151 (center-left), 181, 203`). This positions elements so that `x`/`y` refer to the element's geometric center (or left edge for center-left), not its top-left corner.

### 3.2 Replica

WFF `Group`, `PartText`, `PartImage`, `PartDraw` use **top-left origin by default**. `x`/`y` always refer to the top-left corner of the element's bounding box. There's no `anchor` attribute in WFF v4.

To center an element you must subtract half its dimensions from the desired center coordinate. The replica does this manually (e.g. minute-numbers group at `x=65 y=65` with `width=320 height=320` to center a 320×320 region within 450 → `(450-320)/2 = 65` ✓).

### 3.3 What this affects

Mostly nothing — the replica's hand-computed coordinates are correct. But two situations would silently bite:

1. **Adding new centered elements.** If you copy code from the original (which assumes center-middle) without converting to top-left, your element will be placed offset. Always subtract `width/2`, `height/2` from the original center.
2. **Pivot points for rotation.** WFF Group uses `pivotX`/`pivotY` for rotation pivot (defaults to 0,0 = top-left). To rotate around a center you must set `pivotX="width/2" pivotY="height/2"` explicitly. The replica's minute-numbers group uses no `pivotX`/`pivotY` but instead places the parent at `x=65 y=65` with size 320×320 — and the `<Transform target="angle">` rotates around the parent's own pivot. The default pivot (0,0) means rotation happens around the top-left of the rotating group, which is the canvas point (65,65). Since 65 is the corner of the 320×320 inscribed circle whose center IS the canvas center (225,225)... wait that's actually wrong. The minute ring should rotate around the **canvas center** (225,225), but with pivot at (0,0) of a group placed at (65,65), it rotates around (65,65).

Let me verify the math: a group at x=65, y=65, w=320, h=320 with default pivot=(0,0). When you set `angle=30`, the entire group rotates around its own local (0,0), which is canvas (65,65). That would rotate around the top-left corner of the inscribed ring, not the center.

**Potential bug:** the minute-numbers ring may be rotating around the wrong pivot. Run the watch face and watch the rotation pattern at e.g. minute 15 (should be 90° rotation). If the labels swing through an arc that's offset from the canvas center, the pivot is wrong — fix by adding `pivotX="160" pivotY="160"` (half of 320) to the minutes Group at `watchface.xml:1241`.

(Or — if it visually looks correct, then either WFF interprets pivot defaults differently than I'm assuming, OR the group's `scaleX/scaleY` Transforms are also pivoting around 0,0 and the visual cancels out. Verify on device.)

### 3.4 What to add to the main deltas doc

Add to §4 (dial geometry) of `REPLICA_DELTAS.md`:

> **Pivot check needed.** Minutes group at `watchface.xml:1241` has no explicit `pivotX`/`pivotY`. WFF default is (0,0) = top-left of the group, which would rotate the ring around canvas (65,65) instead of canvas center (225,225). Test on device; if the ring swings asymmetrically, add `pivotX="160" pivotY="160"`.

---

## 4. Other things I checked and found nothing new

Briefly, so we know they were considered:

- **`paint-order: stroke`** on the original `#progress-arc-overlap` (`complication.css:37, 206`) — only used on `goal-progress`, which the replica handles as `ranged-value`. Not a delta in the Dials/Dials+Arcs scope.
- **`text-anchor: middle` / `start` / `end`** — WFF uses `align="START | CENTER | END"` (default CENTER on PartText). Replica relies on defaults; original explicitly sets `text-anchor: middle` for hour/minute. Same effective result.
- **`fill-rule`, `paint-order`** — not exposed in WFF.
- **`text-buffer`** — Koru-specific; WFF uses `<Template><Parameter expression="..."/></Template>` or direct text content. Replica uses both forms correctly.
- **`opacity` vs `alpha`** — 0.0-1.0 vs 0-255. All conversions verified in §1 of `REPLICA_DELTAS_SIZES.md`.
- **`cap: round` vs `cap="ROUND"`** — same semantics; replica uses ROUND consistently.
- **Image PartImage `tintColor` channel mixing** — both engines tint by multiplying the source alpha (or value) by the tint color; identical results for monochromatic source PNGs (which the tick rings are).

---

## 5. Summary: what to add to the main deltas doc

Three new items belong in `REPLICA_DELTAS.md`:

1. **§7 (AOD):** "Arc shrinkage in AOD is not faithful to the original — the original keeps arc-width constant. Cause is Wear OS system overlay + indicator/halo blending; not fixable in XML beyond duplicating the indicator as an AOD-only thicker version. See `REPLICA_DELTAS_AOD_AND_TEXT.md` §1."

2. **§1 (fonts/type):** "Letter spacing not yet matched — original uses `-1px` on hour (`letterSpacing="-0.009"` in WFF EM units) and `+1px` on rotating digits (`letterSpacing="0.05"`). Replica is at 0. Visual: replica hour is slightly looser, rotating digits slightly tighter."

3. **§4 (dial geometry):** "Pivot check needed on the minutes Group — WFF default pivot is (0,0) = group's top-left, which would rotate the ring around canvas (65,65) rather than (225,225). Verify on device; if asymmetric, add `pivotX='160' pivotY='160'`."

Two should also slot into the priority fixes list:

- **#7.5 (between Optional pill fill and Optional ring scale)**: Add `letterSpacing="-0.009"` to the hour Font and `letterSpacing="0.05"` to the rotating digit Fonts.
- **#7.6**: Verify and fix the minutes Group pivot.
