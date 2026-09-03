# Update protocol

One static file per app on the server, fetched over plain HTTP on the LAN. There
is no backend: publishing is copying an APK and rewriting a JSON file, and
checking for an update is one GET.

## Layout

```
/var/www/updates/
  <appKey>/
    latest.json
    <appKey>-<versionName>-<versionCode>-<abi>.apk
    archive/
```

`appKey` is the folder name, not the package name — `reminderlists`, `steps`,
`bikelog`. The client builds its manifest URL as
`<baseUrl>/<appKey>/latest.json`.

## latest.json

```json
{
  "package": "com.reminderlists",
  "versionName": "0.3.20260828",
  "versionCode": 71,
  "abi": "arm64-v8a",
  "url": "reminderlists-0.3.20260828-71-arm64-v8a.apk",
  "size": 12841234,
  "sha256": "3f0c…64 hex chars…"
}
```

| field | why it is there |
| --- | --- |
| `package` | The client drops a manifest whose package is not its own: a wrong `appKey` would otherwise offer a second app rather than an update. |
| `versionName` | Shown in the dialog. Never compared. |
| `versionCode` | **The only field an update decision is made on**, and it is read out of the APK itself, not out of `build_number.txt` or `pubspec.yaml` — see below. Android itself refuses to install a lower one over a higher one. |
| `abi` | Documentation for a human reading the folder. The client does not filter on it: what is published is what the phones take. |
| `url` | Relative to the app folder, so the same file serves whatever address the server is reached at. An absolute `http(s)://` URL is honoured as given. |
| `size` | The progress bar's fallback when the response is chunked, and nothing else. Optional. |
| `sha256` | Lowercase hex of the APK. Verified after download; a mismatch deletes the file and reports an error. |

No changelog field: the notes live in each project's `CHANGELOG.md` and stay
there.

## versionCode comes from the APK, never from the build files

The client compares the manifest against what `PackageManager` reports for the
installed app, so the manifest has to carry the number that is actually inside
the APK. Those two part ways:

- **bikelog** built with `flutter build apk --split-per-abi` and no
  `versionCodeOverride`: the Flutter Gradle plugin rewrites each split as
  `abi * 1000 + build`, so `version: 0.9.260901+70` shipped as **2070** on arm64.
  A manifest saying `70` would have meant "older than installed" forever. It now
  carries the same override EasySend has.
- **EasySend** builds the same way but overrides the code back to
  `flutter.versionCode`, so its APK really does carry the `+N` from the pubspec.
- The Kotlin projects use AGP's own ABI splits, which leave `versionCode` alone.

Three different answers to the same question, so the publish script must not
guess: it reads the number from the APK it is about to publish, with

```sh
aapt2 dump badging <apk> | sed -n "s/.*versionCode='\([0-9]*\)'.*/\1/p"
```

That is correct for every project regardless of how its splits are configured,
and it stays correct if one of them changes.

Both Flutter projects now normalize the code, but the script must not depend on
that: it is one line in a build file away from being untrue again.

## What actually protects an install

The APK signature. All eight projects sign with the same key
(`~/.my-safe/key.properties`), and Android refuses a package signed with another
one, so a substituted APK cannot replace an installed app. The `sha256` above
catches a truncated or corrupted transfer, which a signature check alone would
only report at install time.
