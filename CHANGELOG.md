# CHANGELOG
> Newest entries on top.
> N=new feature, E=error fix, F=fine-tune, R=refactor, I=infrastructure

## Unreleased
- F: The update button names the version it found and installs it on one tap — the confirm dialog asked the same question a second time, and the tap before it had already answered
- R: The card, the pills and the way on screen come from ../updater's UpdaterUi; only the facts are drawn here
- F: The pills keep their 2dp outline, in the accent colour now, so a tinted button is the same shape as a filled one instead of a lighter blob on the card
- F: The buttons are violet — the update offer filled, the rest a pale tint — and the links match them; the card stays white, and the colours come from ../updater's UpdaterColors so both dialogs agree
- N: The update button says what it found: it opens as "Check for updates" on white, asks the server as the card comes up, and turns into an inverted "Update" only when a newer build is actually published
- F: The dialog is centred whatever the host's theme does with dialogs — on One UI a DeviceDefault app slid it to the bottom edge
- F: The facts scroll and the buttons stay put, so a tall card no longer pushes them off the screen; values are a size smaller
- N: Shared About dialog: one call puts up the app's name, version, build date, GitHub page and mailbox, with a check-for-updates button beside them
- I: The GitHub page an app publishes to is UpdaterConfig.repoUrl now, so the account and the repository name are still written in exactly one place
