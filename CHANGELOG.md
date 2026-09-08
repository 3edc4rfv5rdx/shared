# CHANGELOG
> Newest entries on top.
> N=new feature, E=error fix, F=fine-tune, R=refactor, I=infrastructure

## Unreleased
- F: The dialog is centred whatever the host's theme does with dialogs — on One UI a DeviceDefault app slid it to the bottom edge
- F: The facts scroll and the buttons stay put, so a tall card no longer pushes them off the screen; values are a size smaller
- N: Shared About dialog: one call puts up the app's name, version, build date, GitHub page and mailbox, with a check-for-updates button beside them
- I: The GitHub page an app publishes to is UpdaterConfig.repoUrl now, so the account and the repository name are still written in exactly one place
