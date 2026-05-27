<p align="right">
  <a href="https://github.com/hdream0322/Anki-Android/releases/latest"><img alt="Download" src="https://img.shields.io/badge/Download-F57C00?logo=github&logoColor=white&style=for-the-badge"/></a>
  <a href="https://github.com/ankidroid/Anki-Android"><img alt="Upstream Repository" src="https://img.shields.io/badge/Upstream-ankidroid%2FAnki--Android-181717?logo=github&logoColor=white&style=for-the-badge"/></a>
  <a href="README.md"><img alt="한국어" src="https://img.shields.io/badge/README-한국어-DA291C?style=for-the-badge"/></a>
</p>

<!-- ============================================================ -->
<!-- Deurim Fork — Personal customization build                   -->
<!-- ============================================================ -->

> **🛠 Deurim Fork**
> A personal customization build of [ankidroid/Anki-Android](https://github.com/ankidroid/Anki-Android).
> Can be installed alongside the official AnkiDroid on the same device.
>
> **Key changes**
> - **Review heatmap on the Study Options screen** — scoped to the selected deck + its sub-decks, with today's cell highlighted, month/weekday labels, tab-based daily statistics, upcoming-card forecast, and longest streak / studied-day count / totals.
> - **Review heatmap home-screen widget** — see the heatmap for a chosen deck right from the home screen (pick a deck when adding the widget; auto re-render on resize).
> - **Polished tablet split-screen** — when a finished deck is selected the right pane no longer goes blank: it shows card stats + a 🎉 congratulations message, with no flicker when switching decks.
> - **Last-studied date on the deck picker** — shows "Today / N days ago" per deck.
> - **Session progress bar at the top of the reviewer.**
> - **In-app auto-update** — checks GitHub Releases every 24 hours; if a new version is available, download it from the notification shade and install it through the system installer in one tap. Available immediately from Settings → General → Deurim and the About screen.
> - **New-version dialog** — on first launch after an update, shows human-written Korean release notes (`release-notes/v0.0.X.md` is used both for the GitHub Release body and the in-app dialog; works offline).
> - **Refreshed About screen** — Deurim fork version + versionCode + actual release date, plus shortcuts to "Install upstream AnkiDroid" and the GitHub release notes.
> - **App label `AnkiDroid.d`** — same label for both debug and release; debug uses a red icon + `.debug` package so it can be installed alongside the official AnkiDroid.
> - **Fork-only versionCode auto-increment** — `upstreamVersionCode + forkBuild`, so the fork keeps tracking upstream while every fork build is recognized as an OS-level upgrade.
> - **Whiteboard flicker fix** (multi-touch instance scoping + upstream HW-layer fix applied).

---

<p align="center">
<img alt="" src="docs/graphics/logos/banner_readme.png"/>
</p>

<a href="https://github.com/ankidroid/Anki-Android/releases"><img src="https://img.shields.io/github/v/release/ankidroid/Anki-Android" alt="release"/></a>
<a href="https://github.com/ankidroid/Anki-Android/actions"><img src="https://img.shields.io/github/checks-status/ankidroid/Anki-Android/main?label=build" alt="build"/></a>
<a href="https://opencollective.com/ankidroid"><img src="https://img.shields.io/opencollective/all/ankidroid" alt="Open Collective backers and sponsors"/></a>
<a href="https://github.com/ankidroid/Anki-Android/issues"><img src="https://img.shields.io/github/commit-activity/m/ankidroid/Anki-Android" alt="commit-activity"/></a>
<a href="https://github.com/ankidroid/Anki-Android/network/members"><img src="https://img.shields.io/github/forks/ankidroid/Anki-Android" alt="forks"/></a>
<a href="https://github.com/ankidroid/Anki-Android/stargazers"><img src="https://img.shields.io/github/stars/ankidroid/Anki-Android" alt="stars"/></a>
<a href="https://crowdin.com/project/ankidroid"><img src="https://badges.crowdin.net/ankidroid/localized.svg"></img></a>
<a href="https://github.com/ankidroid/Anki-Android/graphs/contributors"><img src="https://img.shields.io/github/contributors/ankidroid/Anki-Android" alt="contributors"/></a>
<a href="https://discord.gg/qjzcRTx"><img src="https://img.shields.io/discord/368267295601983490"></img></a>
<a href="https://github.com/ankidroid/Anki-Android/blob/main/COPYING"><img src="https://img.shields.io/github/license/ankidroid/Anki-Android" alt="license"/></a>

# AnkiDroid
A semi-official port of the open source [Anki](https://apps.ankiweb.net/index.html) spaced repetition flashcard system to Android. Memorize anything with AnkiDroid!

<img src="docs/graphics/logos/ankidroid_logo.png" align="right" width="40%" height="100%"></img>

### Features

<div style="display:flex;">
 
- Night mode
- Whiteboard 
- Progress widget
- Detailed statistics
- Syncing with AnkiWeb
- Write answers (optional)
- Text-to-speech integration
- More than 10,000 premade decks
- Spaced repetition (AI-optimized [FSRS algorithm](https://github.com/open-spaced-repetition))
- Supported contents: text, images, sounds, MathJax
- Add cards by intent from other applications like dictionaries

</div>

Install
---------
<div style="display:flex;">

<a href="https://play.google.com/store/apps/details?id=com.ichi2.anki&utm_source=global_co&utm_medium=prtnr&utm_content=Mar2515&utm_campaign=PartBadge&pcampaignid=MKT-Other-global-all-co-prtnr-py-PartBadge-Mar2515-1">
    <img alt="Get it on Google Play" height="80"
        src="docs/graphics/logos/google-badge.png" /></a>

<a href="https://f-droid.org/repository/browse/?fdid=com.ichi2.anki">
    <img alt="Get it on F-Droid" height="80"
        src="docs/graphics/logos/f-droid-badge.png"></a>

<a href="http://apps.obtainium.imranr.dev/redirect.html?r=obtainium://add/https://github.com/ankidroid/Anki-Android">
    <img alt="Get it on Obtainium" height="80"
        src="https://github.com/user-attachments/assets/713d71c5-3dec-4ec4-a3f2-8d28d025a9c6"/></a>

</div>

Signing certificate fingerprint to [verify](https://developer.android.com/studio/command-line/apksigner#usage-verify) the APK:
```
SHA-256: 2071534f0f4b5e54ae952dd275d70da6e3459ee69909d2ab1b4843c4c5b21a45 
SHA-1: f24e06a3657b190a12671100402df32d7b9b3d36
```

Wiki
----
View [Wiki](https://github.com/ankidroid/Anki-Android/wiki)

Help
----
Check the [user manual](https://ankidroid.org/docs/manual.html) and the wiki for usage instructions. See the [help page](https://ankidroid.org/docs/help.html) 
for how to submit a bug report or contact a project member, etc.

Contribute
----------
You can contribute to AnkiDroid by beta testing, translating, or submitting code. 
See the [contribution wiki page](https://github.com/ankidroid/Anki-Android/wiki/Contributing) for more info.

Join Us On
----------

<a href="https://discord.gg/qjzcRTx"><img src="docs/graphics/logos/discord_logo_color.svg" height="46px"/></a>
<a href="https://www.reddit.com/r/Anki"><img src="docs/graphics/logos/reddit_logo_color.png" height="50px"/></a>
<a href="https://www.facebook.com/AnkiDroid/"><img src="docs/graphics/logos/facebook_logo_color.png" height="50px"/></a>
<a href="https://x.com/ankidroid"><img src="docs/graphics/logos/twitter_logo.png" height="50px"/></a>
<a href="https://forums.ankiweb.net/"><img src="/docs/graphics/logos/anki_forums_logo.png" height="50px"/></a>

## Credits
<!--- Do not rename this section. AnkiDroid contains a deep link to the section
header - see https://github.com/ankidroid/Anki-Android/pull/11803 --->

### Code Contributors

Thanks to these awesome code contributors who keep this project going

<a href="https://github.com/ankidroid/Anki-Android/graphs/contributors"><img src="https://opencollective.com/ankidroid/contributors.svg?width=890&button=false" /></a>

### [Sponsors](https://opencollective.com/ankidroid#sponsor)
<a href="https://opencollective.com/ankidroid#sponsor" target="_blank">
  <img alt="AnkiDroid Sponsors" src="https://opencollective.com/Ankidroid/sponsors.svg?width=890" />
</a>

### [Backers](https://opencollective.com/ankidroid#backer)

A big thank you to each of our backers 🙏
<a href="https://opencollective.com/Ankidroid#backers" target="_blank"><img width=110 src="https://opencollective.com/Ankidroid/backers/badge.svg?"></a>

<p>Your generous donations mean the world to us, and we can't express our gratitude enough. Your support fuels our mission and helps us make a real difference</p>

<a href="https://opencollective.com/Ankidroid/donate" target="_blank">
  <img alt="Donate to AnkiDroid" src="https://opencollective.com/Ankidroid/donate/button@2x.png?color=blue" width=200 />
</a>

### [Translators](https://crowdin.com/project/ankidroid/activity-stream)

Thanks to our 1400 translators, for allowing us to be available, partially or totally, in 99 languages as of July 2022.

License
-------
* [GPL-3.0 License](https://github.com/ankidroid/Anki-Android/blob/main/COPYING)
* [AGPL-3.0 License](https://github.com/ankitects/anki/blob/main/LICENSE) for some part of the back-end
* [LGPL-3.0 License](https://github.com/ankidroid/Anki-Android/blob/main/api/COPYING.LESSER) for the AnkiDroid API
