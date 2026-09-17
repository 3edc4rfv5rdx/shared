# CHANGELOG
> Newest entries on top.
> N=new feature, E=error fix, F=fine-tune, R=refactor, I=infrastructure

## Unreleased
- E: An app kept alive in the background asks the server again when it is brought back an hour after the last answer, instead of never again in that process
- E: A build published minutes after a check is offered at once: the server is asked once per process instead of once per six hours, and the interval now holds back only the repeat of an offer the user has already answered
- R: checkIntervalHours is offerIntervalHours, which is what it has always governed
- E: A manual check no longer winds the start-up check's clock: the About button was putting the next six hours of silent checks to sleep every time it was pressed
- F: peek() hands back the build it found instead of a yes, and install() puts that build on without asking again — the caller's own button already carried the offer
- E: The download dialog no longer steps wider as the percentage grows a digit: every card in the module is one fixed width now, the screen less a margin and never past 400dp
- R: The card, the pills and the way a dialog goes on screen live in one UpdaterUi that ../about draws from too, instead of a copy in each module
- F: The start-up check decides whether to run on its own thread, so the launch activity opens no preferences file
- E: An update dialog lost to a screen going away is offered again on the next start instead of six hours later
- E: A second check cannot start while a download is running, and the installer is only ever given the file whose checksum was verified
- E: The start-up check runs again after the phone clock has been moved back, instead of waiting for it to catch up
- E: Cancel on the permission dialog forgets the install instead of starting it on the next screen the user comes back to
- E: A check or download whose thread died no longer leaves every later check silent or the progress dialog up for good
- F: The pills keep their 2dp outline, in the accent colour now, so a tinted button is the same shape as a filled one instead of a lighter blob on the card
- F: One palette for both modules in UpdaterColors — violet buttons and progress bar on the same white card, and ../about draws from the same file instead of its own copy of the colours
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
