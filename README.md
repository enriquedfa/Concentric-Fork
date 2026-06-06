# Concentric - Watch Face

<img src='https://play-lh.googleusercontent.com/Qp-fN1rU6bimstpohGY03DlDPMRdYapLK0CXH-utB6HJQhtCLxBERs47vjFP4rvVU-w=w832-h470' style="width:600px;" ></img>

<a href='https://play.google.com/store/apps/details?id=com.watchfacedesigns.Concentric&utm_source=https%3A%2F%2Fgithub.com%2Flukakilic%2Fconcentric-watch-face%2Ftree%2Fmain&pcampaignid=pcampaignidMKT-Other-global-all-co-prtnr-py-PartBadge-Mar2515-1' style="display:inline-block"><img alt='Get it on Google Play' src='https://play.google.com/intl/en_us/badges/static/images/badges/en_badge_web_generic.png' style="width:200px;"/></a>

The Concentric design is originally from the Google Pixel Watch (1). This project recreates it using the [Watch Face Format](https://developer.android.com/training/wearables/wff) (WFF v4) — no Java/Kotlin, just declarative XML. This fork extends the original with richer complication support (ranged-value, goal-progress, weighted-elements, and image/text variants across five slots) plus a CI pipeline.

## Modify the Watch Face

The whole watch face is XML under `app/src/main/res/`:

- `raw/watchface.xml` — the full scene graph: user configuration (colors, modes, AOD) and every visual element.
- `xml/watch_face_info.xml`, `xml/watch_face_shapes.xml` — WFF metadata and the 450×450 circular shape binding.

Watch Face Format is well [documented](https://developer.android.com/training/wearables/wff/watch-face) — pin the docs to `?version=4` to match the manifest. See [`CLAUDE.md`](CLAUDE.md) for an architecture tour and the project conventions.

## Build the Watch Face

This fork builds with Gradle, like current Wear OS watch faces:

```sh
./gradlew :app:assembleDebug      # build a debug APK
./gradlew :app:installDebug       # install to a connected Wear OS 6 device/emulator
```

Every push and pull request runs [`.github/workflows/checks.yml`](.github/workflows/checks.yml), which lints, assembles the APK, validates the WFF XML with Google's validator, and evaluates the memory footprint. Run the validator locally the same way CI does (it exits `0` even on failure, so check the log for `PASSED`/`FAILED`):

```sh
java -jar app/libs/wff-validator.jar 4 app/src/main/res/raw/watchface.xml
```

For more background on building WFF watch faces, see [wear-os-samples/WatchFaceFormat](https://github.com/android/wear-os-samples/tree/main/WatchFaceFormat).
