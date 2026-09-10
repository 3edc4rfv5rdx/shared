# CHANGELOG
> Newest entries on top.
> N=new feature, E=error fix, F=fine-tune, R=refactor, I=infrastructure

## Unreleased
- N: An on/off flag of the module's own, Updater.isEnabled/setEnabled, so every app can offer "check at launch" as a settings switch instead of keeping that preference itself; it stops the start-up check alone, never the button
- N: Updater.peek asks the server and answers its caller instead of the user — a check that shows no dialog, remembers nothing and spends no start-up turn, for a host that draws the answer itself
- F: The dialogs are centred whatever the host's theme does with dialogs — on One UI a DeviceDefault app slid them to the bottom edge
- N: Updates come from the app's own GitHub release — /releases/latest/download/latest.json — so a device updates wherever it is and nothing of ours has to keep running
- N: One manifest names every ABI split and the device picks its own by Build.SUPPORTED_ABIS, so the arm64 phone and the armeabi-v7a TV box update from one file
- I: The local Apache server is gone, and with it its site config, its install notes and 18-ToUpdate.sh; 23-ToUpdate.sh writes the manifest after the release upload, describing the assets GitHub already has
- R: The server address is a default in UpdaterConfig, so an app names only its own key and moving the machine is one edit
- I: One CHANGELOG legend across every project here — N/E/F/R/I, newest on top, the type letter always followed by a colon

- F: Dialogs carry their own pill buttons — white on light, grey on dark — instead of the platform's, so the updater looks the same in every host app.
- E: The install resumes by itself when the user comes back from granting the install permission, instead of stopping there until the next check.
- N: Android update client: manifest check, confirmation dialog, download with SHA-256 verification, system install.
