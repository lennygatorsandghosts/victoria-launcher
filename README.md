# Vicky+

> **Vicky+ is a personal fork of [Victoria Launcher](https://github.com/adelmonte/victoria-launcher)**
> by adelmonte, who did all the real work. It installs beside the original
> (`org.lemmyorleans.vickyplus`) and tracks upstream closely.
>
> **What the fork adds** (current as of `0.67.0-vicky.4`)
> - **A badge on shortcut icons** — a bookmark carries a small copy of the icon of the app
>   that opens it, so two browsers' bookmarks stop looking alike. Plain browser tiles are
>   masked to the chosen icon shape. Settings → "Show app badge on shortcuts".
> - **Clearer bookmark removal** — "Remove from home screen" and "Delete bookmark" say which
>   is which, and deleting asks first.
> - **A floating button**, bottom-right, with an action per gesture (tap, three swipes,
>   long-press to edit), and a slim web-search bar docked above the keyboard that opens a URL
>   template of your choosing — for example your own SearXNG.
> - **Niagara-ish list** — a wave A-Z scrubber with a bright strip, the private space as its
>   own section at the bottom, a date and battery header, recently-installed suggestions, and
>   a one-tap preset for the whole look.
> - **A stricter settings import** — the file is size-capped, type-checked and limited to
>   known settings before anything is written. Exports from stock Victoria still import.
>
> **Already upstream, so no longer a difference:** pinned shortcuts as list entries (#48) and
> Android 15 private-space support (#49) were both merged into Victoria itself.
>
> Everything here is offered upstream when it is general enough to be worth someone else's
> maintenance. The branches named `feat/…` and `fix/…` carry no Vicky+ naming for that reason.
>
> Like the original it asks for no network permission: the search button only hands an
> address to your browser.
>
> **Verifying a download.** Releases here are signed on the maintainer's machine. The
> signing certificate's SHA-256 fingerprint is
> `C9:79:FE:45:9A:6D:C6:33:0F:BD:A6:6F:6F:29:DA:6B:6C:B3:08:09:4F:7D:3E:61:40:1E:C1:01:CA:89:17:57`
> — check it with `apksigner verify --print-certs` or AppVerifier. It is **not** the
> original's key, so this is a separate app rather than an update to Victoria.
>
> Everything below is the original project's README.

---

# Victoria Launcher

An open source alternative to [Niagara Launcher](https://niagaralauncher.app) — a
minimal, list-based Android home screen.

![Victoria Launcher](docs/banner2.png)

## Install

[<img src="https://fdroid.gitlab.io/artwork/badge/get-it-on.png" alt="Get it on F-Droid" height="70">](https://f-droid.org/packages/dev.victorialauncher/)

Or grab the APK from [Releases](../../releases). Both carry the same signature,
so you can move between them without uninstalling.

Once it is installed, pick Victoria Launcher under
**Settings → Apps → Default apps → Home app**.

Requires Android 8.0 (API 26) or newer.

## Getting started

The home screen starts almost empty on purpose — everything is added by you.

- **Long-press the wallpaper** for the main menu: add favorites, add a widget,
  edit the layout, or open settings.
- **Swipe in from either edge** of the screen for the full A-Z app list, and
  slide your thumb along the letters to jump straight to one. Tap the edge and
  let go to just open the list.
- **Long-press any app** — on the home screen or in the A-Z list — to rename it,
  change its icon, hide it, or move it into a folder.
- **Edit layout** turns on drag handles for reordering and steppers for every
  gap, height and margin, including how far the A-Z strip reaches.

Worth knowing about in **Settings**:

- Alignment and icon side, set separately for favorites and the A-Z list
- A search box in the app list, at the top or the bottom
- Sorting each letter by how often you open its apps
- Swipe up from the home screen to open the app list
- Swipe left or right below your favorites to launch a chosen app
- Now Playing controls, which need notification access
- Double-tap the A-Z strip to lock the screen, which needs an accessibility
  service — if that toggle is greyed out, open App info and allow restricted
  settings first (Android blocks it for apps installed outside a store)

Work profiles are picked up automatically. A private space needs Android 15 or
later and this app set as your default home app; while it is unlocked its apps
are listed like any others, and a "Private space" padlock entry locks and
unlocks it.

## Changelog

Per-release notes live in
[fastlane/metadata/android/en-US/changelogs](fastlane/metadata/android/en-US/changelogs),
and are shown on each [GitHub release](../../releases) and on the app's F-Droid
page.

## Build

You need JDK 17 and an Android SDK with platform 35.

```sh
./gradlew assembleDebug
```

Translations are very welcome — see [TRANSLATING.md](docs/TRANSLATING.md).

See [CONTRIBUTING.md](docs/CONTRIBUTING.md) for the full build and contribution
notes, and [ARCHITECTURE.md](docs/ARCHITECTURE.md) for how the code fits together.

## License

[GPL-3.0-or-later](LICENSE)
