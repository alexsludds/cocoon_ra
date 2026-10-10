# Cocoon RA

A tiny companion app for [Cocoon](https://github.com/inssekt/CocoonFE) on the AYN Thor's second screen. It shows RetroAchievements for the game you're playing right now: your completion status, each achievement, and the comments on retroachievements.org when you tap one.

Cocoon itself is closed-source, so this can't be a button inside Now Playing. Instead it's a separate app styled to match Cocoon's look, pinned to the dock and set to open on the bottom screen.

## Install
1. Download `cocoon-ra.apk` from [Releases → latest](../../releases/latest) on the Thor and install it.
2. Open **Achievements** and enter your RetroAchievements username and **Web API Key**. The key is on retroachievements.org → Settings → Authentication. This is the same key Cocoon uses.

## Hook it into Cocoon
- **Pin it to the dock:** on Home, move down to the dock, pick an empty slot, and choose *Achievements*.
- **Open it on the bottom screen:** in the App Drawer, highlight *Achievements*, press **Y**, choose **Edit App**, and set its display to the bottom screen.

## How it works
- The current game is your RetroAchievements "last game", which your emulator updates as you play. The list refreshes every 30 s, so new unlocks show up while you play on the top screen.
- Filter by All / Locked / Unlocked. A gold border means a hardcore unlock.
- Tap an achievement to see its comments. *Open on RetroAchievements* opens the full page.
- It follows the system's light or dark mode. The colours are at the top of `MainActivity.kt`, so you can match your Cocoon theme.

Every push to `main` builds a new APK through GitHub Actions. All builds use the same signing key, so updates install over the old version.
