# 유튜브 댓글 에이전트 대시보드

> **TRACK 2 · 마케팅 AI 에이전트 개발** 과제 구현
>
> 지정된 영상의 맥락에 맞는 유튜브 댓글을 달되, 그 댓글을 본 사람이 **'운명원'**(연애 사주 잘 봐주는 곳)에서
> 연애 사주를 보고 싶어지도록 유도하는 **로컬 대시보드**입니다.

영상을 **불러오고 → 고르고 → 버튼 한 번**에 크롬 프로필이 열리며 **직접 타이핑**으로 댓글이 달립니다.

---

## 화면 — 세 칸

| 칸 | 동작 |
|---|---|
| **① 영상 불러오기** | 버튼 한 번에 최신 영상 목록(썸네일·제목·채널·날짜). '뜨기 직전 영상' 탐색·링크 직접 입력 지원 |
| **② 영상 선택** | 고르면 AI가 「영상 속 고민 한 줄」 + 「댓글 초안」 생성 (40~120자, 영상 속 구체 장면 언급) |
| **③ 댓글 달기** | 지정 크롬 프로필이 열리며 한 글자씩 타이핑 → 게시 → 내 댓글 확인. 진행 단계·실패 사유·스크린샷 표시 |

하단 **기록 패널**: 어느 영상에 · 어느 프로필로 · 무슨 댓글을 · 성공/실패를 시간순으로.

---

## 요구사항 → 구현 매핑

| 요구사항 | 구현 |
|---|---|
| ① 영상 불러오기 (썸네일·제목·채널·날짜) | `YoutubeService.listLatest` + `/api/videos` |
| ② 고민 한 줄 + 댓글 초안 (자막/제목·설명 + 인기댓글 기반) | `YoutubeService.buildContext` + `TranscriptService` + `ClaudeCommentService` |
| 댓글 규칙 (40~120자, 링크·해시태그·가짜후기 금지) | `CommentRuleService` (생성 후 2차 검증·재생성) |
| ③ 크롬 프로필 열림 → 한 글자씩 → 게시 → 확인 | `BrowserPostService` (Playwright `launchPersistentContext`) |
| 기본 프로필 자동제어 금지 (`--user-data-dir`) | 프로필별 `user-data-dir`로 persistent context |
| 유튜브 한국어 화면 | `hl=ko` + locale `ko-KR` |
| 운영 규칙: 영상당 1개 · 하루 상한 · 기록 | `PostingService` + `PostLog`/`PostLogRepository` |
| 설정 예시 (profiles/comment/limits) | `application.yml` + `AppProperties` |
| 가점: 프로필 로테이션 | `PostingService.selectProfile` |
| 가점: 링크 직접 입력 | `/api/draft?url=...` + `VideoIdSupport` |
| 가점: 뜨기 직전 영상 찾기 | `YoutubeService.findTrending` + `/api/trending` |
| 비밀값 커밋 금지 | `${ENV}` 참조 + `.gitignore`/`.env.example` |

---

## 기술 스택

- **Spring Boot 3.5.3** (Java 21) — REST API + 대시보드 서빙
- **Playwright 1.52** (Java) — 실제 크롬 프로필 구동·타이핑·게시
- **YouTube Data API v3** — 영상 목록·메타·인기댓글·뜨기직전 탐색
- **Claude API** (`claude-sonnet-4-6`) — 고민 요약 + 댓글 생성
- **H2** (파일 DB) — 기록·운영규칙 집계
- 프론트: 바닐라 HTML/CSS/JS

---

## 사전 준비

### 1) 필수 설치
- **JDK 21**
- **Google Chrome** (설치돼 있어야 `channel=chrome`로 구동)

### 2) API 키 2개 발급
- **YouTube Data API v3 키** — Google Cloud Console → API 및 서비스 → 사용 설정 → 키 발급
- **Anthropic Claude API 키** — <https://console.anthropic.com>

> 키는 코드/저장소에 넣지 않고 **환경변수로만** 주입합니다. (`.env.example` 참고)

### 3) 크롬 프로필 로그인 준비 (③ 게시에 필수)
게시에 쓸 프로필 폴더를 **미리 유튜브에 로그인**해 둬야 합니다. 프로필 폴더에서 크롬을 한 번 열어 로그인 후 닫으세요.

```powershell
# 유튜브1 프로필 로그인 (최초 1회) — 창이 열리면 구글 계정 로그인 후 창 닫기
& "C:\Program Files\Google\Chrome\Application\chrome.exe" --user-data-dir="$PWD\chrome-profiles\youtube1"
```

> 유튜브2·3도 가점용이면 각각 다른 구글 계정으로 같은 방식(`youtube2`, `youtube3`)으로 로그인.
> `chrome-profiles/`는 `.gitignore`로 제외되어 로그인 세션이 저장소에 올라가지 않습니다.
> **필수는 유튜브1 하나**입니다.

---

## 실행

**PowerShell**
```powershell
$env:YOUTUBE_API_KEY="발급받은_유튜브_키"
$env:ANTHROPIC_API_KEY="발급받은_클로드_키"
.\mvnw.cmd spring-boot:run
```

**bash**
```bash
YOUTUBE_API_KEY=... ANTHROPIC_API_KEY=... ./mvnw spring-boot:run
```

브라우저에서 **<http://localhost:8080>** 접속.

> 키를 안 넣어도 서버·화면은 뜨며, 상단에 '환경변수 미설정' 경고가 표시됩니다(실제 호출 시에만 필요).

---

## 사용 흐름

1. **① 영상 불러오기** 클릭 → 최신 영상 목록 (또는 🔥 뜨기 직전 영상 / 링크 직접 입력)
2. 영상 하나 클릭 → **② 고민 한 줄 + 댓글 초안** 자동 생성 (필요하면 초안 수정·다시 생성, 프로필 선택)
3. **③ 댓글 달기** 클릭 → 크롬 프로필이 열리며 한 글자씩 타이핑 → 게시 → 내 댓글 확인
4. 하단 **기록**에서 성공/실패·스크린샷 확인

---

## 설정 변경 (`src/main/resources/application.yml`)

```yaml
app:
  comment: { min-chars: 40, max-chars: 120, brand: 운명원 }
  limits:  { daily-max-comments: 10 }      # 하루 게시 상한
  profiles:                                # 내 컴퓨터의 크롬 프로필
    - { name: 유튜브1, user-data-dir: ./chrome-profiles/youtube1 }
    - { name: 유튜브2, user-data-dir: ./chrome-profiles/youtube2 }
    - { name: 유튜브3, user-data-dir: ./chrome-profiles/youtube3 }
  browser: { type-delay-ms: 120, headless: false }   # 타이핑 간격 등
```

---

## 제작 의도

- **운영 규칙을 "코드에서" 강제** — 영상당 1개·하루 상한·기록을 UI 매너가 아니라 `PostingService`에서 막아,
  사람이 실수로 중복/과다 게시하는 것을 구조적으로 방지했습니다.
- **생성 후 2차 검증** — AI가 쓴 댓글을 그대로 믿지 않고 `CommentRuleService`로 글자수·금지(링크/해시태그/가짜후기)를
  다시 검사하고, 위반 시 사유를 붙여 재생성합니다. '운명원'은 '써봤다'가 아니라 '궁금해지는' 톤으로만 유도합니다.
- **지정 프로필(user-data-dir) 구동** — 평소 쓰는 기본 크롬을 건드리지 않도록 프로필 폴더를 분리해,
  로그인 세션을 재사용하면서도 기본 프로필 자동제어를 피했습니다.
- **맥락 이해 우선** — 자막을 best-effort로 읽고(없으면 제목·설명으로 폴백) 인기 댓글까지 재료로 넘겨,
  '그 영상에서만 나올 수 있는' 구체적 댓글이 되도록 했습니다.
- **설명 없이 쓰는 세 칸** — 화면을 요구사항 그대로 ①→②→③ 한 방향 흐름으로 두어 처음 보는 사람도 바로 쓰게 했습니다.

---

## 주의

- 유튜브 UI(한국어) 구조 변경 시 셀렉터 보강이 필요할 수 있습니다.
- 자동화는 **과제 시연 목적**입니다. 실제 운영 시 각 플랫폼 정책을 반드시 확인하세요.
- API 키 등 비밀값은 **환경변수로만** 주입하며 저장소에 커밋하지 않습니다.
