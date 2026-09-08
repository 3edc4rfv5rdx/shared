# about

One About dialog for all the projects in `../`. It says what the app is, which
build is running and where to reach its author, and it offers the update check
that `../updater` otherwise only makes by itself every six hours.

Like `../updater` and `../backups`, there is no library module and no Flutter
plugin: the Kotlin sources are compiled straight into each app through
`sourceSets`. One copy of the code, one look, no AAR to publish. It adds no
dependency at all — the whole thing is `AlertDialog`, `LinearLayout` and
`TextView`.

The dialog is drawn in code rather than in Compose, for the same reason the
updater's are: one copy serves Compose apps and Flutter apps alike, so its look
belongs to it in all of them instead of half-belonging to whichever toolkit the
host happens to use. Black on a white card, in every app and either theme.

## What it costs a project

### 1. `app/build.gradle.kts`

```kotlin
android {
    sourceSets {
        getByName("main") {
            // Kotlin projects: $rootDir/../about   Flutter: $rootDir/../../about
            kotlin.directories.add("$rootDir/../about/android/src")
        }
    }
}
```

`../updater` has to be on that list too: About reads the repository address out
of `UpdaterConfig` and calls `Updater.checkNow` for its button. Every project
here already compiles it in.

No `res` set and no manifest entries: the module has no resources, no strings of
its own in `res/values-*` — they are in `AboutStrings.kt`, so a release build
that shrinks resources cannot drop them — and no permissions. Opening a link or
a mail client needs none.

### 2. One call, where the About screen used to be built

```kotlin
About.show(
    activity,
    AboutConfig(
        updater = UPDATER_CONFIG,
        buildDate = BuildConfig.BUILD_DATE,
    ),
)
```

That is the whole of it. The name on the dialog is the launcher label, the
version is read off the installed package, and the GitHub address is built from
the account and repository already named in `UpdaterConfig` — so the only thing
an app has to hand over is the build date, which no API knows.

`extra` adds lines of its own, label to value, under the ones every app shows:

```kotlin
AboutConfig(
    updater = UPDATER_CONFIG,
    buildDate = BuildConfig.BUILD_DATE,
    extra = listOf("Device id" to deviceId),
)
```

`description` puts a sentence above the facts, for an app whose name does not
say what it is. `offerUpdate = false` drops the Update button, for an app whose
builds are not published as releases. `email` is a default — the same mailbox in every app —
and is there to be overridden, not to be repeated.

## What it puts on screen

The launcher label as the title, then

- `Version` — as installed, not as written in the sources
- `Build date` — from `BuildConfig.BUILD_DATE`, omitted when empty
- anything `extra` carries
- `GitHub` — the repository page, opens in a browser
- `E-mail` — opens a mail client on `mailto:`

and, at the bottom, `Update` and `Close`. Update skips the updater's six-hour
interval and answers either way — silence from a button is a broken button —
and the About dialog closes first, so the updater's own dialog is not covered.

Russian and Ukrainian are in `AboutStrings.kt` beside English; the device locale
picks one. `GitHub` and `E-mail` are names, not words, and stay as they are.
