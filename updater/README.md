# updater

One in-app updater for all the projects in `../`: six Kotlin/Compose apps and two
Flutter apps. The client checks the manifest of the newest GitHub release, asks the
user, and only then downloads and installs.

There is no library module and no Flutter plugin. The Kotlin sources are compiled
straight into each app through `sourceSets`, because every project — Flutter ones
included — builds its APK with the ordinary Android Gradle Plugin. One copy of
the code, eight apps, no AAR to rebuild and no artifact to publish.

## What it costs a project

Three edits, none of which touch the app's own code beyond one line.

### 1. `app/build.gradle.kts`

```kotlin
android {
    // Kotlin projects: ../updater/android/…   Flutter projects: ../../../updater/android/…
    sourceSets {
        getByName("main") {
            // Kotlin projects: $rootDir/../updater   Flutter: $rootDir/../../updater
            kotlin.directories.add("$rootDir/../updater/android/src")
            res.directories.add("$rootDir/../updater/android/res")
        }
    }
}
```

`$rootDir` is the project root, so a Kotlin project at `PRJ/BikeTracker` writes
`$rootDir/../updater/…` and a Flutter project, whose Gradle root is
`PRJ/bikelog/android`, writes `$rootDir/../../updater/…`.

`kotlin` and not `java`: on these AGP/KGP versions the Kotlin source set no
longer inherits the java one, and `.kt` files placed in a java source dir are
simply not compiled — the build fails on unresolved references to `dev.updater`.

`directories.add(…)` rather than `srcDir(…)`: AGP 9 deprecates `srcDir` in
favour of that mutable set. On an older AGP, `kotlin.srcDir("…")` is the same
thing without the warning.

No dependencies are added: the updater runs on `HttpURLConnection`,
`MessageDigest`, `org.json` and `FileProvider` — everything already present.

### 2. `AndroidManifest.xml`

Paste the entries from `android/MANIFEST-SNIPPET.xml`: the two permissions and
the FileProvider. Manifest entries merge from library modules, and shared sources
are not one, so this part is by hand.

### 3. One call at start-up

Kotlin app, in the launcher activity's `onCreate`:

```kotlin
Updater.checkOnStart(this, UpdaterConfig(appKey = "reminderlists", repo = "ReminderLists"))
```

The repository is all an app has to say, and only when its name is not the app
key itself — `bikelog` and `xcalc` pass nothing but the key. The account and the
offer interval are defaults in `UpdaterConfig`, so neither is written eight
times. An app published elsewhere passes a `manifestUrl` of its own, which is
then used exactly as given.

Flutter app, in `MainActivity.onCreate` — the same call, and Dart is not involved
at all:

```kotlin
class MainActivity : FlutterActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Updater.checkOnStart(this, UpdaterConfig(appKey = "bikelog"))
    }
}
```

For a "check for updates" menu entry, `Updater.checkNow(activity, config)` skips
the interval and reports the result either way.

## How often it looks

The server is asked when the app starts, and again whenever its launch activity
comes back to the screen an hour or more after the last answer. Only an answer
counts — a launch with no network does not use the look up. Nothing about the
look is written to preferences: a manifest is well under a kilobyte, and the
hour lives in memory, so a new process — a build installed over this one
included — asks at once.

The app calls `checkOnStart` once, in `onCreate`; the module itself watches the
activity's later starts. Once per process was not enough: an app brought back
from the background gets its activity back without an `onCreate`, and a player
keeps its process for days, so a build published after its one look was never
offered.

What is rationed is the **offer**, not the look. A build the user has already
been shown and answered — Later, or the dialog closed — is not put up again for
`offerIntervalHours`. A build with any other version code has not been seen, so
it is offered the moment it appears, however recently the last one was.

The two were one clock until it became clear what that costs: a look that found
nothing silenced the hours in which the next build was published, so an app
could sit for six hours next to an update it had already been told about.

## Settings

The module keeps its own on/off flag and gives a settings screen the two calls
it needs — `Updater.isEnabled(context)` and `Updater.setEnabled(context, on)`.
The switch itself belongs to the app: every project here draws its settings in
its own style, with its own localized string.

Default is on. Off stops the start-up check only; the About dialog's button and
its silent look both still work, because neither is the unasked-for interruption
the switch exists to stop.

## Asking without showing anything

`Updater.peek(activity, config) { newer -> }` asks the same question and shows
nothing: the answer comes back on the main thread, for a screen that draws it
itself. It remembers nothing — neither the interval nor a declined build — so a
look never costs the start-up check its turn — neither the hourly look nor the
offer interval.

## What the user sees

`checkOnStart` fetches a few kilobytes of manifest and stays silent unless there
is a newer build: nothing is downloaded before the answer.

```
        New version
        Version: 0.3.20260828
        Build: 71

  [Later]   [Cancel]   [Update]
```

- **Update** — downloads with a progress bar that can be cancelled, verifies the
  SHA-256, hands the APK to the system installer.
- **Cancel** — this build is put aside and not offered again. A newer one still
  gets through.
- **Later** — this build is not put up again for `offerIntervalHours` (6 by
  default); after that, or as soon as a newer build appears, it is offered again.

The dialog is a plain `AlertDialog` rather than Compose or a Flutter widget: one
updater serves both stacks. Its colours are its own rather than the host's, in
`UpdaterColors` — a white card, black text, and outlined violet buttons, the
filled one being whatever the dialog is really asking for. `../about` draws from the same
file, so the card its update button opens is the card it came from.

Strings are localized in `UpdaterStrings.kt` (en/ru/uk) rather than in
`res/values-*`: shared sources have no `R` of their own, and reaching the app's
one by name would hide the strings from the resource shrinker that every project
runs in release.

## Where builds come from

Each app reads the manifest of its own newest GitHub release:

```
https://github.com/<user>/<repo>/releases/latest/download/latest.json
```

Nothing of ours is between the phone and the APK — no server to keep running, no
address to change, and the update works wherever the device is, not only at
home. `PROTOCOL.md` has the file itself.

Publishing is `23-ToUpdate.sh`, the same file in every project: run by hand
after `22-RelUpload.sh`, it asks GitHub what the release carries, reads the
version code and the SHA-256 out of each APK, and uploads the manifest beside
them.

## One manifest, every architecture

`latest.json` names all the splits the release carries, and the device chooses:
the client walks its own `Build.SUPPORTED_ABIS`, so a 64-bit phone takes the
arm64 APK and an armeabi-v7a TV box the v7a one out of the same file, falling
back to the universal APK when neither is published.

An update need not match the ABI of what is installed — Android compares the
package, the signature and the version code — so the fallback is a real one and
not a compromise.
