<p align="right">
  <a href="https://github.com/hdream0322/Anki-Android/releases/latest"><img alt="다운로드" src="https://img.shields.io/badge/%EB%8B%A4%EC%9A%B4%EB%A1%9C%EB%93%9C-F57C00?logo=github&logoColor=white&style=for-the-badge"/></a>
  <a href="https://github.com/ankidroid/Anki-Android"><img alt="원본 리포지토리" src="https://img.shields.io/badge/원본-ankidroid%2FAnki--Android-181717?logo=github&logoColor=white&style=for-the-badge"/></a>
  <a href="README.en.md"><img alt="English" src="https://img.shields.io/badge/README-English-2C7BE5?style=for-the-badge"/></a>
</p>

<!-- ============================================================ -->
<!-- Deurim Fork (마개조판) — 개인용 커스터마이즈 빌드           -->
<!-- ============================================================ -->

> **🛠 Deurim Fork (마개조판)**
> [AnkiDroid](https://github.com/ankidroid/Anki-Android)에 편의 기능을 더한 개인 개조판
>
> 공식 AnkiDroid와 한 폰에 같이 설치 가능 (앱 이름 `AnkiDroid.d`)
>
> 추가 기능 설정은 **설정 → 유용한 기능**에 모여 있음
>
> **📊 학습 기록**
> - **잔디밭 히트맵** — 덱 화면에서 날마다 공부한 양을 달력으로 확인. 날짜를 누르면 그날 기록, 연속 학습일과 앞으로 볼 카드 수도 표시
> - **홈 화면 위젯** — 고른 덱의 잔디밭을 홈 화면에서 바로 확인
>
> **📚 덱 목록**
> - **마지막 학습일** — 덱마다 "오늘 / N일 전" 표시
> - **정렬** — 이름 순 / 오래된 순 / 최근 순. 오래된 순은 밀린 카드가 있는 덱부터 보여 줌
> - **덱 색상** — 덱을 길게 눌러 파스텔 색 5가지로 칠하기. 접힌 덱엔 하위 덱 색이 점으로 표시
> - **태블릿 분할 화면** — 다 끝낸 덱을 골라도 빈 화면 대신 통계와 🎉 축하 메시지
>
> **✏️ 학습 화면**
> - **진행률 바** — 화면 위에 오늘 남은 카드 진행 상황 표시. 반짝임 효과와 속도 조절 가능
> - **효과음** — 정답·오답 소리, 덱 완료 시 박수. 앱을 켜면 무음으로 시작하고 볼륨 버튼을 누르면 소리 남
> - **AI 카드 채팅** — 보고 있는 카드에 대해 ChatGPT·Claude·Gemini에게 바로 질문. 본인 API 키 필요 (기기에만 암호화 저장)
> - **화이트보드 개선** — 깜빡임 해결, 덱끼리 펜 설정 공유, 카드를 확대·스크롤하면 필기도 같이 이동, 학습 취소 시 그림 복구
>
> **🔄 자동 업데이트**
> - 새 버전이 나오면 알려 주고 앱 안에서 바로 받아 설치 (속도·남은 시간 표시)
> - Wi-Fi에서만 받기, 특정 버전 건너뛰기, 설치 못 한 업데이트 다시 설치
> - 업데이트 후 처음 켜면 바뀐 점을 한국어로 안내
>
> **🎨 기타**
> - 눈이 편한 주황색 테마 (라이트·다크·블랙 모두)
> - 피드백은 이 개조판 전용 페이지로 연결

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
오픈소스 [Anki](https://apps.ankiweb.net/index.html) 간격 반복 플래시카드 시스템의 준공식 안드로이드 포팅. AnkiDroid 와 함께라면 무엇이든 외울 수 있습니다!

<img src="docs/graphics/logos/ankidroid_logo.png" align="right" width="40%" height="100%"></img>

### 주요 기능

<div style="display:flex;">

- 야간 모드
- 화이트보드
- 진행률 위젯
- 상세 통계
- AnkiWeb 와 동기화
- 답 직접 입력 (선택)
- TTS(텍스트 음성 변환) 연동
- 10,000 개가 넘는 미리 만들어진 덱
- 간격 반복 학습 (AI 최적화 [FSRS 알고리즘](https://github.com/open-spaced-repetition))
- 지원 콘텐츠: 텍스트, 이미지, 사운드, MathJax
- 사전 등 다른 앱에서 인텐트로 카드 추가

</div>

설치
---------
<div style="display:flex;">

<a href="https://play.google.com/store/apps/details?id=com.ichi2.anki&utm_source=global_co&utm_medium=prtnr&utm_content=Mar2515&utm_campaign=PartBadge&pcampaignid=MKT-Other-global-all-co-prtnr-py-PartBadge-Mar2515-1">
    <img alt="Google Play 에서 받기" height="80"
        src="docs/graphics/logos/google-badge.png" /></a>

<a href="https://f-droid.org/repository/browse/?fdid=com.ichi2.anki">
    <img alt="F-Droid 에서 받기" height="80"
        src="docs/graphics/logos/f-droid-badge.png"></a>

<a href="http://apps.obtainium.imranr.dev/redirect.html?r=obtainium://add/https://github.com/ankidroid/Anki-Android">
    <img alt="Obtainium 에서 받기" height="80"
        src="https://github.com/user-attachments/assets/713d71c5-3dec-4ec4-a3f2-8d28d025a9c6"/></a>

</div>

APK [검증](https://developer.android.com/studio/command-line/apksigner#usage-verify)에 사용되는 서명 인증서 지문:
```
SHA-256: 2071534f0f4b5e54ae952dd275d70da6e3459ee69909d2ab1b4843c4c5b21a45 
SHA-1: f24e06a3657b190a12671100402df32d7b9b3d36
```

위키
----
[위키](https://github.com/ankidroid/Anki-Android/wiki) 참고

도움말
----
사용 방법은 [사용자 매뉴얼](https://ankidroid.org/docs/manual.html) 과 위키를 확인하세요. 버그 신고나 프로젝트 멤버에게 연락하는 방법은 [도움말 페이지](https://ankidroid.org/docs/help.html) 를 참고하세요.

기여하기
----------
베타 테스트, 번역, 코드 기여 등으로 AnkiDroid 에 참여할 수 있습니다. 자세한 내용은 [기여 위키 페이지](https://github.com/ankidroid/Anki-Android/wiki/Contributing) 를 확인하세요.

함께하기
----------

<a href="https://discord.gg/qjzcRTx"><img src="docs/graphics/logos/discord_logo_color.svg" height="46px"/></a>
<a href="https://www.reddit.com/r/Anki"><img src="docs/graphics/logos/reddit_logo_color.png" height="50px"/></a>
<a href="https://www.facebook.com/AnkiDroid/"><img src="docs/graphics/logos/facebook_logo_color.png" height="50px"/></a>
<a href="https://x.com/ankidroid"><img src="docs/graphics/logos/twitter_logo.png" height="50px"/></a>
<a href="https://forums.ankiweb.net/"><img src="/docs/graphics/logos/anki_forums_logo.png" height="50px"/></a>

## 크레딧
<!--- Do not rename this section. AnkiDroid contains a deep link to the section
header - see https://github.com/ankidroid/Anki-Android/pull/11803 --->

### 코드 기여자

이 프로젝트를 지탱해 주는 멋진 코드 기여자들에게 감사드립니다.

<a href="https://github.com/ankidroid/Anki-Android/graphs/contributors"><img src="https://opencollective.com/ankidroid/contributors.svg?width=890&button=false" /></a>

### [스폰서](https://opencollective.com/ankidroid#sponsor)
<a href="https://opencollective.com/ankidroid#sponsor" target="_blank">
  <img alt="AnkiDroid Sponsors" src="https://opencollective.com/Ankidroid/sponsors.svg?width=890" />
</a>

### [후원자](https://opencollective.com/ankidroid#backer)

모든 후원자분들께 진심으로 감사드립니다 🙏
<a href="https://opencollective.com/Ankidroid#backers" target="_blank"><img width=110 src="https://opencollective.com/Ankidroid/backers/badge.svg?"></a>

<p>여러분의 너그러운 기부는 저희에게 큰 힘이 됩니다. 그 마음에 어떤 말로도 부족할 만큼 감사하며, 여러분의 후원이 우리의 목표를 이루고 의미 있는 변화를 만드는 원동력이 됩니다.</p>

<a href="https://opencollective.com/Ankidroid/donate" target="_blank">
  <img alt="AnkiDroid 후원하기" src="https://opencollective.com/Ankidroid/donate/button@2x.png?color=blue" width=200 />
</a>

### [번역가](https://crowdin.com/project/ankidroid/activity-stream)

2022년 7월 기준, 부분 또는 전부 99개 언어로 제공될 수 있게 도와주신 1,400 명의 번역가 여러분께 감사드립니다.

라이선스
-------
* [GPL-3.0 License](https://github.com/ankidroid/Anki-Android/blob/main/COPYING)
* 백엔드 일부에 대해 [AGPL-3.0 License](https://github.com/ankitects/anki/blob/main/LICENSE)
* AnkiDroid API 에 대해 [LGPL-3.0 License](https://github.com/ankidroid/Anki-Android/blob/main/api/COPYING.LESSER)
