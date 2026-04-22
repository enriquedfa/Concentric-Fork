# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project overview

Concentric is a Wear OS watch face built entirely with the declarative **Watch Face Format (WFF)** — there is no Java/Kotlin code. The app module's `AndroidManifest.xml` declares `com.google.wear.watchface.format.version` = `4` and `android:hasCode="false"`; all rendering behavior lives in XML resources.

For format reference, always consult the **WFF v4 documentation**: https://developer.android.com/reference/wear-os/wff/watch-face?version=4 — match the `?version=4` query to the manifest, since element/attribute support changes per version.

## Build & tooling

- **Gradle wrapper**: `./gradlew <task>` (use `gradlew.bat` on Windows cmd; the wrapper works from bash).
- **Assemble**: `./gradlew :app:assembleDebug` / `:app:assembleRelease`.
- **Install to a paired watch / emulator**: `./gradlew :app:installDebug` (requires adb-connected Wear OS device). The `android` CLI helper is the preferred way to manage emulators, SDK components, and deploys from the terminal — prefer it over manual `adb`/`sdkmanager` invocations when available.
- **No unit/instrumented tests** exist — the project has zero source code, so typical test tasks are no-ops.
- **AGP 9.1.1** with `android.newDsl=false` in `gradle.properties`. `app/build.gradle.kts` uses `configure<ApplicationExtension> { ... }` (imported from `com.android.build.api.dsl`) instead of the deprecated `android { }` block — keep it that way so AGP 10 removal doesn't break the build.
- `settings.gradle.kts` suppresses `@Incubating` warnings file-wide for the dependency-resolution DSL; leave the suppression in place.

## Architecture

The entire watch face lives in three XML files under `app/src/main/res/`:

| File | Role |
|---|---|
| `xml/watch_face_info.xml` | WFF metadata: preview drawable, category, editability. |
| `xml/watch_face_shapes.xml` | Binds the `CIRCLE` shape at `450×450` to `@raw/watchface`. |
| `raw/watchface.xml` | ~2100-line scene graph containing user configuration + all visuals. |

`raw/watchface.xml` has two top-level sections inside `<WatchFace>`:

1. **`<UserConfigurations>`** (lines ~5–150) — `ColorConfiguration`, `ListConfiguration`, and `BooleanConfiguration` entries expose editor options. Three color palettes (`a0SecondsColor`, `a1ComplicationColor`, `a2AccentColor`) share the same 40-option palette and are referenced elsewhere via `[CONFIGURATION.a0SecondsColor]` expressions. List configs like `z2_mode`, `z1_aod`, `z0_index` drive `Variant` / `Compare` branches in the scene. String labels for all options come from `res/values/strings.xml`.
2. **`<Scene>`** (line ~152 onward) — the scene graph. Rendered elements:
   - Background and index rings (reuses drawables in `res/drawable-nodpi/`).
   - The `numbers` group: 12 `PartText` elements rotated by `[MINUTE] * (-6)` to form the rotating minute ring.
   - Four **`<ComplicationSlot>`** blocks at the corners, each bounded by a `<BoundingArc>` sweeping ~72° around a corner pivot (`centerX`/`centerY` set to 0 or 225 to place the arc's center off-canvas). Each slot has its own `<Complication>` branches for `RANGED_VALUE`, `SHORT_TEXT`, `MONOCHROMATIC_IMAGE`, and `EMPTY` types, with a `<Condition>` that picks `noIcon` vs. default layouts via `textLength([COMPLICATION.TEXT])` and `[COMPLICATION.MONOCHROMATIC_IMAGE] == null`.
   - A fifth bottom-center `ComplicationSlot` (`slotId="4"`) for `SHORT_TEXT` / `SMALL_IMAGE` / `MONOCHROMATIC_IMAGE`.

### Conventions used throughout the file

- **Expressions** use bracketed refs: `[HOUR]`, `[MINUTE]`, `[COMPLICATION.TEXT]`, `[CONFIGURATION.<id>]`. Ternary form: `condition ? a : b`. Helpers like `textLength(...)` work as documented in WFF v4.
- **AOD / ambient** branches are gated by `<Variant mode="AMBIENT" target="alpha" value="..." />` expressions that evaluate `[CONFIGURATION.z1_aod]` against option ids.
- **Redundant defaults are intentionally stripped** — don't re-add `align="CENTER"`, `ellipsis="FALSE"`, `weight="NORMAL"`, `slant="NORMAL"`, `direction="CLOCKWISE"` on `<Arc>`/`<BoundingArc>`, or `hueRotate="0"` / `brightness="1"` on `<HsbFilter>`. The lint inspection will flag them again if reintroduced. `saturate="0"` on `HsbFilter` is *not* a default (desaturation before tinting) — keep it.
- **Known IDE false-positive errors** in `watchface.xml`: `Cannot resolve symbol 'empty'` on `<InlineImage resource="empty" source="COMPLICATION.MONOCHROMATIC_IMAGE" ...>` and `<expr> expected` on `<Parameter expression="&#160;" />`. These are Watch Face Studio export idioms — `source` overrides `resource` at runtime, and `&#160;` injects literal whitespace. They don't affect the build; don't "fix" them without testing the rendered watch face.

### Assets

- `res/drawable-nodpi/` — all raster assets (rings, index marks, complication border, preview). Referenced by `resource="name"` without a type prefix in WFF.
- `res/font/` — Inter and Roboto Mono TTFs, referenced by the filename stem via `<Font family="inter_regular" ... />`.
- `res/mipmap-*` — launcher icons only; not used by the watch face itself.

## Modifying the watch face

When editing `watchface.xml`, keep in mind:
- Coordinates are in the **450×450** canvas declared by `watch_face_shapes.xml`; many nested groups use a **225×225** local frame (quadrants) — check the enclosing `<Group>` before reading `x`/`y`.
- When adding a user-facing option, add the `<ColorOption>`/`<ListOption>` in `<UserConfigurations>` **and** a matching `<string>` in `res/values/strings.xml` (displayed labels come from there).
- Bump `android:value` on the `com.google.wear.watchface.format.version` `<property>` in the manifest only if you start using features from a newer WFF version — and update the docs URL accordingly.
