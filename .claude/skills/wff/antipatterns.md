# WFF antipatterns — concrete failures observed in this project

Mistakes that look right but break in non-obvious ways. Read this before editing
`<Condition>`, `<ListConfiguration>`, AOD branches, or complications. New
entries should follow the same shape: **what looked right → what actually
happened → why → what to do instead**.

---

## 1. `<ListConfiguration>` inside a `<Condition>` branch silently picks option 0

**What looked right.** Wrap a list configuration inside a `<Condition>` so that
"if mode is small, show this list of indices; otherwise, show that list":

```xml
<!-- DO NOT DO THIS -->
<Condition>
    <Expressions>
        <Expression name="small">[CONFIGURATION.z1_mode] == 1</Expression>
    </Expressions>
    <Compare expression="small">
        <ListConfiguration id="z0_index">
            <ListOption id="0">...</ListOption>
            <ListOption id="1">...</ListOption>
            <ListOption id="2">...</ListOption>
        </ListConfiguration>
    </Compare>
    <Default>
        <ListConfiguration id="z0_index">
            <ListOption id="0">...</ListOption>
            <ListOption id="1">...</ListOption>
            <ListOption id="2">...</ListOption>
        </ListConfiguration>
    </Default>
</Condition>
```

**What actually happened.** Switching the index option in the editor did
nothing. Only `ListOption id="0"` ever rendered, regardless of which option the
user picked. Reproduced live in commit `c29078f` ("seconds index stuck on
option 0") and reverted in `7a8507e`.

**Why.** A `<ListConfiguration>` placed inside the chosen branch of an outer
`<Condition>` does NOT re-evaluate when the user changes the option — it falls
through to the first child. The Condition resolves once at scene
construction; the inner ListConfiguration's reactivity gets neutered.

**What to do instead.** Use parallel sibling `<Group>`s, each with a
`<Transform target="alpha">` driven by the gate. Each group contains its
`<ListConfiguration>` as a DIRECT child. See
`patterns/listconfig-gate-siblings.xml`.

**Note.** `<Condition>` *is* fine when both branches contain only static
content (no nested `<ListConfiguration>`). The `small`/`default` swap in
`patterns/condition-compare-default.xml` works correctly.

---

## 2. "Fixing" the `Cannot resolve symbol 'empty'` IDE error on `<InlineImage>`

**What looked right.** Android Studio underlines `resource="empty"` on:

```xml
<InlineImage
    height="28"
    resource="empty"
    source="COMPLICATION.MONOCHROMATIC_IMAGE"
    width="28" />
```

with `Cannot resolve symbol 'empty'`. The instinct is to delete `resource` or
point it at a real drawable.

**What actually happened.** Deleting `resource` makes the build fail (it's
required by the schema). Pointing it at a real drawable replaces the
runtime-supplied complication glyph with a static image.

**Why.** This is a Watch Face Studio export idiom. `resource` is required at
the schema level as a fallback, but `source="COMPLICATION.MONOCHROMATIC_IMAGE"`
overrides it at runtime. The literal name `empty` is conventionally a
zero-content drawable; the IDE doesn't know about the runtime override and
flags the symbol.

**What to do instead.** Leave it alone. The build succeeds; the watch
renders correctly. Add a code-comment if reviewers might be confused.

---

## 3. "Fixing" the `<expr> expected` IDE error on `<Parameter expression="&#160;" />`

**What looked right.** The IDE flags `<Parameter expression="&#160;" />` as
malformed because `&#160;` is whitespace, not an expression. The instinct is
to replace it with `[COMPLICATION.TEXT]` or to delete the Parameter.

**What actually happened.** Removing or "fixing" it changes the visible spacing
of the rendered text — the literal non-breaking space is doing real work in
`<TextCircular>` / `<Template>` to control kerning and prevent collapse.

**Why.** WFS uses `&#160;` (non-breaking space) as a literal whitespace token
inside `<Template>` parameter slots. The IDE's expression validator doesn't
recognize whitespace-only "expressions."

**What to do instead.** Leave it alone. It compiles and renders correctly.

---

## 4. Re-adding redundant attribute defaults that lint flags

**What looked right.** When copying or hand-writing elements, it feels safer
to be explicit:

```xml
<!-- DO NOT DO THIS -->
<Arc align="CENTER" direction="CLOCKWISE" ... />
<Text ellipsis="FALSE" weight="NORMAL" slant="NORMAL">...</Text>
<HsbFilter hueRotate="0" brightness="1" saturate="0" />
```

**What actually happened.** The Android Studio inspection flags every one of
those redundant attributes. Reviewing diffs becomes noisier; merge conflicts
multiply.

**Why.** Concentric intentionally strips attributes whose values match the
schema default. The lint rule was tuned to enforce this.

**What to do instead.** Omit defaults. The full list of "do not re-add":

| Element | Attribute | Default to omit |
|---|---|---|
| `<Arc>`, `<BoundingArc>` | `direction` | `CLOCKWISE` |
| `<Text>` | `align` | `CENTER` |
| `<Text>` | `ellipsis` | `FALSE` |
| `<Font>` | `weight` | `NORMAL` |
| `<Font>` | `slant` | `NORMAL` |
| `<HsbFilter>` | `hueRotate` | `0` |
| `<HsbFilter>` | `brightness` | `1` |

**Exception.** `<HsbFilter saturate="0" />` is NOT a default — it desaturates
before tinting and is load-bearing. Keep it.

---

## 5. Bumping the manifest WFF version "to be safe"

**What looked right.** `com.google.wear.watchface.format.version` is set to
`4`. WFF 5 exists. Bumping it might unlock newer features.

**What actually happened.** Versions above what the device's Wear OS
implementation supports cause the watch face to silently fail to install or
render on older hardware.

**Why.** The version field is a contract: "this watch face requires features
from version N." Devices with older runtimes refuse to load it.

**What to do instead.** Only bump when you actually use a v5+ feature, and
update the docs URL in `CLAUDE.md` and `SKILL.md` (`?version=N`) at the same
time. Confirm the hardware floor with the user before bumping.

---

## 6. Inventing element or attribute names that "feel WFF-y"

**What looked right.** Adding `<Layer>`, `<Animation interpolator="...">`,
`<Group blendMode="MULTIPLY">`, or any element/attribute that "fits the
pattern" of what WFF seems to support.

**What actually happened.** The build sometimes accepts the unknown attribute
silently and ignores it. The watch face renders, but the new attribute does
nothing — and no error tells you so.

**Why.** WFF is XML; unknown attributes don't fail validation universally.
Training data on WFF is thin and conflates v1/v2/v3/v4. Hallucinated APIs
look plausible.

**What to do instead.** Before suggesting any new element or attribute, grep
the vendored XSD:

```
Grep -path reference/wff-schema/v4 -pattern 'name="<attr>"'
Grep -path reference/wff-schema/v4 -pattern 'element name="<Element>"'
```

If it's not there, it does not exist in v4. Don't invent it.

---

## 7. Using `@drawable/...` or `@font/...` references inside WFF resource attributes

**What looked right.** Coming from regular Android XML, references to
drawables and fonts use the `@drawable/foo` and `@font/bar` idioms. Applying
the same to WFF:

```xml
<!-- DO NOT DO THIS -->
<Image resource="@drawable/index_seconds_0" />
<Font family="@font/inter_regular" ... />
```

**What actually happened.** Build error or silent broken render — the
runtime can't resolve the prefixed name.

**Why.** WFF resource attributes take the bare resource name (drawable basename
or font filename stem), not Android's `@type/name` reference syntax.

**What to do instead.** `resource="index_seconds_0"`, `family="inter_regular"`.
File extensions are also omitted.
