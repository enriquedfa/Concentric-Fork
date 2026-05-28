# AOD-only thicker indicator — diagnose + implementation plan

You observed that the corner arc complications "shrink to a fine line" in AOD. Per the analysis in `REPLICA_DELTAS_AOD_AND_TEXT.md` §1, the original Concentric does **not** intentionally shrink arcs in AOD — the shrinkage you see is either Wear OS system overlay dimming or the 12-thick colored indicator + 20-thick black halo visually merging with the dark background, leaving only the thinnest track layer perceptible.

This plan covers (a) how to diagnose which it actually is, and (b) if it's a real geometry shrink, how to override with an AOD-only thicker indicator.

**Don't implement the workaround before doing the diagnosis.** It adds 16+ new elements and may have no visible effect if the cause is Wear OS dimming (which the watch face XML cannot override).

---

## 1. Step 0 — diagnose

### 1.1 Capture interactive vs AOD screenshots

On a real device or emulator:
1. Set `z3_aod_compl` to TRUE so complications stay visible in AOD.
2. Configure a ranged-value complication (battery is easiest) on at least one corner slot.
3. Take a screenshot in interactive mode (`adb shell screencap -p > interactive.png`).
4. Enter AOD (cover the screen sensor or wait for timeout).
5. Take a screenshot in AOD (`adb shell screencap -p > aod.png`).

### 1.2 Measure pixel widths

Open both screenshots side-by-side at 1× zoom. Look at the colored value indicator (the bright sliver at the value angle).

| Observation | Cause | Action |
|---|---|---|
| Same pixel width, lower brightness | Wear OS system dim | **No XML fix possible.** Accept it. Stop here. |
| Same pixel width, gray instead of colored | Wear OS color quantization | **No XML fix possible.** Accept or use brighter base colors. |
| Visibly thinner (e.g., 8px vs 12px) | Element-level shrinkage | Continue to §2 |
| Indicator gone, only thin underlying track visible | Halo blends into bg, exposing 4-thick placeholder | Continue to §2 — increase indicator thickness in AOD |

If you can't tell from screenshots, use an image editor (Pixelmator, GIMP) with the eyedropper / pixel ruler. Count pixel rows of the colored stroke perpendicular to the arc tangent.

### 1.3 Confirm: is the black halo causing the issue?

Quick test before implementing the full workaround: temporarily set the `indicator_background` arc's `color="#000000"` to `color="#ffffff"` (or remove the PartDraw entirely) in slot 0 only. Rebuild, install, enter AOD with `z3_aod_compl=TRUE`. If the indicator now appears thicker/more visible in AOD, the halo was the cause. Revert your test and proceed with §2.

---

## 2. Implementation — AOD-only thicker indicator

### 2.1 Approach

For each corner slot, duplicate the indicator arc (and optionally its background halo) into AOD-specific variants. Use reciprocal `<Variant>` blocks so:

- Interactive PartDraw: visible (alpha=255) interactive, hidden (alpha=0) ambient.
- AOD PartDraw: hidden interactive, visible ambient. Drawn with thicker stroke (e.g., 16 instead of 12) and possibly without the black halo (since the halo is what causes the merge problem).

The pattern mirrors WatchFace Studio's standard "two-state element" idiom — one for each mode, switched via Variants.

### 2.2 XML pattern

The existing indicator structure in slot 0 (from `watchface.xml:3288-3338`, the `customRange` branch — `Default` branch around line 3422-3556 has a similar structure to update):

```xml
<!-- EXISTING: interactive indicator background (black halo, 20-thick) -->
<PartDraw name="indicator_background" height="225" width="225" x="0" y="0">
    <Arc centerX="225" centerY="225" endAngle="305" height="350" startAngle="303" width="350">
        <Transform target="startAngle" value="..." />
        <Transform target="endAngle" value="..." />
        <Stroke cap="ROUND" color="#000000" thickness="20" />
    </Arc>
</PartDraw>

<!-- EXISTING: interactive indicator (colored, 12-thick) -->
<PartDraw name="indicator" height="225" tintColor="[CONFIGURATION.a3CompFgColor]" width="225" x="0" y="0">
    <Arc centerX="225" centerY="225" endAngle="305" height="350" startAngle="303" width="350">
        <Transform target="startAngle" value="..." />
        <Transform target="endAngle" value="..." />
        <Stroke cap="ROUND" color="#ffffff" thickness="12" />
    </Arc>
</PartDraw>
```

Wrap both existing elements with a `<Variant>` that hides them in AMBIENT, then add two new AOD-only siblings (no black halo, thicker stroke):

```xml
<!-- MODIFIED: interactive indicator background, hidden in AOD -->
<PartDraw name="indicator_background" alpha="255" height="225" width="225" x="0" y="0">
    <Variant mode="AMBIENT" target="alpha" value="0" />
    <Arc centerX="225" centerY="225" endAngle="305" height="350" startAngle="303" width="350">
        <Transform target="startAngle" value="..." />
        <Transform target="endAngle" value="..." />
        <Stroke cap="ROUND" color="#000000" thickness="20" />
    </Arc>
</PartDraw>

<!-- MODIFIED: interactive indicator, hidden in AOD -->
<PartDraw name="indicator" alpha="255" height="225" tintColor="[CONFIGURATION.a3CompFgColor]" width="225" x="0" y="0">
    <Variant mode="AMBIENT" target="alpha" value="0" />
    <Arc centerX="225" centerY="225" endAngle="305" height="350" startAngle="303" width="350">
        <Transform target="startAngle" value="..." />
        <Transform target="endAngle" value="..." />
        <Stroke cap="ROUND" color="#ffffff" thickness="12" />
    </Arc>
</PartDraw>

<!-- NEW: AOD-only indicator (thicker, no black halo) -->
<PartDraw name="indicator_aod" alpha="0" height="225" tintColor="[CONFIGURATION.a3CompFgColor]" width="225" x="0" y="0">
    <Variant mode="AMBIENT" target="alpha" value="255" />
    <Arc centerX="225" centerY="225" endAngle="305" height="350" startAngle="303" width="350">
        <Transform target="startAngle" value="..." />
        <Transform target="endAngle" value="..." />
        <Stroke cap="ROUND" color="#ffffff" thickness="16" />
    </Arc>
</PartDraw>
```

The two Transform `value="..."` expressions are the long startAngle/endAngle formulas in the existing slot — copy them verbatim into the new `indicator_aod` element.

### 2.3 Per-slot scope

You have 4 corner slots × 2 branches per slot (`customRange` Compare + default Default) = 8 places where the indicator pair lives. Each place needs:
- Modify 2 existing PartDraws (add `<Variant>` to hide in AOD)
- Add 1 new PartDraw (the AOD-only thick indicator)

So total: **8 modifications + 8 new elements = 24 element edits.**

Alternative (less surgical): skip the AOD-only indicator on the `customRange` Compare branch since custom-range complications are less common. Only do the default Default branch — halves the work to **4 mods + 4 new elements**.

### 2.4 Optional: skip the black halo in AOD entirely

Instead of duplicating the indicator with a thicker stroke, you can just hide the black halo in AOD (let the colored indicator stand alone on the dark background). The colored indicator's 12-thick stroke is then directly visible without the merging-halo effect.

This is a simpler intervention — just add `<Variant mode="AMBIENT" target="alpha" value="0">` to each `indicator_background` PartDraw. 4-8 element modifications total, no new elements. Try this first; if it's enough, you don't need the thicker AOD indicator at all.

```xml
<!-- Minimal intervention: just hide the black halo in AOD -->
<PartDraw name="indicator_background" alpha="255" height="225" width="225" x="0" y="0">
    <Variant mode="AMBIENT" target="alpha" value="0" />
    <Arc ...>
        <Stroke cap="ROUND" color="#000000" thickness="20" />
    </Arc>
</PartDraw>
```

**Recommendation:** try this minimal intervention first. If the indicator still looks thin in AOD afterwards, escalate to the full thicker-indicator approach.

---

## 3. Caveats

- **The cause may be Wear OS dimming, not geometry.** If your diagnosis in §1 shows same pixel width / lower brightness, this plan is moot. No XML can fix system AOD overlay.
- **AOD power budget.** Wear OS limits AOD pixel counts and color depth. Adding more visible pixels (thicker indicator) consumes more battery and may trigger anti-burn-in shift effects. Track battery impact across a 24h cycle after the change.
- **Burn-in risk.** A perpetually-visible thick indicator at the same screen position will burn into the OLED over many months. Wear OS typically applies a small randomized pixel shift in AOD to mitigate, but extending visible pixels increases risk. Don't make the AOD indicator thicker than 16-18px.
- **`z3_aod_compl=FALSE` makes this all moot.** If the user has complications hidden in AOD (the replica's default for `z3_aod_compl`), none of this work matters. Verify the user's default before investing time.
- **Visual consistency with other slots.** If you implement this on slot 0 only, slots 1/2/3 will look different in AOD. Either do all 4 or skip the feature.

---

## 4. Implementation order

1. **Diagnose** per §1. If cause is Wear OS dimming, stop.
2. **Try the minimal intervention** (§2.4) on slot 0 only. Build + install. Enter AOD with `z3_aod_compl=TRUE`. Compare to before.
3. **If minimal is enough**, propagate the single-line `<Variant>` change to all 4 slots. Done.
4. **If still too thin**, implement the full thicker-indicator approach (§2.2) on slot 0. Verify on device.
5. **Propagate the full approach** to slots 1, 2, 3 with per-slot angle expressions copied from each slot's existing indicator.

---

## 5. Files touched

- `app/src/main/res/raw/watchface.xml` only.

### Minimal intervention (recommended first step)
- 8 lines added (one `<Variant>` element per `indicator_background` PartDraw, in 4 slots × 2 branches).

### Full thicker-indicator
- 32 lines added per slot × 4 slots = ~128 lines, plus 16 existing PartDraws need `<Variant>` modifiers added.

---

## 6. Estimated effort

- **Diagnosis (§1):** 15-30 minutes (screenshots + measurement).
- **Minimal intervention (§2.4) on all 4 slots:** 15 minutes.
- **Full thicker-indicator on all 4 slots × both branches:** 1-2 hours.
- **Full thicker-indicator with only the default Default branch (skipping custom-range):** 30-45 minutes.

---

## 7. Decision matrix

| Diagnosis result | Recommended action |
|---|---|
| Wear OS dimming only | Do nothing. Accept the look. |
| Halo merging, indicator thin but present | Minimal intervention (§2.4). |
| Halo merging + indicator visibly shrunk pixel-wise | Minimal intervention first; if still thin, full thicker-indicator (§2.2). |
| Indicator completely invisible in AOD | Confirm `z3_aod_compl=TRUE`. If yes, full thicker-indicator. If no, this isn't a bug. |
