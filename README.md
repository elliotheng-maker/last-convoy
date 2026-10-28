# Last Convoy (Android)

Download the latest APK: https://github.com/elliotheng-maker/last-convoy/releases/latest/download/last-convoy.apk

- `updates/` — hot game updates (game.html + version.json). Phones check this on launch and prompt to update.
- `python3 publish.py "notes"` copies the latest mobile build in, bumps the version; commit & push to ship.
- Changes under `app/` trigger a new APK build (GitHub Actions → Releases).
