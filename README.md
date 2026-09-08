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
check interval are defaults in `UpdaterConfig`, so neither is written eight
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
