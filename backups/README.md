# backups

One daily backup for every project in `../` that holds a database. The app says
what goes inside an archive; this module decides when a copy is made, what it is
called, where it lands and how many are kept — three, the newest three.

Like `../updater`, there is no library module and no Flutter plugin: the Kotlin
sources are compiled straight into each app through `sourceSets`. One copy of
the code, one policy, no AAR to publish. It adds no dependency at all — the
whole thing is `MediaStore`, `SharedPreferences` and a `Thread`.

## What it costs a project

### 1. `app/build.gradle.kts`

```kotlin
android {
    sourceSets {
        getByName("main") {
            // Kotlin projects: $rootDir/../backups   Flutter: $rootDir/../../backups
            kotlin.directories.add("$rootDir/../backups/android/src")
        }
    }
}
```

No `res` set and no manifest entries: the module has no resources, no strings
and no permissions. Writing into `Documents/` through MediaStore has needed none
since Android 10, which is also the module's floor — `RELATIVE_PATH` and
`IS_PENDING` are API 29. Every project here is above it.

### 2. One config, next to the app's own backup code

```kotlin
fun backupsConfig(context: Context): BackupsConfig {
    val appContext = context.applicationContext
    return BackupsConfig(
        dirName = BACKUP_DIR_NAME,   // Documents/ReminderLists
        filePrefix = "backup_",      // backup_20260904-091200.zip
        write = { out -> writeArchive(appContext, out) },
    )
}
```

`keepCount` defaults to three and `extension` to `.zip`; an app that wants
other values passes them. `extension` and `mimeType` have to agree the way
MediaStore sees it (`.zip` is `application/zip`): a mismatch is refused when
the config is built, because MediaStore would otherwise rename the file on
publish and rotation would never recognise it.

`write` is the app's entire share of the work: fill this stream with one
complete archive, throw if you cannot. It is called on a background thread and
never twice at once.

### 3. One call at start-up

```kotlin
Backups.checkOnStart(this, BackupManager.backupsConfig(this))
```

In the launcher activity's `onCreate`, and again wherever a service records data
while the app is closed — Steps and BikeTracker write step counts and track
points with no UI open, and a start-up-only check would leave those days without
a copy. The call reads nothing on the calling thread — it hands the decision to
one short-lived worker and returns — so putting it in both places costs nothing.

`onCreate` is the usual second place. An app that gates its database against a
recording in progress wants the end of that recording instead: BikeTracker calls
it where a ride is finished and saved, because a copy started as the ride begins
would be holding the database at the moment the ride asks for it.

Not from a `BroadcastReceiver`: a receiver has about ten seconds before the
process may be killed, and a killed process in the middle of a write is exactly
the case the module has to clean up afterwards.

A Flutter app calls it from Dart instead, over its own method channel: the
databases live where `path_provider` put them, so Dart is the only side that can
say what goes into the archive. bikelog does this once its paths are
initialised.

## Behaviour

- **Once a calendar day**, in the phone's own time zone, not every 24 hours.
- **A manual backup counts as the day's copy.** The "Create backup" button calls
  `Backups.runNow(context, config)`, which is the same path — so manual and
  automatic copies are one pool, share one rotation, and the last three of them
  are what is on the phone.
- **A failed attempt waits an hour** before the next one, so a phone with no
  free space is not asked to build an archive on every launch.
- **The archive is written pending, synced to disk and only then published**, so a
  half-written file is never visible and never counts as a copy. An empty one is
  refused outright.
- **Rotation runs after a successful copy, never before, and never on that copy.** A backup that failed
  must not have cost the oldest one that worked.
- **Only the app's own files are touched.** The pool is exactly what the module
  writes — `filePrefix`, a `yyyyMMdd-HHmmss` timestamp, the ` (n)` MediaStore adds
  to a taken name, `extension` — matched case-sensitively in code, not by a SQL
  pattern. `backup_20260904-091200.zip` rotates; `Backup_notes.ZIP` or
  `backup_old.zip` in the same folder is never counted and never deleted.

## Settings

The module keeps its own on/off flag and exposes the three things a settings
screen needs — `Backups.isEnabled(context)`, `Backups.setEnabled(context, on)`
and `Backups.lastRunAt(context)`. The switch itself belongs to the app: every
project here draws its settings in Compose, in its own style, with its own
localized string.

Default is on. Off stops the daily copy; the button still works.

## Where the copies go

`Documents/<dirName>/` through MediaStore, next to the manual backups the apps
already write there — a copy that is deleted along with the app it was meant to
save is not a backup, and this way the user can pull a file off the phone
without the app's help.

One case the module cannot fix: after an uninstall and reinstall the old copies
belong to the previous install, and MediaStore refuses to let the new one delete
them. They stay on the phone, outside the rotation, and are logged rather than
forced — a consent dialog has no business appearing behind a silent backup.

## Which projects have a database

| project | archive | state |
| --- | --- | --- |
| ReminderLists | db + photos + sounds + logs | wired in |
| BikeTracker | db (rides, track points) | wired in |
| Steps | db (day history) | wired in |
| myplayer | — | nothing to archive: its settings and book progress ride Android Auto Backup |
| bikelog | Flutter, sqflite + photos | wired in: Dart hands the paths over a channel and asks for the check itself |
