# WFF v4 schema findings — what's actually possible for the deltas

Companion to `REPLICA_DELTAS.md`. Based on the vendored XSD at
`reference/wff-schema/v4/`. Use this to revise the "impossible / WFF ceiling"
claims and unblock the smooth animations and the gradient arcs.

---

## 1. CUBIC_BEZIER easing IS supported on both `<Variant>` and `<Animation>`

**Schema:**

- `reference/wff-schema/v4/common/variant/variantElements.xsd:69-85`
- `reference/wff-schema/v4/common/animationElement.xsd:38-49`

Both elements accept `interpolation` values of `LINEAR | EASE_IN | EASE_OUT | EASE_IN_OUT | OVERSHOOT | CUBIC_BEZIER`, plus a `controls` attribute taking four floats (`vector4fType`, e.g. `"0.47 0.17 0.43 1.0"`) when `interpolation="CUBIC_BEZIER"`.

This contradicts `REPLICA_DELTAS.md` §7 and §10 — the cubic-bezier crossfades from the original watch face **can** be reproduced.

### 1.1 The original Concentric easings, ported

| Original Koru animation | WFF equivalent on `<Variant mode="AMBIENT">` |
|---|---|
| `cubic-bezier(0.47, 0.17, 0.43, 1.0)` ring opacity fade | `interpolation="CUBIC_BEZIER" controls="0.47 0.17 0.43 1.0"` |
| `cubic-bezier(0.37, 0.54, 0.02, 1.0)` pill width close | `interpolation="CUBIC_BEZIER" controls="0.37 0.54 0.02 1.0"` |
| `cubic-bezier(0.26, 0.54, 0.06, 1.0)` pill width open / ring scale | `interpolation="CUBIC_BEZIER" controls="0.26 0.54 0.06 1.0"` |
| `cubic-bezier(0.57, 0, 0.53, 0.83)` opacity fade-in | `interpolation="CUBIC_BEZIER" controls="0.57 0 0.53 0.83"` |

### 1.2 Variant timing model (important gotcha)

`<Variant>` `duration` and `startOffset` are **normalized to [0.0, 1.0]**, not seconds. The vendor decides the actual ambient-transition length, and your value is scaled proportionally.

```xml
<Variant
    mode="AMBIENT"
    target="alpha"
    value="0"
    interpolation="CUBIC_BEZIER"
    controls="0.47 0.17 0.43 1.0"
    duration="0.83"
    startOffset="0.17" />
```

If the vendor's AOD transition is 800ms, this animates over 0.83 × 800 = 664ms, starting at 0.17 × 800 = 136ms in. So you can stagger AOD reveals (matching the original's "second-ring delay" behavior) but can't pin exact ms.

### 1.3 Continuous, ms-precise animations: use `<Transform>` + `<Animation>`

For non-AOD timed animations, `<Transform>` accepts a nested `<Animation>` whose `duration` is in seconds (`reference/wff-schema/v4/common/transform/transformElements.xsd:23-28`, `common/animationElement.xsd:76-82`):

```xml
<Transform target="alpha" value="ANIMATION_VALUE">
    <Animation
        duration="0.333"
        interpolation="CUBIC_BEZIER"
        controls="0.47 0.17 0.43 1.0"
        repeat="0" />
</Transform>
```

`Animation` also has `fps` (default 15) and `repeat` (-1 for infinite).

### 1.4 What this changes in the delta doc

- **Remove** "keyframed cubic-bezier per-layer animations is a hard ceiling" from §10.
- **Remove** "snap-switch is the only honest WFF answer" from §7.
- **Add** to priority fixes: replace the minute-pill `interpolation="EASE_IN_OUT"` (line ~3126) with `interpolation="CUBIC_BEZIER" controls="0.26 0.54 0.06 1.0"` for the open direction and a second `Variant` with `controls="0.37 0.54 0.02 1.0"` for the close, if you want pixel-faithful pill morph.
- **Add** ring scale Variants. The original animates the minute ring `1 → 0.97` going to AOD and `0.90 → 1` returning. Add `<Variant mode="AMBIENT" target="scaleX" value="0.90" interpolation="CUBIC_BEZIER" controls="0.26 0.54 0.06 1.0">` (and matching `scaleY`) to the `minutes` Group at `watchface.xml:1241`. `Group` supports Variant (`reference/wff-schema/v4/group/groupElement.xsd:52`).

---

## 2. Gradient arcs and weighted strokes ARE supported

**Schema:**

- `reference/wff-schema/v4/group/part/draw/style/weightedStrokeElement.xsd`
- `reference/wff-schema/v4/group/part/draw/gradient/sweepGradientElement.xsd`
- `reference/wff-schema/v4/group/part/draw/style/strokeElement.xsd:32-38`
- `reference/wff-schema/v4/group/part/draw/shape/arcElement.xsd:36-37`

`<Arc>` accepts either `<Stroke>` (single color) OR `<WeightedStroke>` (multi-color segmented). `<Stroke>` and `<Fill>` can themselves contain `<LinearGradient>`, `<RadialGradient>`, or `<SweepGradient>`.

### 2.1 WeightedStroke for ranged-value gradient bars

`<WeightedStroke>` supports:
- `colors` — list of hex colors **OR** a `colorListSourceType` reference like `[COMPLICATION.WEIGHTED_ELEMENTS_COLORS]`
- `weights` — float list **OR** a source reference like `[COMPLICATION.RANGED_VALUE_COLOR_INTERPOLATE]`
- `interpolate="true"` — draws gradient between consecutive colors (segments need N+1 colors for N weights)
- `discreteGap` — visible gap between segments (useful with `cap="ROUND"`)
- `thickness`, `cap`

### 2.2 Provider data exposure (the missing piece in earlier analysis)

The XSD documents two complication data sources for color/weight arrays:

| Source | Documented at |
|---|---|
| `[COMPLICATION.WEIGHTED_ELEMENTS_COLORS]` | `primitiveListTypes.xsd:150, 169` |
| `[COMPLICATION.RANGED_VALUE_COLOR_INTERPOLATE]` | `primitiveListTypes.xsd:183` |

(WFF source identifiers follow `[A-Z0-9]+([._]\w+)*` — the schema doesn't enumerate every complication field as an enum, but the colorList/colorWeight type docs name these explicitly.)

The original Koru's `gradientArc` segments — driven by provider `segment_colors` — **can** be reproduced for `WEIGHTED_ELEMENTS` complication data.

### 2.3 Concrete example: replace replica's solid `<Stroke>` with `<WeightedStroke>` in the WEIGHTED_ELEMENTS branch

```xml
<Arc centerX="225" centerY="225" endAngle="351" height="350"
     startAngle="288" width="350">
    <WeightedStroke
        colors="[COMPLICATION.WEIGHTED_ELEMENTS_COLORS]"
        weights="1 1 1 1 1 1 1"
        thickness="4"
        discreteGap="4"
        cap="ROUND" />
</Arc>
```

(For weighted-elements with explicit per-segment weights, replace the static `"1 1 1..."` with a `[COMPLICATION.WEIGHTED_ELEMENTS_WEIGHTS]` source — confirm the exact identifier in the live WFF docs at https://developer.android.com/reference/wear-os/wff/watch-face?version=4 before committing.)

### 2.4 What this changes in the delta doc

- **Remove** "color-ramp / weighted-elements gradient arcs cannot be reproduced" from §10.
- **Add** a new complication-type branch (`<Complication type="WEIGHTED_ELEMENTS">`) to slots 1/2/3/0 using `<WeightedStroke>`. The replica currently lists `WEIGHTED_ELEMENTS` neither in `supportedTypes` nor in the `<Complication type=...>` branches for the corner slots — so providers offering segmented data will fall through to EMPTY. Adding it gives the replica feature parity with the original on this front.

---

## 3. Sweep / linear / radial gradients on `<Fill>` and `<Stroke>`

For elements that don't need provider data (decorative arcs, the pill background), use `<SweepGradient>`:

```xml
<Arc centerX="225" centerY="225" endAngle="351" height="350"
     startAngle="288" width="350">
    <Stroke color="#ffffff" thickness="4" cap="ROUND">
        <SweepGradient
            centerX="225" centerY="225"
            startAngle="288" endAngle="351"
            colors="[CONFIGURATION.a2CompBaseColor] [CONFIGURATION.a3CompFgColor]"
            positions="0.0 1.0" />
    </Stroke>
</Arc>
```

(`positions` is a normalized list per-color along the sweep.)

Use cases:
- A subtle gradient on the corner arc track that ramps from `a2CompBaseColor` to `a3CompFgColor`.
- Linear gradient on the minute pill background.
- Radial gradient inside a bulb complication.

---

## 4. Font weight/width axes (variable fonts)

**Schema:** `reference/wff-schema/v4/group/part/text/fontElement.xsd:55-88`

`<Font>` supports:
- `weight` — `THIN | ULTRA_LIGHT | EXTRA_LIGHT | LIGHT | NORMAL | MEDIUM | SEMI_BOLD | BOLD | ULTRA_BOLD | EXTRA_BOLD | BLACK | EXTRA_BLACK`
- `width` — `ULTRA_CONDENSED | EXTRA_CONDENSED | CONDENSED | SEMI_CONDENSED | NORMAL | SEMI_EXPANDED | EXPANDED | EXTRA_EXPANDED | ULTRA_EXPANDED`
- `slant` — `NORMAL | ITALIC`
- `letterSpacing` — float in EM units

This means you don't need separate TTF files per weight if you ship a variable font. A single `Inter-Variable.ttf` (or `RobotoFlex-Variable.ttf`) named `inter_variable` lets you write:

```xml
<Font family="inter_variable" size="108" weight="MEDIUM" width="NORMAL"
      letterSpacing="-0.02">
```

…and pull a heavier hour digit without bundling `inter_black.ttf` separately. This addresses §1 of the delta doc (hour reads thin) without growing the APK.

Note that the replica currently uses `family="inter_medium"` / `inter_regular` — single-weight files. Either bundle Inter Variable (one file ~330 KB covers the full weight axis) or keep static fonts and just pick a heavier `_bold.ttf` / `_semibold.ttf`.

---

## 5. `extractColorFromColors()` and `extractColorFromWeightedColors()`

**Schema:** `reference/wff-schema/v4/common/simpleTypes/arithmeticExpressionType.xsd:62-63`

Two helper functions exposed in expressions:
- `extractColorFromColors(colorList, count, index)` — pull color at a position.
- `extractColorFromWeightedColors(colorList, weightList, count, normalizedValue)` — pull the color that the value falls into.

Use case: color the value-indicator dot/arc to match the segment the current ranged value falls within for a `WEIGHTED_ELEMENTS` complication:

```xml
<Stroke
    color="extractColorFromWeightedColors([COMPLICATION.WEIGHTED_ELEMENTS_COLORS], [COMPLICATION.WEIGHTED_ELEMENTS_WEIGHTS], 7, ([COMPLICATION.RANGED_VALUE_VALUE] - [COMPLICATION.RANGED_VALUE_MIN]) / ([COMPLICATION.RANGED_VALUE_MAX] - [COMPLICATION.RANGED_VALUE_MIN]))"
    thickness="12" />
```

(Confirm exact parameter order in WFF v4 docs — the schema only declares the function names.)

---

## 6. Image filters (HsbFilter)

**Schema:** `reference/wff-schema/v4/group/part/image/imageFilter/hsbFilterElement.xsd`

`<HsbFilter hueRotate="0" saturate="1" brightness="1">`. The replica already uses `saturate="0"` on the slot 4 icon to desaturate before tinting (correct per `antipatterns.md` §4). No new affordance here, but worth noting `hueRotate` and `brightness` are available and can be expression-driven for things like ambient dimming or theme accents.

---

## 7. Other useful features the schema reveals

| Feature | Schema location | Possible use |
|---|---|---|
| `<Variant>` `startOffset` (normalized) | `variantElements.xsd:47-60` | Stagger the second-ring fade after the minute-ring fade in AOD |
| `<Animation>` `repeat="-1"` infinite | `animationElement.xsd:59-73` | Looping decorative animations (subtle pulse on the indicator) |
| `<Animation>` `fps` configurable | `animationElement.xsd:75` | Lower fps on AOD-adjacent animations |
| `BoundingArc` `outlinePadding` | `boundingElement.xsd:54-55` | Tap region padding without changing visible arc |
| `BoundingArc` `isRoundEdge` | already used | Already correctly set on corner slots |
| `<ComplicationSlot>` `tintColor`, `scaleX/Y`, `angle`, `pivot2D` | `complicationSlotElement.xsd:46-50` | Per-slot tinting/rotation without wrapping in a Group |
| `Expression` helpers: `clamp`, `round`, `floor`, `numberFormat`, `subText`, `textLength`, trig, `pow`, `colorArgb`, `colorRgb` | `arithmeticExpressionType.xsd:28-65` | Already partly used; `numberFormat` cleaner than `%s` Template for value rounding |

---

## 8. Revised "WFF ceilings" list (replaces §10 of REPLICA_DELTAS.md)

Genuinely impossible:
- **Auto-class-driven CSS swap based on complication data type.** Replaced verbosely by `<Condition>` + `<Compare>`. (Unchanged.)
- **Image quartering trick.** No shared-resource-rotation mechanic. Ship full PNG. (Unchanged.)
- **Hardcoded Fitbit / Pixel Weather providers.** First-party only. (Unchanged.)
- **ms-precise AOD transitions.** `<Variant>` durations are normalized to the vendor's transition window, not absolute ms. You can re-shape and stagger but can't pin to "exactly 0.4 seconds." (Refinement, not removal.)
- **Bezier easing on the rotating-text counter-rotation.** `<Transform target="angle" value="[MINUTE] * (-6)">` already uses `<Animation duration="1">`, which only smooths the per-tick step.

Available after all:
- **Cubic-bezier eased AOD crossfades.** Use `<Variant interpolation="CUBIC_BEZIER" controls="...">`. §1.
- **Multi-color / weighted-elements gradient arcs.** Use `<WeightedStroke colors="[COMPLICATION.WEIGHTED_ELEMENTS_COLORS]" ...>`. §2.
- **Variable-font weight without extra TTFs.** `<Font weight="BLACK">` on a variable font file. §4.
- **Color picked at runtime from a list/weighted-list.** `extractColorFromColors`, `extractColorFromWeightedColors`. §5.

---

## 9. Revised priority fixes (additions to REPLICA_DELTAS.md §11)

These slot into the existing priority list. Numbering continues from the original list.

8. **Add CUBIC_BEZIER easing to the minute pill morph.** Replace `interpolation="EASE_IN_OUT"` at `watchface.xml:3126` with the original's bezier; consider two stacked `<Variant>` blocks (one for the close, one for the open) if you want different curves per direction.

9. **Add ring scale Variants** to the `minutes` Group (`watchface.xml:1241`) — `scaleX/scaleY` 1 → 0.97 (AOD) with cubic-bezier `0.26 0.54 0.06 1.0`. Same for the `seconds` Group with a `startOffset` of ~0.17.

10. **Add `WEIGHTED_ELEMENTS` to the corner slots' `supportedTypes`** and add a `<Complication type="WEIGHTED_ELEMENTS">` branch using `<WeightedStroke colors="[COMPLICATION.WEIGHTED_ELEMENTS_COLORS]" weights="[COMPLICATION.WEIGHTED_ELEMENTS_WEIGHTS]" thickness="4" cap="ROUND" discreteGap="2">`. This gives parity with the original's segment-rendering for things like heart-rate zones, AQI bands, etc.

11. **(Optional) Add a `RANGED_VALUE` gradient track** using `<WeightedStroke interpolate="true">` so the value bar fades through colors (e.g. green → orange → red for battery).

12. **(Optional) Switch to Inter Variable** and remove `inter_medium.ttf` / `inter_regular.ttf`. Set hour weight via `weight="MEDIUM"` (or heavier) instead of relying on font filename. Smaller APK, more flexibility.

---

## 10. Grep recipes for future investigation

When checking whether a feature exists in v4, grep the schema first:

```
Grep -path reference/wff-schema/v4 -pattern 'name="<attribute>"'
Grep -path reference/wff-schema/v4 -pattern 'element name="<Element>"'
Grep -path reference/wff-schema/v4 -pattern '<xs:enumeration value="<VALUE>"'
```

Common entry points to bookmark:
- `common/simpleTypes/arithmeticExpressionType.xsd` — all expression helpers and operators.
- `common/simpleTypes/sourceType.xsd` — enumerated data sources (time, battery, weather, sensor, health).
- `common/simpleTypes/primitiveListTypes.xsd` — colorListType, colorWeightListType (where complication data sources are documented).
- `common/variant/variantElements.xsd` — AOD transition timing and easing.
- `common/animationElement.xsd` — continuous animation timing and easing.
- `complication/complicationElement.xsd` — supported complication types enum.
- `group/part/draw/` — shapes, strokes, fills, gradients.
