<!-- ============================================================ -->
<!-- Deurim Fork (마개조판) — 개인용 커스터마이즈 빌드           -->
<!-- ============================================================ -->

> **🛠 Deurim Fork (마개조판)**
> [ankidroid/Anki-Android](https://github.com/ankidroid/Anki-Android)을 개인용 개조 빌드
> 공식 AnkiDroid와 같은 기기에 함께 설치 가능
>
> **주요 변경점**
> - **덱 개요(Study Options)에 리뷰 히트맵 추가** — 선택한 덱 + 하위 덱 범위, 오늘 칸 강조, 월/요일 라벨, 탭으로 날짜별 통계, 예정 카드 forecast, 최장 연속 학습일 / 학습일수 / 합계 표시
> - **리뷰 히트맵 홈 화면 위젯** — 선택한 덱의 잔디밭을 홈에서 바로 확인 (위젯 추가 시 덱 선택, 크기 조절 시 자동 재렌더)
> - **태블릿 분할 화면 마무리** — 학습 완료된 덱을 골라도 우측 패널이 비지 않고 카드 통계 + 🎉 축하 메시지를 함께 표시, 덱 전환 시 깜빡임 제거
> - **덱피커 마지막 학습일 표시** — 덱별로 "오늘 / N일 전" 표시
> - **리뷰어 상단에 세션 진행률 바** 추가
> - **인앱 자동 업데이트** — 24시간마다 GitHub Releases를 확인해 새 버전이 있으면 알림 표시줄에서 다운로드 → 시스템 인스톨러로 한 번에 설치. 설정 → 일반 → Deurim 과 About 화면에서 즉시 확인 가능
> - **새 버전 안내 다이얼로그** — 업데이트 후 첫 실행 시 사람이 쓴 한국어 릴리스 노트를 보여 줌 (`release-notes/v0.0.X.md` 가 GitHub Release body 와 인앱 안내에 동시에 사용됨, 오프라인에서도 동작)
> - **About 화면 정비** — Deurim fork 버전 + versionCode + 실제 릴리스 날짜 표기, "원본 AnkiDroid 설치" 와 GitHub 릴리스 노트 바로가기 추가
> - **앱 라벨 `AnkiDroid.d`** — 디버그/릴리스 모두 같은 라벨, debug 는 빨간 아이콘 + `.debug` 패키지로 공식 AnkiDroid 와 함께 설치 가능
> - **fork 전용 versionCode 자동 증분** — `upstreamVersionCode + forkBuild` 식으로 upstream 을 따라가면서도 fork 빌드가 OS-level 업그레이드로 인식됨
> - **화이트보드 플리커 수정** (멀티터치 인스턴스 스코핑 + upstream HW-layer 픽스 적용)

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
