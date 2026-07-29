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
            "Companion phone app to install Concentric on your paired Wear OS watch.",
            "Detects whether a watch is connected and whether the face is already installed.",
            "Quick links to rate the face and send feedback.",
        ),
    ),
)
