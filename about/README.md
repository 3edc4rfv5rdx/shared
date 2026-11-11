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
host happens to use. The card, the pills and the way on screen are `AboutUi`,
in the colours of `AboutColors`; `../updater` draws its own dialogs with the same
kit. The dependency runs one way: About compiles without the updater, and the
updater cannot compile without About.

## What it costs a project

### 1. `app/build.gradle.kts`

```kotlin
android {
    sourceSets {
        getByName("main") {
            // Kotlin projects: $rootDir/../shared/about   Flutter: $rootDir/../../shared/about
            kotlin.directories.add("$rootDir/../shared/about/android/src")
        }
    }
}
```

That is all a build without the updater needs — a store build, say — and the
dialog then has no update button. A build that carries `../updater` adds its
sources as `../updater` describes and passes `AboutUpdater(UPDATER_CONFIG)` as
`updates`; which list a build uses is the switch, and a product flavor per store
can hold one each.

No `res` set and no manifest entries: the module has no resources, no strings of
its own in `res/values-*` — they are in `AboutStrings.kt`, so a release build
that shrinks resources cannot drop them — and no permissions. Opening a link or
a mail client needs none.

### 2. One call, where the About screen used to be built

```kotlin
About.show(
    activity,
    AboutConfig(
        repo = "Steps",
        buildDate = BuildConfig.BUILD_DATE,
        updates = AboutUpdater(UPDATER_CONFIG), // only in a build that carries ../updater
    ),
)
```

That is the whole of it. The name on the dialog is the launcher label, the
version is read off the installed package, and the GitHub address is built from
`repo` and the account every app here shares — so what an app hands over is the
repository and the build date, which no API knows.

### 3. The accent, once and whenever it changes

```kotlin
About.accent = 0xFF1565C0.toInt()   // null, or never set: blue-grey
```

One value for the whole process, read each time a dialog is built — About's and
the updater's alike. Not a field of `AboutConfig`, because the updater's
start-up dialog comes up before anyone has opened About and has to be in the
same colour. Set it in `onCreate`, before the start-up check, and again when the
app's accent changes; in Compose a `SideEffect` inside the theme does both.

`extra` adds lines of its own, label to value, under the ones every app shows:

```kotlin
AboutConfig(
    repo = "Steps",
    buildDate = BuildConfig.BUILD_DATE,
    extra = listOf("Device id" to deviceId),
)
```

`description` puts a sentence above the facts, for an app whose name does not
say what it is. The update button opens as "Check for updates" on the pale tint
every other button uses and becomes a filled "Update" only once the server has
confirmed there is a newer build, so it never promises one that is not there.
Leaving `updates` out drops it altogether. `email` is a default — the same
mailbox in every app — and is there to be overridden, not to be repeated.

## What it puts on screen

The launcher label as the title, then

- `Version` — as installed, not as written in the sources
- `Build date` — from `BuildConfig.BUILD_DATE`, omitted when empty
- anything `extra` carries
- `GitHub` — the repository page, opens in a browser
- `E-mail` — opens a mail client on `mailto:`

and, at the bottom, `Update` (only when `updates` is given) and `Close`. Update skips the updater's six-hour
interval and answers either way — silence from a button is a broken button —
and the About dialog closes first, so the updater's own dialog is not covered.

Russian and Ukrainian are in `AboutStrings.kt` beside English; the device locale
picks one. `GitHub` and `E-mail` are names, not words, and stay as they are.
