# Goal-progress >100% halo — implementation plan

Add visual feedback when a goal-progress complication exceeds its target (e.g., step count 12,500 / 10,000 goal). The original Concentric brightens the overlap segment by 1.5× and outlines it in black; the replica currently doesn't render goal-progress at all on the corner arcs.

Sources:
- Original behavior: `Pixel Watch Face/app/src/main/assets/widgets/complication.css:37, 206-207` (`#progress-arc-overlap { brightness: 1.5; stroke-width: 4; stroke: #000000; paint-order: stroke; }`)
- Original symbol: `widgets/complication.defs:497-502, 660-663` (the `<arc id='progress-arc-overlap'>` element)
- WFF v4 complication type enum: `reference/wff-schema/v4/complication/complicationElement.xsd:30` (`GOAL_PROGRESS`)

---

## 1. Verification needed before coding

WFF v4 lists `GOAL_PROGRESS` as a complication type, but the schema doesn't enumerate which fields the runtime exposes for it. The likely candidates (by analogy with `RANGED_VALUE`):

| Source | Likely meaning | Status |
|---|---|---|
| `[COMPLICATION.GOAL_PROGRESS_VALUE]` | current progress (e.g., 12500 steps) | ⚠ unverified — may instead be `[COMPLICATION.RANGED_VALUE_VALUE]` reused |
| `[COMPLICATION.GOAL_PROGRESS_TARGET_VALUE]` | goal target (e.g., 10000) | ⚠ unverified |
| `[COMPLICATION.GOAL_PROGRESS_MIN_VALUE]` | typically 0 | ⚠ unverified |

The complication-data class in Wear OS Watchface library uses field names like `value`, `targetValue`, `min`. WFF often mirrors these as `[COMPLICATION.<TYPE>_<FIELD>]`. **First step: confirm exact names against live docs at https://developer.android.com/reference/wear-os/wff/watch-face?version=4 (search for "GOAL_PROGRESS").**

If GOAL_PROGRESS reuses the RANGED_VALUE fields (it might — many WFF v3+ implementations collapse them), then RANGED_VALUE_VALUE > RANGED_VALUE_MAX is your over-100% signal and no new sources are needed.

---

## 2. WFF doesn't have `brightness: 1.5` on strokes

Schema check: `<Stroke>` only takes `color`, `thickness`, `cap`, `dashIntervals`, `dashPhase` (`reference/wff-schema/v4/group/part/draw/style/strokeElement.xsd:40-52`). No `brightness`, no `blendMode`.

`<HsbFilter>` (which has `brightness`) is image-only — it nests inside `<PartImage>` not `<PartDraw>`.

**Options to fake brightness 1.5:**

| Option | How | Trade-off |
|---|---|---|
| **A — Pre-brightened color literal** | Compute `color * 1.5` per stop manually (e.g., if `a3CompFgColor = #2295ff` then brightened = `#80c2ff`) and hardcode | Loses theme reactivity entirely |
| **B — Lighter theme color reference** | Use `a0PrimaryColor` (which is the lighter "primary" role) instead of `a3CompFgColor` for the overlap segment | Reuses existing theme, but coupling is semantic guesswork |
| **C — White overlay** | Stack a second arc with `color="#80ffffff"` (50% white) over the normal-color overlap arc | Cheap brightness boost; reads as "highlighted"; works on any base color |
| **D — Approximate via `colorArgb()` expression** | `colorArgb(255, clamp(red([CONFIGURATION.a3CompFgColor]) * 1.5, 0, 255), ...)` — requires `red()`, `green()`, `blue()` helpers | Schema doesn't list those helpers in `arithmeticExpressionType.xsd` — unlikely to work |

**Recommendation: Option C.** Stack a 50% white arc on top of a normal-color overlap arc. Equivalent to a 1.5× brightness bump and survives theme changes. Two cheap arcs vs the alternatives' compromises.

---

## 3. XML changes per slot

### 3.1 Extend `supportedTypes` on all 4 corner slots

| Slot | File line | Change |
|---|---|---|
| 0 (top_left)     | `watchface.xml:3191` | `RANGED_VALUE SHORT_TEXT MONOCHROMATIC_IMAGE EMPTY` → `RANGED_VALUE GOAL_PROGRESS SHORT_TEXT MONOCHROMATIC_IMAGE EMPTY` |
| 1 (top_right)    | `watchface.xml:4342` | same |
| 2 (bottom_left)  | `watchface.xml:5497` | same |
| 3 (bottom_right) | `watchface.xml:6652` | same |

If you're also adding `WEIGHTED_ELEMENTS` from the other plan, the supportedTypes string becomes `RANGED_VALUE GOAL_PROGRESS WEIGHTED_ELEMENTS SHORT_TEXT MONOCHROMATIC_IMAGE EMPTY`.

### 3.2 Add a `<Complication type="GOAL_PROGRESS">` branch per slot

Pattern: render the value arc from start to value-angle. If value > target, render an additional "overlap" arc and a white-overlay arc on top for the over-100% portion.

Math: `valueAngle = startAngle + sweep * (value / target)` capped at `endAngle` when value ≤ target. If value > target, the **base arc fills 100%** (start → endAngle), and the **overlap arc** renders from start → `(value - target) / target * sweep` representing the second-lap excess.

(Visually: a full-strength loop plus a partial second loop heaped on top. This matches the original's behavior — `#progress-arc` fills 100%, `#progress-arc-overlap` adds the excess.)

Skeleton for slot 0 (NW, startAngle=288, endAngle=351, sweep=63°). Insert after the existing `<Complication type="RANGED_VALUE">` block.

```xml
<Complication type="GOAL_PROGRESS">
    <Condition>
        <Expressions>
            <Expression name="modeOn">
                [CONFIGURATION.z1_mode] != 0
            </Expression>
        </Expressions>
        <Compare expression="modeOn">
            <Group name="z1_gate" alpha="255" height="225" width="225" x="0" y="0">
                <!-- Placeholder track -->
                <PartDraw name="placeholder" alpha="255" height="225"
                          tintColor="[CONFIGURATION.a2CompBaseColor]" width="225" x="0" y="0">
                    <Transform target="alpha" value="[CONFIGURATION.z4_placeholder_bar] ? 255 : 0" />
                    <Arc centerX="225" centerY="225" endAngle="351" height="350"
                         startAngle="288" width="350">
                        <Stroke cap="ROUND" color="#33ffffff" thickness="4" />
                    </Arc>
                </PartDraw>

                <!-- Base progress arc: clamped at endAngle (100% cap)
                     Replace GOAL_PROGRESS_VALUE/TARGET_VALUE with whatever the verified WFF
                     names turn out to be — likely RANGED_VALUE_VALUE / RANGED_VALUE_MAX -->
                <PartDraw name="progress" height="225"
                          tintColor="[CONFIGURATION.a3CompFgColor]" width="225" x="0" y="0">
                    <Arc centerX="225" centerY="225" endAngle="351" height="350"
                         startAngle="288" width="350">
                        <Transform
                            target="endAngle"
                            value="288 + clamp([COMPLICATION.GOAL_PROGRESS_VALUE] / [COMPLICATION.GOAL_PROGRESS_TARGET_VALUE], 0, 1) * 63" />
                        <Stroke cap="ROUND" color="#ffffff" thickness="5" />
                    </Arc>
                </PartDraw>

                <!-- Overlap arc: only visible when value > target. Renders the excess starting from
                     the arc's start, capped at one more full lap (200% goal). -->
                <PartDraw name="overlap" alpha="255" height="225"
                          tintColor="[CONFIGURATION.a3CompFgColor]" width="225" x="0" y="0">
                    <Transform
                        target="alpha"
                        value="[COMPLICATION.GOAL_PROGRESS_VALUE] > [COMPLICATION.GOAL_PROGRESS_TARGET_VALUE] ? 255 : 0" />
                    <Arc centerX="225" centerY="225" endAngle="351" height="350"
                         startAngle="288" width="350">
                        <Transform
                            target="endAngle"
                            value="288 + clamp(([COMPLICATION.GOAL_PROGRESS_VALUE] - [COMPLICATION.GOAL_PROGRESS_TARGET_VALUE]) / [COMPLICATION.GOAL_PROGRESS_TARGET_VALUE], 0, 1) * 63" />
                        <Stroke cap="ROUND" color="#ffffff" thickness="5" />
                    </Arc>
                </PartDraw>

                <!-- White-overlay arc on top of overlap for the 1.5x brightness fake. -->
                <PartDraw name="overlap_highlight" alpha="255" height="225" width="225" x="0" y="0">
                    <Transform
                        target="alpha"
                        value="[COMPLICATION.GOAL_PROGRESS_VALUE] > [COMPLICATION.GOAL_PROGRESS_TARGET_VALUE] ? 128 : 0" />
                    <Arc centerX="225" centerY="225" endAngle="351" height="350"
                         startAngle="288" width="350">
                        <Transform
                            target="endAngle"
                            value="288 + clamp(([COMPLICATION.GOAL_PROGRESS_VALUE] - [COMPLICATION.GOAL_PROGRESS_TARGET_VALUE]) / [COMPLICATION.GOAL_PROGRESS_TARGET_VALUE], 0, 1) * 63" />
                        <Stroke cap="ROUND" color="#ffffff" thickness="5" />
                    </Arc>
                </PartDraw>

                <!-- Optional: TEXT label, copy the MASK + TextCircular pattern from
                     RANGED_VALUE branch (lines 3375-3419 in the existing slot 0). -->
            </Group>
        </Compare>
        <Default>
            <!-- AOD branch — single base arc, no overlap visualization, no text -->
            <PartDraw name="progress_aod" alpha="255" height="225"
                      tintColor="[CONFIGURATION.a3CompFgColor]" width="225" x="0" y="0">
                <Arc centerX="225" centerY="225" endAngle="351" height="350"
                     startAngle="288" width="350">
                    <Transform
                        target="endAngle"
                        value="288 + clamp([COMPLICATION.GOAL_PROGRESS_VALUE] / [COMPLICATION.GOAL_PROGRESS_TARGET_VALUE], 0, 1) * 63" />
                    <Stroke cap="ROUND" color="#ffffff" thickness="5" />
                </Arc>
            </PartDraw>
        </Default>
    </Condition>
</Complication>
```

### 3.3 Per-slot angle table

Same as the WEIGHTED_ELEMENTS plan. For each slot, replace the four `startAngle=288, endAngle=351, sweep=63` values:

| Slot | startAngle | endAngle | sweep | centerX | centerY |
|---|---|---|---|---|---|
| 0 NW | 288 | 351 | 63 | 225 | 225 |
| 1 NE | 9   | 72  | 63 | 0   | 225 |
| 2 SW | 189 | 252 | 63 | 225 | 0   |
| 3 SE | 108 | 171 | 63 | 0   | 0   |

The `Transform target="endAngle"` expressions use `startAngle + sweep * progress` — so for slot 1, change `288 + ... * 63` to `9 + ... * 63`, etc.

---

## 4. Black-stroke outline (original's `stroke: #000000` around the overlap)

The original draws a 4px black stroke around the overlap arc so it contrasts with the base arc underneath. Functionally similar to the `indicator_background` halo on the RANGED_VALUE branch.

To replicate: insert one more PartDraw between the `progress` arc and the `overlap` arc, drawn at slightly thicker stroke in black, same endAngle as the overlap:

```xml
<PartDraw name="overlap_outline" alpha="255" height="225" width="225" x="0" y="0">
    <Transform
        target="alpha"
        value="[COMPLICATION.GOAL_PROGRESS_VALUE] > [COMPLICATION.GOAL_PROGRESS_TARGET_VALUE] ? 255 : 0" />
    <Arc centerX="225" centerY="225" endAngle="351" height="350"
         startAngle="288" width="350">
        <Transform
            target="endAngle"
            value="288 + clamp(([COMPLICATION.GOAL_PROGRESS_VALUE] - [COMPLICATION.GOAL_PROGRESS_TARGET_VALUE]) / [COMPLICATION.GOAL_PROGRESS_TARGET_VALUE], 0, 1) * 63" />
        <Stroke cap="ROUND" color="#000000" thickness="9" />
    </Arc>
</PartDraw>
```

(Thickness 9 = 5 colored + 2 each side = a 2px black outline around the colored overlap.)

Optional — purely decorative. The white-overlay highlight alone reads well enough on most theme colors.

---

## 5. Caveats

- **Source identifier names unverified.** If WFF rejects `GOAL_PROGRESS_VALUE`, fall back to using the slot in RANGED_VALUE mode and treat MAX as the target. Many providers send goal-progress data as both types anyway.
- **Math assumes target > 0.** Division by zero if target is 0 — providers shouldn't send goals of 0 but defensive `[COMPLICATION.GOAL_PROGRESS_TARGET_VALUE] == 0 ? 0 : ...` may be worth adding.
- **Cap at 200%.** The `clamp(..., 0, 1)` on the overlap means values above 200% of goal show as a full second lap, not a third+ lap. Reasonable — visualizing 5× goal as five laps would be unreadable.
- **No theming for the white overlay.** It's pure white at 50% alpha. On very-light theme palettes (porcelain, snow) the overlap will look washed out. Acceptable for most palettes; if it bothers you on light themes, gate the overlay opacity by a darkness-of-theme expression (complex — punt).
- **Text label.** The original shows the value text on the arc whether or not the goal is exceeded. Copy the existing `MASK` + `TextCircular` pattern from the RANGED_VALUE branch if you want labels. Probably worth doing.
- **AOD treatment.** The skeleton above renders only the base progress in AOD (no overlap visualization). Add the overlap arc to the Default branch too if you want the 1.5x effect to persist in AOD — adds ~30 lines per slot.

---

## 6. Implementation order

1. **Verify source names** against live WFF v4 docs.
2. **Pick one slot for a smoke test** — slot 2 (bottom_left) currently defaults to `STEP_COUNT` (replica) which is a natural goal-progress candidate. Add the branch there first.
3. **Build + install. Assign a step provider to slot 2.** Walk to exceed your daily goal (or temporarily lower the goal in the step app for faster testing).
4. **Visually verify:**
   - Below goal: single colored arc growing.
   - At goal: full arc, no overlap visible.
   - Above goal: full arc + overlap arc + white highlight (or black outline if you added that).
5. **Propagate to slots 0, 1, 3** with their per-slot angle adjustments.
6. **(Optional) Add text labels** matching the RANGED_VALUE branch.
7. **(Optional) Add overlap to the AOD branch.**

---

## 7. Files touched

- `app/src/main/res/raw/watchface.xml` — `supportedTypes` on 4 slots, new `Complication type="GOAL_PROGRESS"` branches on 4 slots (~150 lines added per slot if you include text labels and AOD; ~80 lines without).

No string, drawable, or font asset changes.

---

## 8. Estimated effort

- One-slot smoke test (no text, no AOD overlap): **45 minutes** after source identifiers are verified.
- All 4 slots with text labels: **2-3 hours**.
- Add AOD overlap rendering: **+30 minutes**.
- Add black outline option: **+15 minutes per slot** (mostly copy-paste).

---

## 9. Future variations

- **Progress over goal as a different color entirely.** Instead of white-overlay, swap the overlap arc tint to a different config color (e.g., `a0PrimaryColor`). Stronger semantic signal — "you crossed the line."
- **Animated pulse on overlap.** Use `<Transform target="alpha">` with `<Animation duration="2" repeat="-1">` on the overlap to gently pulse. Watch out for AOD power impact.
- **`extractColorFromColors(...)` for tier colors.** Define a list of colors per zone (under-goal, at-goal, double-goal) and pick at runtime based on the ratio. More expressive but more XML.
