# Audit: about, backups, updater — 2026-09-10

Product contract: `about/README.md`, `backups/README.md`, `updater/README.md`,
`updater/PROTOCOL.md`, the three CHANGELOGs, and `23-ToUpdate.sh` (identical in every
project). Verified against the live wiring in `Steps` (`MainActivity`, `StepsService`,
`SettingsScreen`, `ZipBackup`, `AndroidManifest.xml`, `build.gradle.kts`). No earlier
audit of these modules exists.

Every source file of the three modules was read end to end. Findings marked *probed*
were checked by running something; the rest are read from the code.

## System model

**updater** — `Updater` (object, process-global state: `running`, `pendingInfo`,
`pendingConfig`, `permissionWatch`), `UpdaterPrefs` (`dev.updater` prefs: `enabled`,
`lastCheckAt`, `declinedVersionCode`), `UpdaterNet` (manifest GET, APK download with
streaming SHA-256), `UpdateInfo.parse` (manifest → chosen build for this device),
`UpdaterDialog` (confirm / message / installSources / Progress), `UpdaterInstall`
(permission check, Settings intent, FileProvider install intent).
Lifecycle of a check: `checkOnStart`/`checkNow` → `running` CAS → worker fetches manifest
→ main thread: `running=false`, `lastCheckAt` written, confirm dialog → `startDownload`
→ cache sweep → Progress dialog → worker downloads → main thread: verify SHA → install.
Untrusted data crosses in at the manifest body and the APK body (own GitHub release).

**backups** — `Backups` (object, `gate` semaphore), `BackupsPrefs` (`dev.backups` prefs:
`enabled`, `lastRunAt`, `lastAttemptAt`), `BackupsStore` (MediaStore: create pending,
publish, discard, prune). Lifecycle: `checkOnStart` takes the gate on the caller's thread,
worker decides `isDue`, `perform` = create pending → app's `write` → publish → `lastRunAt`
→ `prune`. `runNow` is the same `perform` under the same gate, blocking.

**about** — `About.show` builds the facts and hands `AboutDialog` two callbacks into
`Updater` (`peek` on open, `checkNow` on tap). No state of its own.

## Invariants the product depends on

- U1. At most one updater operation (check, dialog, download) is in flight per process.
- U2. A start-up check "counts" (`lastCheckAt` advances) only when the user could have
  seen its answer.
- U3. The file handed to the installer is the file whose SHA-256 was verified.
- U4. Nothing is downloaded or installed without a tap on Update in this session.
- B1. Rotation never deletes the copy just published.
- B2. Rotation deletes only files this module wrote (README: "Only the app's own files
  are touched").
- B3. A published archive is complete on disk.
- X1. The two modules answer the same question (attempted recently? dialog chrome?) by
  one rule (global rule: no duplication between modules).

---

### 1. P2 [FIXED fa2b79d] — A second download can start while one is in flight; the cache sweep deletes the file being written and the installer gets the wrong file

**Affected components:** `updater/android/src/dev/updater/Updater.kt` — `check`
(`running.set(false)` at line 126, before any dialog), `startDownload` (lines 191-197:
`dir.listFiles()?.forEach { it.delete() }` then `File(dir, "${appKey}-${versionCode}.apk")`),
`UpdaterDialog.Progress`; invariants U1, U3.

**Current behavior and reproduction:** `running` guards only the manifest fetch. It is
released on the main thread before the confirm dialog is shown, so the confirm dialog,
the Progress dialog and the download run with no guard at all. The dialogs are the only
thing stopping a second `checkNow`, and they die with the activity: `Steps/MainActivity`
declares no `configChanges`, so rotating the phone during a download destroys the
activity, the framework tears the Progress dialog down (leaked window), the worker keeps
downloading with `progress.cancelled == false`, and the user is left on a live screen with
no dialog. Open About → Check → Update: `startDownload` runs again, deletes every file in
`cache/updater` — including the one the first worker still holds open — and opens a new
`FileOutputStream` on the same path. On Linux the first worker keeps writing to the
unlinked inode, finishes, computes a SHA over its own bytes, which matches, and calls
`UpdaterInstall.install(activity, target)` — where `target` now names the second worker's
half-written file. The installer reports a parse error, or, if the second download has
finished by then, installs a file whose SHA was never compared with anything.

The rotation trigger is an empirical assumption about the user; the mechanics
(`running` released early, sweep deletes an open file, both workers share one path) are
code facts.

**Root cause:** U1 is enforced for the fetch only; the download owns no lock and the
cache sweep does not know a download can be in progress.

**Required outcome:** A second check or download cannot start while a download is in
flight, whatever happens to the activity that started it. The file passed to the
installer is exactly the file whose digest was verified (download into a unique
temporary name, verify, then rename to the final name; or hold the lock through the
install hand-off). A download that lost its dialog to activity recreation must either be
cancelled or keep its "in flight" status visible to the next check.

**Constraints:** `peek` stays lock-free (README: it spends no start-up turn). A manual
check during a download may answer with a message, but must not start a second one.

**Tests to add:** a unit test around a `Updater`-level lock: start download, attempt
`checkNow` → no second worker, no sweep. A test that a finished download installs from
the path it wrote and verified, even after another download was requested.

---

### 2. P2 [FIXED e5f3e7f] — Rotation can delete the copy it just published

**Affected components:** `backups/android/src/dev/backups/BackupsStore.kt` — `prune`
(lines 95-112: keeps the first `keepCount` rows of `DATE_ADDED DESC, _ID DESC`, dooms the
rest); `Backups.perform` (line 167-168: `lastRunAt = now` then `prune`); invariant B1.

**Current behavior and reproduction:** `prune` never learns which URI was just published.
It trusts that the new row sorts first. `DATE_ADDED` is the wall clock at insert time. If
the clock moved back since the last three copies were made (manual time change, a phone
that booted with a wrong date and corrected it later, a time-zone-free jump of the
system clock), the new copy sorts below the three older ones and is deleted in the same
`perform` that made it. `lastRunAt` was already set, so the day is marked done and no
retry happens. The user's newest data has no copy that day and nothing is logged as a
failure.

The clock moving backwards is an empirical assumption; that `prune` cannot tell the new
row from the old ones is a code fact.

**Root cause:** B1 is assumed from an ordering, not enforced by identity.

**Required outcome:** The row published by this `perform` is never in the doomed set,
independent of how the query sorts. The rest of rotation stays as it is.

**Constraints:** README: rotation runs after a successful copy and never before; a
failed backup must not cost an old one. Never throw from `prune`.

**Tests to add:** with a fake resolver holding three rows dated in the future and one just
published dated now, `prune` deletes one of the future rows and keeps the new one.

---

### 3. P2 [FIXED de81374] — Rotation matches more than the module writes: case-insensitive, any middle text

**Affected components:** `BackupsStore.prune` (line 86: pattern =
`likeLiteral(prefix) + "%" + likeLiteral(extension)`; line 91-93: `DISPLAY_NAME LIKE ?`);
`backups/README.md` "Only the app's own files are touched"; invariant B2. *Probed.*

**Current behavior and reproduction:** SQLite `LIKE` is case-insensitive for ASCII, and
`%` matches anything. Probed with sqlite3:
`'Steps-20260101-000000.ZIP' LIKE 'steps-%.zip'` → 1. So in `Documents/Steps` a user file
named `Steps-Notes.ZIP`, `steps-old.zip` or `STEPS-export.zip` is part of the pool and is
deleted on the fourth backup. The module writes exactly
`<prefix><yyyyMMdd-HHmmss><extension>` with an optional ` (n)` MediaStore suffix; the
query accepts a far wider set. This is a destructive operation on a public folder the
README promises to leave alone.

The user putting such a file there is an empirical assumption; the match width is a
code fact, probed.

**Root cause:** The pool is defined by a SQL pattern that cannot express "our exact
shape" — B2 is documented, not enforced.

**Required outcome:** Only names of the shape the module produces are ever deleted:
exact prefix (case-sensitive), a 15-character `yyyyMMdd-HHmmss` timestamp, an optional
` (n)`, exact extension. Keep the SQL as a coarse prefilter if convenient, then apply the
exact check in Kotlin on `DISPLAY_NAME` before adding to `doomed`.

**Constraints:** Copies made by earlier versions of every app under the same prefix must
still rotate (they have the same shape). `keepCount` counts only pool files.

**Tests to add:** a pure function `isPoolName(name, config)` with cases:
`steps-20260910-091200.zip` true, `steps-20260910-091200 (1).zip` true,
`Steps-20260910-091200.zip` false, `steps-old.zip` false, `steps-20260910-091200.zip.bak`
false.

---

### 4. P2 [FIXED d0c6300] — A start-up check whose answer lands on a dead activity is counted as done; the offer is lost for six hours

**Affected components:** `Updater.check` (line 145: `prefs.lastCheckAt = now` before
`UpdaterDialog.confirm`, which silently returns when `!alive(activity)` at line 180);
`checkOnStart` line 44-45; invariant U2. Verified on Steps: `MainActivity` has no
`configChanges`, so rotation or a theme/language change (Steps' own SettingsScreen
comment: "changing the theme or the language recreates the activity under an open
dialog") recreates it.

**Current behavior and reproduction:** Launch Steps, rotate within the fetch (~1 s on a
good network, up to 20 s on a bad one). The result is posted to the old activity:
`lastCheckAt` is set, `confirm` returns without showing anything. The new activity's
`checkOnStart` ran while `running` was true and was dropped, or runs later and sees
`elapsed < interval`. The user is not offered the update for `checkIntervalHours`. The
same loss happens if the dialog was shown and the activity was recreated under it: the
dialog dies with the window, nothing is remembered, and the interval has already started.

**Root cause:** "the check counts" is decided before "the user could see it".

**Required outcome:** `lastCheckAt` advances only when the confirm dialog (or the
up-to-date / no-build outcome) was actually delivered to a live activity. When the
activity is gone by the time the answer arrives, the next `checkOnStart` runs again
without waiting for the interval.

**Constraints:** README: no dialog more often than the interval, none for a declined
build. `peek` still remembers nothing.

**Tests to add:** a test double activity with `isDestroyed == true`: after `check`,
`lastCheckAt` is unchanged.

---

### 5. P3 [FIXED b29c184] — The Cancel pill on the permission dialog leaves a pending install armed

**Affected components:** `Updater.startDownload` (lines 183-189: `pendingInfo`,
`pendingConfig`, `watchForPermission`), `UpdaterDialog.installSources` (line 227: Cancel
only dismisses), `watchForPermission.onActivityResumed`; invariant U4.

**Current behavior and reproduction:** Update → "Permission needed" → Cancel. The
lifecycle watch stays registered and `pendingInfo` stays set. Any later resume of any
activity of the app consumes them: if by then the user has granted install permission on
their own (Settings, another app's prompt), the download starts with a Progress dialog on
whatever screen they returned to, with no Update tap in that session. If not, the watch
is dropped silently, which is the intended path. The dangling state also survives until
the next resume even when the Settings intent failed to launch.

**Root cause:** Cancel is not treated as withdrawing the consent that `pendingInfo`
records.

**Required outcome:** Cancel on the permission dialog clears `pendingInfo`,
`pendingConfig` and unregisters the watch. The watch is armed only after
`openInstallSourcesSettings` actually started an activity.

**Constraints:** CHANGELOG E-entry: the install still resumes by itself when the user
comes back from granting the permission.

**Tests to add:** after Cancel, a subsequent `onActivityResumed` with `canInstall == true`
starts no download.

---

### 6. P3 [FIXED 7f831c7] — Two modules answer "attempted recently?" by different rules on a clock that moved back

**Affected components:** `Updater.checkOnStart` line 44-45
(`elapsed < interval` with `elapsed` possibly negative); `Backups.isDue` line 95
(`!in 0 until RETRY_INTERVAL_MS`, explicitly treating a negative interval as "unusable
timestamp, go ahead"); invariant X1.

**Current behavior:** A `lastCheckAt` in the future makes `elapsed` negative, which is
`< interval`, so the start-up update check is silent until the wall clock passes the
stored value — hours or days after a manual time change. Backups handles the identical
situation deliberately and documents why. Same question, two answers.

**Root cause:** The guard was written once per module instead of once.

**Required outcome:** A negative elapsed time is treated as "no usable timestamp" in the
updater as it is in backups; ideally the expression lives in one shared place used by both
prefs classes.

**Constraints:** None beyond keeping the interval semantics for a sane clock.

**Tests to add:** `lastCheckAt = now + 1 day` → `checkOnStart` proceeds to `check`.

---

### 7. P3 [FIXED 072b1c8] — The archive is published before it is on disk

**Affected components:** `Backups.perform` (lines 150-158: `openOutputStream(uri)?.use`
→ `publish`); `UpdaterNet.download` line 88 (`output.fd.sync()` — the other module does it);
invariant B3, X1.

**Current behavior:** `close()` on the resolver stream returns with the bytes in the page
cache. `IS_PENDING = 0` is written immediately after. Power loss or a kernel panic in that
window leaves a truncated but published file that counts as one of the three copies and
pushes a complete one out on the next rotation. A process kill does not lose page cache,
so this needs power loss — an empirical assumption on frequency; the missing fsync is a
code fact and the updater already does it for the same reason.

**Root cause:** "published means complete" is not backed by a sync.

**Required outcome:** The file descriptor is synced before `IS_PENDING` is cleared
(`openFileDescriptor(uri, "w")` → `FileOutputStream(pfd.fileDescriptor)` → `fd.sync()`),
so a published archive is complete on disk.

**Constraints:** Empty-archive refusal and discard-on-failure stay as they are.

**Tests to add:** none practical; document the guarantee in the README bullet "written
pending and published only when finished".

---

### 8. P3 [FIXED dfec151] — A check worker that dies leaves the updater dead for the rest of the process

**Affected components:** `Updater.check` lines 122-172: `running` is only reset inside
`main.post`, with no `try/finally` on the worker thread.

**Current behavior:** Any throwable on the worker before `main.post` — an
`OutOfMemoryError` while reading the manifest, an unexpected exception from
`UpdateInfo.parse` — leaves `running == true` forever. Every later `checkOnStart` and
`checkNow` returns at line 111 without a word; the About button becomes the "silent
button" the README calls broken. Backups wraps its worker in `try/catch/finally` for
exactly this reason.

**Root cause:** The flag is released on the success path only.

**Required outcome:** `running` is released on every exit of the worker, and a manual
check that could not run at all says so.

**Tests to add:** a `fetch` stub that throws → a second `checkNow` still runs.

---

### 9. P3 [FIXED 418280c + 41d6257] — About duplicates the updater's dialog chrome

**Affected components:** `about/android/src/dev/about/AboutDialog.kt` (`alive`, `present`,
`Ui.dp`, `Ui.content`, `Ui.title`, `Ui.pill`, `Ui.buttons`) versus
`updater/android/src/dev/updater/UpdaterDialog.kt` (same names, same bodies, same
comments); invariant X1; global rule "no duplication between modules".

**Current behavior:** The card, the centring, the Force-Dark opt-out, the pill shape and
the button stack exist twice. The last two CHANGELOG entries ("pills keep their 2dp
outline", "violet buttons") had to be applied in both files; the next tweak will be
applied in one. `UpdaterColors` was already lifted for the same reason; the drawing code
was not.

**Root cause:** `UpdaterDialog` is `internal`, so About could only copy it.

**Required outcome:** One public dialog kit in the updater module (`UpdaterUi` or
similar: card content, title, body, pill with `inverted`, button stack, `present`) that
both `UpdaterDialog` and `AboutDialog` build on. About keeps only what is its own
(`row`, `linkRow`, `paragraph`, `scroller`, and `style` for the re-styled pill).

**Constraints:** No visual change; About's `style(pill, label, inverted)` must remain
callable after the pill is on screen.

**Tests to add:** none; the diff should delete more than it adds.

---

### 10. P3 [FIXED 76e71d6] — A `BackupsConfig` whose extension does not match its MIME type is accepted, and its copies never rotate

**Affected components:** `BackupsConfig.init` (lines 45-50: no check that `extension`
belongs to `mimeType`), `BackupsStore.createPending`, `prune`.

**Current behavior:** MediaStore forces the display name's extension to agree with
`MIME_TYPE` and appends the MIME's extension when it does not (`backup_x.db` with
`application/zip` becomes `backup_x.db.zip`). The file is published under that name, the
`LIKE … .db` pattern never matches it, and every such copy stays forever. No project
currently passes a mismatching pair (all use the defaults), so this is a latent
configuration trap, not a live bug.

**Root cause:** The config promises a name the store may not honour, and the rotation
pattern is built from the promise rather than from what `publish` returned.

**Required outcome:** Either `init` rejects an extension that does not map to
`mimeType`, or rotation is built from names actually returned by `publish`. Document
the rule in the README beside `keepCount`/`extension`.

**Tests to add:** `BackupsConfig(extension = ".db", mimeType = "application/zip")` throws.

---

### 11. P3 — The download bar promises to "spin" on a length-less response and does not

**Affected components:** `UpdaterNet.download` comment lines 68-69 ("0 means 'no bar,
just spin'"), `UpdaterDialog.Ui.progressBar` lines 143-146 (`isIndeterminate = false`,
by design), `Updater.startDownload` lines 209-216 (no post when `total <= 0`).

**Current behavior:** With no `Content-Length` and no `size` in the manifest the bar stays
at 0% and the label stays "Downloading…" for the whole transfer. Nothing spins. GitHub
release assets carry `Content-Length` and `23-ToUpdate.sh` always writes `size`, so
today this is unreachable — recorded so the next reader of the comment does not rely on
it. Accepted as is unless a host ever serves a manifest without `size`.

**Required outcome (if fixed):** Either the comment says "the bar stays empty", or the
label shows bytes downloaded when `total <= 0`.

---

### 12. P3 [FIXED 6392a83] — Start-up check reads preferences and PackageManager on the main thread

**Affected components:** `Updater.checkOnStart` lines 42-45 (`UpdaterPrefs` → 
`SharedPreferences` load, on the caller's thread in `onCreate`); `Updater.check` line
116 (`currentVersionCode` → `PackageManager.getPackageInfo`). Contrast
`Backups.checkOnStart` lines 56-60, which moves even the flag read off the caller's
thread and says why.

**Current behavior:** The first `getSharedPreferences("dev.updater")` in a process reads
and parses the file synchronously in the launcher activity's `onCreate` — small, but the
sibling module treats the same read as unacceptable there. Drift, not a measured
regression (empirical: the file is a few bytes).

**Required outcome:** `checkOnStart` returns at once and decides `enabled`/interval on
the worker, as backups does; or both modules document the same threshold for what may
run on the caller's thread.

**Constraints:** `isEnabled`/`setEnabled` stay synchronous for the settings switch.
