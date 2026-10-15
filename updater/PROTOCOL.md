# Update protocol

One file per app, published as an asset of that app's newest GitHub release.
There is no backend and no server of ours: publishing is uploading the manifest
beside the APKs already in the release, and checking for an update is one GET.

## Where it lives

GitHub serves the newest published release of a repository under a fixed path,
so an app is built once with an address that never changes again:

```
https://github.com/<user>/<repo>/releases/latest/download/latest.json
https://github.com/<user>/<repo>/releases/latest/download/<app>-<version>-<abi>.apk
```

`<user>` and `<repo>` are all `UpdaterConfig` carries — no tag, no version, no
host of ours. The APK urls inside the manifest are written relative to it, which
is exactly where the release keeps its assets, so the same manifest works from
any host it is copied to.

A prerelease is not what `/releases/latest` serves, and the publish script
refuses to write a manifest onto one rather than publish something nobody will
read.

## latest.json

```json
{
  "package": "a.a.easysend",
  "versionName": "0.7.20260904",
  "builds": {
    "arm64-v8a":   { "versionCode": 146, "url": "easysend-0.7.20260904-146-arm64-v8a.apk",   "size": 18868796, "sha256": "b53f…" },
    "armeabi-v7a": { "versionCode": 146, "url": "easysend-0.7.20260904-146-armeabi-v7a.apk", "size": 16375718, "sha256": "d99e…" },
    "universal":   { "versionCode": 146, "url": "easysend-0.7.20260904-146-universal.apk",   "size": 54022079, "sha256": "556d…" }
  }
}
```

| field | why it is there |
| --- | --- |
| `package` | The client drops a manifest whose package is not its own: a wrong repository would otherwise offer a second app rather than an update. Above the map, because every split of a build carries the same one. |
| `versionName` | Shown in the dialog. Never compared. Above the map for the same reason. |
| `builds` | The APK assets of this release, keyed by ABI. What the client chooses from — see below. |
| `builds[abi].versionCode` | **The only field an update decision is made on**, and it is read out of that split's APK, not out of `build_number.txt` or `pubspec.yaml` — see below. Android itself refuses to install a lower one over a higher one. |
| `builds[abi].url` | The asset name, read beside the manifest. An absolute `http(s)://` URL is honoured as given, for a manifest that points somewhere else. |
| `builds[abi].size` | The progress bar's fallback when the response is chunked, and nothing else. Optional. |
| `builds[abi].sha256` | Lowercase hex of that APK. Verified after download; a mismatch deletes the file and reports an error. |

A `versionCode` above the map is still read as the default for an entry that
omits its own, and a manifest with no `builds` at all is read as one flat build —
the shape this file had before the splits were published. Neither is written any
more.

No changelog field: the notes live in each project's `CHANGELOG.md` and in the
release body, and stay there.

## Which build a device takes

The client walks `Build.SUPPORTED_ABIS` — the device's own list, in the device's
own order of preference — and takes the first ABI `builds` has an entry for. A
64-bit phone lists `arm64-v8a` before the `armeabi-v7a` it can also run, so it
takes the arm64 split; an armeabi-v7a TV box has only the one entry to match.
Nothing matched means `universal`, and no `universal` either means there is
nothing here for this device: silent on a start-up check, "no build for this
device" on a manual one, rather than a download that ends at
`INSTALL_FAILED_NO_MATCHING_ABIS`.

GitHub is not asked who is calling and holds no per-device state: every device
reads the same file and answers the question itself.

An update does not have to match the ABI of what is installed. Android compares
the package, the signature and the version code, and nothing else — a universal
APK installs over an arm64 one and the other way round. The only hard rule is
that the device must be able to run the APK's own native libraries.

## versionCode comes from the APK, never from the build files

The client compares the manifest against what `PackageManager` reports for the
installed app, so each entry has to carry the number that is actually inside its
APK. Those two part ways, and not only per project but per split:

- A Flutter build under `flutter build apk --split-per-abi` with no
  `versionCodeOverride` has each split rewritten as `abi * 1000 + build`, so one
  build is **1148** on armeabi-v7a and **2148** on arm64. One number above the map
  would have been wrong for every device but one, which is why the code sits
  inside each entry.
- **EasySend** and **bikelog** override the code back to `flutter.versionCode`,
  so their APKs really do carry the `+N` from the pubspec.
- The Kotlin projects use AGP's own ABI splits, which leave `versionCode` alone.

Three different answers to the same question, so the publish script must not
guess: it reads the number from each APK with

```sh
aapt2 dump badging <apk> | sed -n "s/.*versionCode='\([0-9]*\)'.*/\1/p"
```

That is correct for every project regardless of how its splits are configured,
and it stays correct if one of them changes.

## The manifest describes the release, and is written from it

`23-ToUpdate.sh` asks GitHub what the newest release carries and writes an entry
for every APK asset among those files — never for a split the release does not
have. The checksums and version codes come from the local copies the upload was
made from, so the step is run right after `22-RelUpload.sh`, while that build is
still in the output folder. A release built on another machine, or one whose
output folder has been cleaned, is reported rather than described from a guess.

## What actually protects an install

The APK signature. All the projects sign with the same key
(`~/.my-safe/key.properties`), and Android refuses a package signed with another
one, so a substituted APK cannot replace an installed app. The `sha256` above
catches a truncated or corrupted transfer, which a signature check alone would
only report at install time.

The transfer itself is HTTPS to github.com, so nothing on the way can hand the
phone a different file to begin with.
