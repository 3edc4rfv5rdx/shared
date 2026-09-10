# CHANGELOG
> Newest entries on top.
> N=new feature, E=error fix, F=fine-tune, R=refactor, I=infrastructure

## Unreleased
- E: An extension that does not match the MIME type is refused when the config is built, instead of producing copies that never rotate
- E: A copy is synced to disk before it is published, so a power loss right after cannot leave a truncated file among the kept ones
- E: The copy just made is never deleted by the rotation that follows it, whatever the phone clock did in between
- E: Rotation deletes only files named exactly the way the module names them, so a user's own archive in the same folder is never touched
- N: Shared daily-backup module: one copy a day into Documents/<app>, three kept, manual and automatic copies in one pool
