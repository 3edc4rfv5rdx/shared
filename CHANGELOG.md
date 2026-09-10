# CHANGELOG
> Newest entries on top.
> N=new feature, E=error fix, F=fine-tune, R=refactor, I=infrastructure

## Unreleased
- E: Rotation deletes only files named exactly the way the module names them, so a user's own archive in the same folder is never touched
- N: Shared daily-backup module: one copy a day into Documents/<app>, three kept, manual and automatic copies in one pool
