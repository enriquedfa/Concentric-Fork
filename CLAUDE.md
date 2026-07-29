# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project overview

Concentric (`com.dfamaya.concentric`) is a Wear OS watch face built entirely with the declarative **Watch Face Format (WFF)** — there is no Java/Kotlin code in it. The app module's `AndroidManifest.xml` declares `com.google.wear.watchface.format.version` = `4` and `android:hasCode="false"`; all rendering behavior lives in XML resources. WFF v4 requires the Wear OS 6 runtime, so `minSdk`/`targetSdk` are pinned to `36` — keep them in lockstep with the declared format version.

The project started as a fork of [lukakilic/concentric-watch-face](https://github.com/lukakilic/concentric-watch-face) (MIT) and is now independently maintained; the original author is credited in `README.md` and `LICENSE`.

Two Gradle modules (`settings.gradle.kts`):

- **`:app`** — the pure-XML WFF watch face. The heart of the project; the "no code" rule applies here.
- **`:mobile`** — a Jetpack Compose **phone companion app** that helps users install the face on their watch (Play has trouble offering standalone watch faces on some phones). It shares the watch face's `applicationId` so Play treats them as one multi-form-factor listing, and its FAB uses `RemoteActivityHelper` to open the face's Play listing on the paired watch. It reads `app/src/main/res/values/wear.xml`'s Data Layer capability (`concentric_watchface`) to tell "installed" from "not installed" — keep that string in sync with `WATCH_FACE_CAPABILITY` in `mobile/.../WatchActions.kt`. This module *does* contain Kotlin.

For format reference, always consult the **WFF v4 documentation**: https://developer.android.com/reference/wear-os/wff/watch-face?version=4 — match the `?version=4` query to the manifest, since element/attribute support changes per version.

## Build & tooling

- **Docs**: `android docs` to search docs through android CLI, it might or might not be useful for wear os.
- **Gradle wrapper**: `./gradlew <task>` (use `gradlew.bat` on Windows cmd; the wrapper works from bash).
- **Assemble**: `./gradlew :app:assembleDebug` / `:app:assembleRelease`; the companion is `./gradlew :mobile:assembleDebug`.
- **Install to a paired watch / emulator**: `./gradlew :app:installDebug` (requires adb-connected Wear OS device). The `android` CLI helper is the preferred way to manage emulators, SDK components, and deploys from the terminal — prefer it over manual `adb`/`sdkmanager` invocations when available.
- **No app source / no tests** — the `:app` module is pure WFF XML (no Java/Kotlin), and `:mobile` has no test source set, so there are no unit or instrumented tests and typical test tasks are no-ops. Correctness is enforced by lint, the WFF validator, and a memory-footprint check (see CI below).
- **WFF validator** (run before pushing): `java -jar app/libs/wff-validator.jar 4 app/src/main/res/raw/watchface.xml`. It exits `0` even when validation fails, so scan the output for `PASSED` / `FAILED`.
- **CI** (`.github/workflows/checks.yml`, on every push/PR): runs lint and `assembleDebug` for both modules (uploading both debug APKs as artifacts), the WFF validator above, and a memory-footprint evaluation (`app/libs/memory-footprint.jar`).
- **AGP** (version pinned in `gradle/libs.versions.toml`, applied via `alias(libs.plugins.android.application)`) with `android.newDsl=false` in `gradle.properties`. `app/build.gradle.kts` uses `configure<ApplicationExtension> { ... }` (imported from `com.android.build.api.dsl`) instead of the deprecated `android { }` block — keep it that way so AGP 10 removal doesn't break the build.
- `settings.gradle.kts` suppresses `@Incubating` warnings file-wide for the dependency-resolution DSL; leave the suppression in place.

## Architecture

The rendered watch face is three XML files under `app/src/main/res/`; everything else in the repo is tooling/reference that never ships in the APK (see [Supporting files & tooling](#supporting-files--tooling)).

| File | Role |
|---|---|
| `xml/watch_face_info.xml` | WFF metadata: preview drawable, category, editability. |
| `xml/watch_face_shapes.xml` | Binds the `CIRCLE` shape at `450×450` to `@raw/watchface`. |
| `raw/watchface.xml` | ~12.8k-line scene graph containing user configuration + all visuals. |

`raw/watchface.xml` has two top-level sections inside `<WatchFace>`:

1. **`<UserConfigurations>`** (lines ~12–1239) — `ColorConfiguration`, `ListConfiguration`, `BooleanConfiguration`, and a `<Flavors>` block expose editor options. Four color palettes (`a0PrimaryColor`, `a1AccentColor`, `a2CompBaseColor`, `a3CompFgColor`) share the same 60-option palette and are referenced elsewhere via `[CONFIGURATION.a0PrimaryColor]` expressions. List configs like `z1_mode`, `z1_aod`, `z0_index` drive `Variant` / `Compare` branches in the scene. String labels for all options come from `res/values/strings.xml`.
2. **`<Scene>`** (line ~1240 onward) — the scene graph. Rendered elements:
   - Background and index rings (reuses drawables in `res/drawable-nodpi/`).
   - The `numbers` group: 12 `PartText` elements rotated by `[MINUTE] * (-6)` to form the rotating minute ring.
   - Four **`<ComplicationSlot>`** blocks at the corners, each bounded by a `<BoundingArc>` sweeping ~72° around a corner pivot (`centerX`/`centerY` set to 0 or 225 to place the arc's center off-canvas). Each slot has its own `<Complication>` branches for `RANGED_VALUE`, `GOAL_PROGRESS`, `WEIGHTED_ELEMENTS`, `SHORT_TEXT`, `MONOCHROMATIC_IMAGE`, and `EMPTY` types, with a `<Condition>` that picks `noIcon` vs. default layouts via `textLength([COMPLICATION.TEXT])` and `[COMPLICATION.MONOCHROMATIC_IMAGE] == null`.
   - A fifth left-edge pill `ComplicationSlot` (`slotId="4"`, `name="left_pill_complication"`, vertically centered at the left edge) for `SHORT_TEXT` / `SMALL_IMAGE` / `MONOCHROMATIC_IMAGE`.
   - Every `ComplicationSlot`'s `displayName` must resolve to a string in `res/values/strings.xml` (bare name, no `@string/` prefix) — these labels show up in the watch face editor.

### Conventions used throughout the file

- **Expressions** use bracketed refs: `[HOUR]`, `[MINUTE]`, `[COMPLICATION.TEXT]`, `[CONFIGURATION.<id>]`. Ternary form: `condition ? a : b`. Helpers like `textLength(...)` work as documented in WFF v4.
- **AOD / ambient** branches are gated by `<Variant mode="AMBIENT" target="alpha" value="..." />` expressions that evaluate `[CONFIGURATION.z1_aod]` against option ids.
- **Redundant defaults are intentionally stripped** — don't re-add `align="CENTER"`, `ellipsis="FALSE"`, `weight="NORMAL"`, `slant="NORMAL"`, `direction="CLOCKWISE"` on `<Arc>`/`<BoundingArc>`, or `hueRotate="0"` / `brightness="1"` on `<HsbFilter>`. The lint inspection will flag them again if reintroduced. `saturate="0"` on `HsbFilter` is *not* a default (desaturation before tinting) — keep it.
- **Known IDE false-positive errors** in `watchface.xml`: `Cannot resolve symbol 'empty'` on `<InlineImage resource="empty" source="COMPLICATION.MONOCHROMATIC_IMAGE" ...>` and `<expr> expected` on `<Parameter expression="&#160;" />`. These are Watch Face Studio export idioms — `source` overrides `resource` at runtime, and `&#160;` injects literal whitespace. They don't affect the build; don't "fix" them without testing the rendered watch face.

### WFF quirks (learned the hard way)

- **`ListConfiguration` does NOT re-evaluate when nested inside a `<Condition><Default>` branch.** If a `<ListConfiguration>` lives inside the `Default` (or `Compare`) child of an outer `<Condition>`, the editor switch will appear to do nothing — only the first `<ListOption>` ever renders. This was the bug introduced in commit `c29078f` (seconds index stuck on option 0) and reverted afterward.
  - **Pattern to use instead**: when you need to gate visibility on a boolean, use parallel sibling `<Group>`s, each with a `<Transform target="alpha" value="[CONFIGURATION.flag] ? 0 : 255" />` (and the inverse on the other group). Keep `<ListConfiguration>` as a direct child of its Group. The seconds `index_default` / `index_forced` siblings in `raw/watchface.xml` are the canonical example.
  - **Note**: `<Condition>` with inline `<Compare expression="...">`/`<Default>` *does* work for choosing between static content (it's used for the `small` z1_mode switch). The quirk only bites when a `<ListConfiguration>` is inside the chosen branch.

### Assets

- `res/drawable-nodpi/` — all raster assets (rings, index marks, complication border, preview). Referenced by `resource="name"` without a type prefix in WFF.
- `res/font/` — Inter (`inter_regular`, `inter_medium`), Roboto (`roboto_sb`), and Roboto Mono (`roboto_mono_sb`) TTFs, referenced by the filename stem via `<Font family="inter_regular" ... />`.
- `res/mipmap-*` — launcher icons only; not used by the watch face itself.

### Supporting files & tooling

Everything below lives outside `app/src/main/res/` and never ships in the APK — it's reference material and CI tooling:

| Path | Role |
|---|---|
| `reference/wff-schema/v4/` | Vendored copy of Google's official **WFF v4 XSD**. Ground truth for what elements/attributes exist — if it isn't here, it isn't in v4. The `wff` skill treats it as authoritative; grep it before inventing an attribute. |
| `reference/original notes/` | Markdown research/porting notes from replicating the original Pixel "Concentric 2.0" (visual deltas, schema findings, feature plans). Background only — not consumed by the build, and references a decompiled source project that isn't in this repo. |
| `tools/generate_index.py` | Pillow helper that renders pixel-perfect index/tick-ring PNGs for `res/drawable-nodpi/`. Run by hand when regenerating ring assets; not part of the Gradle build. |
| `app/libs/wff-validator.jar`, `app/libs/memory-footprint.jar` | Google's WFF validator and memory-footprint evaluator. Invoked by CI and runnable locally (see Build & tooling). |
| `.github/workflows/checks.yml` | CI pipeline: lint, assemble + APK upload (watch face and companion), WFF validation, memory-footprint evaluation. |
| `mobile/` | Jetpack Compose phone companion app (Kotlin). Branding lives in `res/values/strings.xml`, the target package / feedback address / capability id in `WatchActions.kt`, and the release notes shown in the About tab in `Changelog.kt`. |
| `.claude/skills/wff/` | Project skill (`SKILL.md`, `antipatterns.md`, `patterns/`) for authoring WFF XML — load it before editing `watchface.xml`. Committed and shared; `.claude/settings.local.json` is per-machine and is gitignored. |

## Modifying the watch face

Most modification will go to `watchface.xml`, keep in mind:
- Coordinates are in the **450×450** canvas declared by `watch_face_shapes.xml`; many nested groups use a **225×225** local frame (quadrants) — check the enclosing `<Group>` before reading `x`/`y`.
- When adding a user-facing option, add the `<ColorOption>`/`<ListOption>` in `<UserConfigurations>` **and** a matching `<string>` in `res/values/strings.xml` (displayed labels come from there).
- Bump `android:value` on the `com.google.wear.watchface.format.version` `<property>` in the manifest only if you start using features from a newer WFF version — and update the docs URL accordingly.
