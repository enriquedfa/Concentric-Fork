---
name: wff
description: Author and modify Watch Face Format (WFF) v4 XML for the Concentric watch face. Load this whenever editing app/src/main/res/raw/watchface.xml, watch_face_info.xml, watch_face_shapes.xml, or anything that references WFF expressions (`[CONFIGURATION.*]`, `[COMPLICATION.*]`, `[MINUTE]`, etc.).
---

# Watch Face Format (WFF) — Concentric

## Target

- **WFF version: 4** (`AndroidManifest.xml` declares `com.google.wear.watchface.format.version` = `4`)
- **Canvas: 450 × 450, `clipShape="CIRCLE"`** (`xml/watch_face_shapes.xml`)
- **No Kotlin/Java** — `android:hasCode="false"`. Everything is XML.
- **Hardware floor: Wear OS 6.** Anything below that is out of scope — do not add backwards-compat shims for older Wear OS versions, and feel free to use any WFF feature available at v4 on Wear OS 6.
- **Docs URL pinned to v4**: https://developer.android.com/reference/wear-os/wff/watch-face?version=4

## Authoritative reference (use this BEFORE guessing)

The full official XSD is vendored at **`reference/wff-schema/v4/`**. Treat it as ground truth. If an element/attribute is not defined there, it does not exist in v4 — do not invent it. Useful entry points:

| Looking for | Read |
|---|---|
| Top-level grammar (`<WatchFace>`, `<Scene>`, attributes) | `reference/wff-schema/v4/watchface.xsd`, `sceneElement.xsd` |
| Expression syntax (`[MINUTE]`, ternary, helpers) | `reference/wff-schema/v4/common/expressionsElement.xsd` |
| `<Condition>`, `<Compare>`, `<Default>` | `reference/wff-schema/v4/common/conditionElement.xsd` |
| `<Variant>` (AOD, ambient) | `reference/wff-schema/v4/common/variant/variantElements.xsd` |
| `<Transform>`, `<Animation>`, pivots | `reference/wff-schema/v4/common/transform/` |
| `<ComplicationSlot>`, `<Complication>`, `<BoundingArc>`, `<BoundingBox>` | `reference/wff-schema/v4/complication/` |
| `<UserConfigurations>`, `<ListConfiguration>`, `<ColorConfiguration>`, `<BooleanConfiguration>` | `reference/wff-schema/v4/userConfiguration/` |
| `<Group>`, `<PartImage>`, `<PartDraw>`, `<PartText>`, animated images | `reference/wff-schema/v4/group/`, `group/part/` |
| Color/angle/numeric primitive types | `reference/wff-schema/v4/common/simpleTypes/` |

**When unsure about an attribute, grep the XSD first**, e.g. `Grep -path reference/wff-schema/v4 -pattern 'name="endAngle"'`.

## Patterns to copy from (NOT to write from scratch)

`patterns/` holds known-working fragments lifted from the live `raw/watchface.xml`. Prefer copying from there over generating new structure. When you find yourself about to author a `<Variant>`, `<Condition>`, or `<ComplicationSlot>` from memory, stop and read the matching pattern first.

| Pattern file | What it shows |
|---|---|
| `patterns/complication-slot-corner-arc.xml` | Corner complication (slot 0) with `<BoundingArc>`, four `<Complication>` types, `noIcon` text-length condition. |
| `patterns/complication-slot-pill.xml` | Bottom pill complication (slot 4) with `<BoundingBox>`, three `<Complication>` types, title-vs-icon condition. |
| `patterns/listconfig-gate-siblings.xml` | The "parallel sibling Group" workaround for the `ListConfiguration`-inside-`Default` quirk (see antipatterns). |
| `patterns/aod-variant.xml` | `<Variant mode="AMBIENT">` gating alpha against `[CONFIGURATION.z1_aod]` option ids. |
| `patterns/condition-compare-default.xml` | Static-content `<Condition><Expressions>/<Compare>/<Default>` switch (this form is safe). |
| `patterns/rotating-text-ring.xml` | Twelve `<PartText>` rotated by `[MINUTE] * (-6)` — the minute ring. |
| `patterns/user-config-color-list-bool.xml` | `<ColorConfiguration>` / `<ListConfiguration>` / `<BooleanConfiguration>` shapes used in this project. |

## Project conventions (Concentric-specific)

Mirrored from the project `CLAUDE.md`. Apply these when editing:

- Coordinates are **450 × 450**; many nested `<Group>`s use a **225 × 225** local frame (quadrants). Always check the enclosing `<Group>` `width/height/x/y` before reading a child's geometry.
- **Three shared color palettes** (`a0SecondsColor`, `a1ComplicationColor`, `a2AccentColor`) — same 40 options each. Reference them via `[CONFIGURATION.<id>]`. New user-facing options need an entry in `<UserConfigurations>` **and** a matching `<string>` in `res/values/strings.xml`.
- **Asset references in WFF** use bare names: `resource="index_minutes_0"`, `family="inter_regular"`. No `@drawable/`, no `@font/`, no extension. Files live in `res/drawable-nodpi/` and `res/font/`.
- **Redundant defaults are stripped intentionally.** Do not re-add: `align="CENTER"`, `ellipsis="FALSE"`, `weight="NORMAL"`, `slant="NORMAL"`, `direction="CLOCKWISE"`, `hueRotate="0"`, `brightness="1"`. Lint flags them. `saturate="0"` on `<HsbFilter>` is **not** a default — keep it (it desaturates before tinting).
- **AOD branches** are gated by `<Variant mode="AMBIENT" target="alpha" value="..." />` against `[CONFIGURATION.z1_aod]`.
- **Indentation**: tabs, matching the existing file. Keep attribute order alphabetical when adding new attributes (existing pattern).

## Hard rules (do not violate)

1. **Never invent elements or attributes.** If it's not in the XSD under `reference/wff-schema/v4/`, it does not exist. Grep before suggesting.
2. **Never put a `<ListConfiguration>` inside a `<Condition>`'s `<Default>` or `<Compare>` branch.** It silently picks the first option only. Use the parallel-sibling-Group pattern (`patterns/listconfig-gate-siblings.xml`).
3. **Never bump the manifest's `format.version`** without a concrete v4-only feature to justify it, and update the docs URL accordingly.
4. **Never "fix" the two known IDE false-positives** without testing the rendered face: `Cannot resolve symbol 'empty'` on `<InlineImage resource="empty" source="COMPLICATION.MONOCHROMATIC_IMAGE" ...>` and `<expr> expected` on `<Parameter expression="&#160;" />`. Both are intentional WFS-export idioms.
5. **Never suggest Java/Kotlin code paths.** This module has no source code; runtime behavior is pure XML.
6. **Always preview an edit's effect on AOD** — search for nearby `<Variant mode="AMBIENT">` and confirm the change behaves correctly when ambient.

## Workflow when editing `watchface.xml`

1. Read the surrounding `<Group>` to understand the local coordinate frame.
2. Find the closest matching pattern in `patterns/` and copy its skeleton.
3. If introducing a new attribute, grep the XSD: `Grep -path reference/wff-schema/v4 -pattern 'name="<attr>"'`.
4. If introducing a config option, add the `<UserConfigurations>` entry **and** the `res/values/strings.xml` label in the same change.
5. Skim `antipatterns.md` if the change involves `<Condition>`, `<ListConfiguration>`, AOD, or complications — the failure modes there are non-obvious.
6. After editing, run `./gradlew :app:assembleDebug` — the AGP build does basic XML sanity checks.

## Validator (not yet wired up)

Google ships a `dwf-format-validator` and `validator.jar` artifact in `google/watchface` on GitHub that statically verifies a built APK against the WFF spec. It is **not yet integrated** in this project. When set up, it should run in the loop after every edit. Until then, rely on the XSD + `assembleDebug` + on-device preview.

See `antipatterns.md` next door for concrete past failures and why they happened.
