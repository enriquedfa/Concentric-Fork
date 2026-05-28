# WEIGHTED_ELEMENTS + Gradient ranged-value — implementation plan

Plan for adding two new visual treatments to the corner arc complications:

- **WEIGHTED_ELEMENTS support** — render provider-supplied colored segments (heart-rate zones, AQI bands, weather bars, etc.) using `<WeightedStroke>`.
- **(Optional) Gradient ranged-value track** — fade the value bar through 2-3 colors using `<WeightedStroke interpolate="true">` for things like battery (green → orange → red).

Both leverage the same WFF v4 primitive: `<WeightedStroke>` inside `<Arc>`. Schema reference: `reference/wff-schema/v4/group/part/draw/style/weightedStrokeElement.xsd`, `reference/wff-schema/v4/group/part/draw/shape/arcElement.xsd:36-37`.

---

## 1. Background — what's possible

### `<WeightedStroke>` accepts

| Attribute | Type | Notes |
|---|---|---|
| `colors` | `colorListType` (hex list) **or** source ref like `[COMPLICATION.WEIGHTED_ELEMENTS_COLORS]` | provider colors come straight in |
| `weights` | `colorWeightListType` (float list) **or** source ref like `[COMPLICATION.RANGED_VALUE_COLOR_INTERPOLATE]` | one weight per segment |
| `interpolate` | boolean | `true` = blend between consecutive colors; `false` = discrete bands |
| `discreteGap` | float | gap between discrete segments (useful with `cap="ROUND"`) |
| `thickness` | float | stroke width |
| `cap` | `BUTT | ROUND | SQUARE` |

### Provider data exposed (per schema docs at `primitiveListTypes.xsd:150, 169, 183`)

| Source | Confirmed in schema? | Notes |
|---|---|---|
| `[COMPLICATION.WEIGHTED_ELEMENTS_COLORS]` | ✓ explicitly documented | the array of segment hex colors |
| `[COMPLICATION.RANGED_VALUE_COLOR_INTERPOLATE]` | ✓ explicitly documented | weight values for ramped ranged value |
| `[COMPLICATION.WEIGHTED_ELEMENTS_WEIGHTS]` | ⚠ implied by analogy with COLORS, **not explicitly named in the schema** | likely valid — confirm against live WFF docs https://developer.android.com/reference/wear-os/wff/watch-face?version=4 before relying on it |
| `[COMPLICATION.WEIGHTED_ELEMENTS_ELEMENT_COUNT]` | ⚠ unverified | may be needed for fixed-array indexing |

> **Verification step before coding:** open the WFF v4 docs URL above, search for "WEIGHTED_ELEMENTS" and "RANGED_VALUE_COLOR_INTERPOLATE", and write down the exact source identifier names. The schema gives examples but isn't exhaustive about source-name patterns.

---

## 2. Feature A — WEIGHTED_ELEMENTS support (segmented bands)

### 2.1 Provider use cases

Real providers that send `WEIGHTED_ELEMENTS`:
- **Air Quality Index** (Google Weather): segments for PM2.5 / PM10 / O3 / NO2.
- **Heart Rate Zones** (Fitbit/Health Connect): segments for resting / light / cardio / peak zones.
- **UV Index** (some weather providers): segments for low / moderate / high / extreme.
- **Sleep Stages**: light / deep / REM segments.

Without this branch, all of those fall through to the EMPTY branch and just don't render in the slot.

### 2.2 XML changes per slot

**Step 1 — extend `supportedTypes` on all 4 corner slots.**

| Slot | File line | Change |
|---|---|---|
| 0 (top_left) | `watchface.xml:3191` | `RANGED_VALUE SHORT_TEXT MONOCHROMATIC_IMAGE EMPTY` → `RANGED_VALUE WEIGHTED_ELEMENTS SHORT_TEXT MONOCHROMATIC_IMAGE EMPTY` |
| 1 (top_right) | `watchface.xml:4342` | same |
| 2 (bottom_left) | `watchface.xml:5497` | same |
| 3 (bottom_right) | `watchface.xml:6652` | same |

**Step 2 — add a `<Complication type="WEIGHTED_ELEMENTS">` branch to each slot.** Insert after the existing `<Complication type="RANGED_VALUE">` block.

Skeleton for slot 0 (top_left). The angles match the slot's existing BoundingArc (start 288°, end 351°). Adapt the angles for the other slots.

```xml
<Complication type="WEIGHTED_ELEMENTS">
    <Condition>
        <Expressions>
            <Expression name="modeOn">
                [CONFIGURATION.z1_mode] != 0
            </Expression>
        </Expressions>
        <Compare expression="modeOn">
            <Group
                name="z1_gate"
                alpha="255"
                height="225"
                width="225"
                x="0"
                y="0">
                <!-- Faint placeholder track (matches the RANGED_VALUE branch behavior) -->
                <PartDraw
                    name="placeholder"
                    alpha="255"
                    height="225"
                    tintColor="[CONFIGURATION.a2CompBaseColor]"
                    width="225"
                    x="0"
                    y="0">
                    <Transform
                        target="alpha"
                        value="[CONFIGURATION.z4_placeholder_bar] ? 255 : 0" />
                    <Arc
                        centerX="225"
                        centerY="225"
                        endAngle="351"
                        height="350"
                        startAngle="288"
                        width="350">
                        <Stroke
                            cap="ROUND"
                            color="#33ffffff"
                            thickness="4" />
                    </Arc>
                </PartDraw>
                <!-- Weighted segments fed by provider colors -->
                <PartDraw
                    name="segments"
                    alpha="255"
                    height="225"
                    width="225"
                    x="0"
                    y="0">
                    <Arc
                        centerX="225"
                        centerY="225"
                        endAngle="351"
                        height="350"
                        startAngle="288"
                        width="350">
                        <WeightedStroke
                            cap="ROUND"
                            colors="[COMPLICATION.WEIGHTED_ELEMENTS_COLORS]"
                            weights="[COMPLICATION.WEIGHTED_ELEMENTS_WEIGHTS]"
                            thickness="4"
                            discreteGap="2" />
                    </Arc>
                </PartDraw>
                <!-- Optional: complication TEXT/TITLE rendered as masked TextCircular,
                     same approach as RANGED_VALUE branch (copy lines 3375-3419 with same angles) -->
            </Group>
        </Compare>
        <Default>
            <!-- AOD branch — single Arc with weighted segments, no text -->
            <PartDraw
                name="segments_aod"
                alpha="255"
                height="225"
                width="225"
                x="0"
                y="0">
                <Arc
                    centerX="225"
                    centerY="225"
                    endAngle="351"
                    height="350"
                    startAngle="288"
                    width="350">
                    <WeightedStroke
                        cap="ROUND"
                        colors="[COMPLICATION.WEIGHTED_ELEMENTS_COLORS]"
                        weights="[COMPLICATION.WEIGHTED_ELEMENTS_WEIGHTS]"
                        thickness="4"
                        discreteGap="2" />
                </Arc>
            </PartDraw>
        </Default>
    </Condition>
</Complication>
```

### 2.3 Per-slot angle overrides

The angles in the snippet above are for slot 0 (NW). For the others, copy and replace these two values everywhere they appear in the WEIGHTED_ELEMENTS branch:

| Slot | startAngle | endAngle | Notes |
|---|---|---|---|
| 0 NW | 288 | 351 | already in snippet |
| 1 NE | 9 | 72 | rightmost arc — replace 288→9, 351→72 |
| 2 SW | 189 | 252 | replace 288→189, 351→252 |
| 3 SE | 108 | 171 | replace 288→108, 351→171 |

(Pull `centerX`/`centerY` from each slot's existing BoundingArc — they differ per slot too.)

### 2.4 Caveats / risks

- **`WEIGHTED_ELEMENTS_WEIGHTS` is unverified.** If WFF rejects the source name, fall back to a static `weights="1 1 1 1 1"` (equal segments). Visually plausible for unknown segment counts.
- **Segment count is provider-defined.** Most providers send 3-7 segments. The original Concentric symbol has 7 `<gradientArc>` slots → 7 max. WFF's `WeightedStroke` doesn't cap explicitly; the renderer should handle any length.
- **No fallback color logic.** If the provider sends colors that clash with the user's `a0PrimaryColor` theme, there's no way to remap. Accept that WEIGHTED_ELEMENTS bypasses the theme intentionally.
- **`discreteGap="2"` matters with `cap="ROUND"`.** Without a gap, round caps from adjacent segments overlap and the colors smear at boundaries. 2px gap with 4px stroke = visible separation.
- **Tap region.** The existing `<BoundingArc>` already defines tap area; no change needed.
- **Text/title rendering.** Most WEIGHTED_ELEMENTS providers also send a `TEXT` field summarizing the data (e.g., "AQI 42 / Good"). Add the same MASK + TextCircular pattern from the RANGED_VALUE branch if you want labels. Or skip if the segmented bar alone is enough.

### 2.5 Verification on device

1. Install a system provider that emits WEIGHTED_ELEMENTS. Wear OS's built-in providers don't (as of writing). You'll need a third-party app — Health Connect heart-rate-zones or Google's AQI complication.
2. Long-press the watch face → assign that provider to one corner slot.
3. Verify segments render in the expected colors and proportions.
4. Switch to AOD; verify segments remain visible if `z3_aod_compl` is on.

---

## 3. Feature B — Gradient ranged-value track (optional)

### 3.1 Why

Battery-level complication looks more meaningful with a green-yellow-red gradient than with a single accent color. The provider doesn't drive the colors (RANGED_VALUE just supplies a numeric value); you pick the gradient stops.

### 3.2 Implementation

Replace the value-fill Arc inside the RANGED_VALUE branch (currently a plain `<Stroke>` filled from start to value angle) with a `<WeightedStroke interpolate="true">` whose colors fade through your preset stops.

**Current value-fill Arc** in slot 0, line ~3340-3366 (the `<Default>` branch of the customRange Condition):

```xml
<PartDraw name="border" height="225" tintColor="[CONFIGURATION.a2CompBaseColor]" width="225" x="0" y="0">
    <Arc centerX="225" centerY="225" endAngle="351" height="350" startAngle="298" width="350">
        <Transform target="startAngle" value="304 + textLength([COMPLICATION.TEXT]) * 4.5" />
        <Transform target="endAngle" value="304 + textLength([COMPLICATION.TEXT]) * 4.5 + ((([COMPLICATION.RANGED_VALUE_VALUE] - [COMPLICATION.RANGED_VALUE_MIN]) / ([COMPLICATION.RANGED_VALUE_MAX] - [COMPLICATION.RANGED_VALUE_MIN])) * (47 - textLength([COMPLICATION.TEXT]) * 4.5))" />
        <Stroke cap="ROUND" color="#ffffff" thickness="5" />
    </Arc>
</PartDraw>
```

Replaces with (drops `tintColor`, swaps `<Stroke>` for `<WeightedStroke>`):

```xml
<PartDraw name="border" height="225" width="225" x="0" y="0">
    <Arc centerX="225" centerY="225" endAngle="351" height="350" startAngle="298" width="350">
        <Transform target="startAngle" value="304 + textLength([COMPLICATION.TEXT]) * 4.5" />
        <Transform target="endAngle" value="304 + textLength([COMPLICATION.TEXT]) * 4.5 + ((([COMPLICATION.RANGED_VALUE_VALUE] - [COMPLICATION.RANGED_VALUE_MIN]) / ([COMPLICATION.RANGED_VALUE_MAX] - [COMPLICATION.RANGED_VALUE_MIN])) * (47 - textLength([COMPLICATION.TEXT]) * 4.5))" />
        <WeightedStroke
            cap="ROUND"
            colors="#ff4444 #ffaa00 #44ff44"
            weights="1 1"
            interpolate="true"
            thickness="5" />
    </Arc>
</PartDraw>
```

**Color stops to consider:**
- Battery: `"#ff4444 #ffaa00 #44ff44"` (red → amber → green) with `weights="1 1"`.
- Goal progress: `"[CONFIGURATION.a2CompBaseColor] [CONFIGURATION.a3CompFgColor]"` (theme-aware fade) with `weights="1"`.

**Note** `interpolate="true"` requires **N+1 colors for N weights** per the schema doc (`weightedStrokeElement.xsd:61-69`). 2 weights → 3 colors. 1 weight → 2 colors.

### 3.3 Per-slot considerations

You may not want the gradient on *every* slot — only on slots where the gradient is semantically meaningful. Options:

- **Apply globally**: changes all 4 corner slots. Simplest.
- **Apply per-slot**: edit only the slots where the user's default is battery or heart-rate (slot 1 has WATCH_BATTERY default). Keep others on solid color.
- **Make it a user setting**: add a `BooleanConfiguration z6_gradient_value` toggle. Then gate the gradient/solid with a Condition. More work but gives users control.

### 3.4 Interaction with the existing two-color logic

Currently the value-fill Arc shares `a2CompBaseColor` with the track ("two-tone bug" from `REPLICA_DELTAS_SIZES.md` §9 — which you confirmed is intentional for custom-range slots with the dot marker). Switching that value-fill to a WeightedStroke gradient sidesteps the issue entirely — the fill is now visually distinct by construction.

For the **non-custom-range branch** (default Compare in the customRange Condition), the value fill is the same `tintColor=a2CompBaseColor color=#ffffff` as the track. Applying the gradient there has the biggest visual win.

For the **custom-range branch**, the user said the dot marker handles value indication. So you might leave that branch alone and only gradient the default branch. Up to you.

---

## 4. Implementation order

1. **Verify source identifier names** against live WFF v4 docs. Critical first step.
2. **Add `WEIGHTED_ELEMENTS` to one slot's `supportedTypes`** (slot 1 is a good candidate — it has WATCH_BATTERY default, and WEIGHTED_ELEMENTS providers often include battery-like ranged data alongside zones).
3. **Add the WEIGHTED_ELEMENTS Complication branch** to that one slot using static `weights="1 1 1 1 1"` initially. Build + install. Assign a real WEIGHTED_ELEMENTS provider and verify segments render.
4. **Switch weights to the data-source ref** (`[COMPLICATION.WEIGHTED_ELEMENTS_WEIGHTS]` or whatever the verified name is). Re-test.
5. **Propagate to slots 0, 2, 3** with their per-slot angle adjustments.
6. **(Optional) Add gradient to RANGED_VALUE default branch** on slot 1 first (battery). Pick gradient stops, build, verify.
7. **(Optional) Decide on gradient scope** — global, per-slot, or user-toggleable.

---

## 5. Files touched

- `app/src/main/res/raw/watchface.xml` — `supportedTypes` on 4 slots, new Complication branches in 4 slots (~400 lines added if you include AOD branches and text labels for each), value-fill Arc replacement for ranged-value gradient.

No string, drawable, or font asset changes needed. No new ColorConfiguration entries needed unless you want user-pickable gradient stops.

---

## 6. Estimated effort

- Feature A (WEIGHTED_ELEMENTS support, all 4 slots, no text labels): **1-1.5 hours** once source identifiers are verified.
- Feature A with text labels matching the RANGED_VALUE branch's MASK pattern: **2-3 hours**.
- Feature B (gradient ranged-value, slot 1 only, default branch): **30 minutes** after Feature A is working.
- Feature B globally with user toggle: **+1 hour** for the BooleanConfiguration and Condition gates.

---

## 7. Future possibilities (out of scope here, just noting)

- `extractColorFromWeightedColors([COMPLICATION.WEIGHTED_ELEMENTS_COLORS], [COMPLICATION.WEIGHTED_ELEMENTS_WEIGHTS], 7, [COMPLICATION.RANGED_VALUE_VALUE] / [COMPLICATION.RANGED_VALUE_MAX])` — could tint the value indicator dot with the color of the segment the current value falls into. Slick effect, expression-only, no new elements.
- `<SweepGradient>` for the placeholder track on RANGED_VALUE slots — fades the empty track itself through theme colors. Decorative.
- `<RadialGradient>` inside a future bulb complication — covered in `HALF_DIAL_REPLICATION.md` if/when you add Half Dial.
