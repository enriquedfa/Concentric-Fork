package com.dfamaya.concentric.companion

data class ChangelogEntry(
    val version: String,
    val date: String,
    val changes: List<String>,
)

/** Newest first. Keep in sync with releases — the About tab renders this list. */
val CHANGELOG: List<ChangelogEntry> = listOf(
    ChangelogEntry(
        version = "1.0",
        date = "July 2026",
        changes = listOf(
            "Complications: five slots — four corner arcs plus a left-edge pill — now covering goal progress and weighted elements on top of ranged values, short text and icons.",
            "Ranged complications with bounds other than 0–100 are mapped correctly by a custom range indicator.",
            "Colors: the palette was rebuilt on Material Design 3 with four separate roles (digits, indices, bars, icons) and a set of ready-made preset flavors.",
            "Always-on display: subtler ambient animations and new graphics that track the original Concentric design more closely.",
            "Settings: the on-watch editor was reorganised around the new color roles and display options.",
            "This companion phone app: installs Concentric on your paired Wear OS watch, detects whether a watch is connected and whether the face is already installed, and offers quick links to rate, send feedback, and share.",
        ),
    ),
)
