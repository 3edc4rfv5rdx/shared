# updater

One in-app updater for all the projects in `../`: six Kotlin/Compose apps and two
Flutter apps. The client checks a static manifest on the home Apache, asks the
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
Updater.checkOnStart(this, UpdaterConfig(appKey = "reminderlists"))
```

The app key is all an app has to say: the server address and the check interval
are defaults in `UpdaterConfig`, so the machine can move without eight apps
being edited. An app somewhere else passes `baseUrl` of its own.

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
- **Later** — nothing is remembered; the next check after the interval
  (`checkIntervalHours`, 6 by default) offers it again.

The dialog is a plain `AlertDialog` rather than Compose or a Flutter widget: one
updater serves both stacks, and it takes the host activity's theme, so it lands
in the app's colours even though it is not the app's own toolkit.

Strings are localized in `UpdaterStrings.kt` (en/ru/uk) rather than in
`res/values-*`: shared sources have no `R` of their own, and reaching the app's
one by name would hide the strings from the resource shrinker that every project
runs in release.

## Server

Apache serves `/var/www/updates/` over plain HTTP on port 8081 — see
`PROTOCOL.md` for the file layout and why HTTP is enough here.

Publishing is not wired up yet: it becomes `18-Publish.sh` in each project,
copying the APK next to a rewritten `latest.json` and linking the new build.
