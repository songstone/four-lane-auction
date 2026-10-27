# Four-Lane Auction — REST API 계약

| 항목 | 내용 |
|---|---|
| 버전 | 0.4 (초안) |
| 작성일 | 2026-10-23 |
| 변경 이력 | 0.4 M1-01 승인: 비밀번호 UTF-8 72바이트 제한·코드 포인트 길이, 닉네임 중복 허용, Refresh 절대 7일·만료 경계·로컬 Secure 예외 / 0.1 최초 작성 / 0.2 프론트 관점 검토 반영 ([부록 B](#부록-b-프론트-관점-검토)): 로그아웃·내 정보 API, 출품물 진행 단계(`stage`)와 편성 가능 판정, 알림 미읽음 수, 다중 탭 재발급, 공개 API의 잘못된 토큰, 이미지 상한 값, 과거 예정 시각 거절. 화면별 데이터 출처 표([부록 A](#부록-a-화면별-데이터-출처))와 계약 질문([11장](#11-계약을-쓰며-찾은-질문))을 더함 / 0.3 계약 질문 CQ-01~04가 PRD OQ-45~48로 결정되어 해당 항목의 (제안)을 뗌. 12장의 차이를 PRD 0.5·ARCHITECTURE 0.3에 반영함. 로그아웃이 쿠키를 받도록 Refresh Token 쿠키 경로를 `/api/auth`로 넓힘 (1.3) |
| 근거 문서 | [PRD.md](PRD.md) 0.5, [ARCHITECTURE.md](ARCHITECTURE.md) 0.3 |
| 짝 문서 | [EVENTS.md](EVENTS.md) (WebSocket/STOMP 계약) |
| 상태 | 초안. 1주차 계약이다. 프론트와 백엔드는 이 문서와 EVENTS.md를 보고 병렬로 개발한다. springdoc가 코드에서 만든 OpenAPI가 이 문서와 같은지는 계약 테스트로 확인한다 |

---

## 0. 이 문서를 읽는 법

**표기** (PRD·ARCHITECTURE와 같다)

| 표기 | 뜻 |
|---|---|
| `(제안)` | PRD에도 ARCHITECTURE에도 없는 값이나 선택. 계약을 끝까지 쓰려고 이 문서가 정했다. 리뷰에서 바꿀 수 있다 |
| `[OQ-nn]` | PRD 10장의 미결 질문. 이 문서도 정하지 않는다. 결정이 계약에 주는 영향은 [10장](#10-미결-사항이-계약에-주는-영향) |
| `[B-nn]` | ARCHITECTURE 부록 B의 빈칸 |
| `CQ-nn` | 이 계약을 쓰며 찾은 질문. 모두 PRD OQ-45~48로 올라가 결정되었다 ([11장](#11-계약을-쓰며-찾은-질문)). 본문은 OQ 번호로 부른다 |
| 절 번호 | `PRD 6.3`, `ARCH 7.2`처럼 문서 이름을 붙인 번호는 그 문서의 절이다. 번호만 쓴 것은 이 문서의 절이고, `EVENTS 3.2`는 짝 문서의 절이다 |

**원칙**

- PRD 기능에 대응하는 엔드포인트만 둔다. ARCH 2.7의 목록이 출발점이다.
- 레인 화면 상태(현재가, 마감 시각 등)는 레인 이벤트와 스냅샷으로만 바꾼다. 입찰 응답으로는 바꾸지 않는다 (ARCH 2.5).
- 미결 사항(OQ)은 응답의 모양을 먼저 고정하고 값의 의미만 열어 둔다. 결정되어도 클라이언트 타입이 바뀌지 않게 하기 위해서다.

**하위 호환 규칙**

- 서버는 응답에 필드를 더할 수 있다. 클라이언트는 모르는 필드를 무시한다.
- enum에 값이 더해질 수 있다. 클라이언트는 모르는 값을 만나도 깨지지 않고 "알 수 없음"으로 표시한다.
- 필드 삭제, 이름·타입·의미 변경은 계약 변경이다. 이 문서의 버전을 올리고 프론트와 함께 바꾼다.

---

## 1. 공통 규칙

### 1.1 요청과 응답

- 모든 경로는 `/api`로 시작한다. 개발 중에는 Vite 개발 서버의 프록시가 같은 출처로 넘기므로 CORS가 없다 (ARCH 1.4).
- 본문은 JSON(UTF-8)이다. 출품 등록(6.4)만 `multipart/form-data`다.
- 필드 이름은 camelCase다.
- 응답 필드는 생략하지 않는다. 값이 없으면 `null`을 넣는다 (제안). 클라이언트 타입에서 `field?:`가 아니라 `field: T | null`로 다루게 하기 위해서다.
- 성공 상태: 조회와 명령은 `200`, 생성은 `201`, 본문 없는 성공은 `204`.
- 요청의 모르는 필드는 무시한다. 예: 회원가입 요청의 `role`은 무시된다 (AC-01-4).

### 1.2 값 형식

| 종류 | 형식 | 예 | 규칙 |
|---|---|---|---|
| ID | JSON 숫자 (64비트 정수) | `345` | DB의 `BIGINT` 값이다. JS `Number`의 안전 범위(2^53 − 1) 안에 있다. 회원 ID는 본인 정보 밖으로 나가지 않는다 (PRD 9.6) |
| 금액 | JSON 정수, 원 단위 | `60000` | 소수, 문자열, 통화 기호를 쓰지 않는다. 모든 가격은 1,000원 단위다 (PRD 6.3) |
| 시각 (응답) | ISO-8601, UTC(`Z`), 밀리초 3자리 | `"2026-10-23T11:00:03.120Z"` | 소수 초가 0이어도 `.000`을 쓴다 (ARCH R7) |
| 시각 (요청) | ISO-8601, 오프셋 필수 | `"2026-10-23T20:00:00.000+09:00"` | 오프셋이 없으면 `400 INVALID_REQUEST` (PRD C5, AC-04-5) |
| 기간 | 밀리초 정수. 필드 이름은 `…Ms` | `15000` | ARCH R11 |
| enum | 대문자 스네이크 문자열 | `"BIDDING"` | |
| 마스킹 닉네임 | 문자열 | `"송**"` | 첫 글자만 남기고 나머지 글자 수만큼 `*`로 바꾼다. 글자는 코드 포인트 단위로 센다 (ARCH 11) |
| 이미지 URL | 같은 출처의 상대 경로 | `"/api/images/9f3c0d…"` | `<img src>`에 그대로 쓴다. 클라이언트가 경로를 조립하지 않는다 (4.5) |

### 1.3 인증

M1-01 승인 (2026-10-27): Access Token은 15분, Refresh Token은 로그인부터 절대 7일이다. 회전해도 최초 만료 시각을 유지한다. 두 토큰 모두 서버의 주입 Clock으로 판단하며 만료 정각부터 무효다(시간 유예 0초). 쿠키의 Secure는 prod에서 항상 켜고, HTTP 개발을 위한 local/test에서만 끈다. 나머지 쿠키 속성은 동일하다.

| 항목 | 규칙 |
|---|---|
| Access Token | `Authorization: Bearer <accessToken>` 헤더로 보낸다. 수명 15분 (M1-01 승인, B-3). 클라이언트는 메모리에만 둔다 (ARCH 11). 만료 시각은 응답의 `accessTokenExpiresAt`으로 알고, 토큰 내용은 해석하지 않는다 |
| Refresh Token | `refreshToken` 쿠키. `HttpOnly; Secure; SameSite=Strict; Path=/api/auth` (제안, ARCH 11). JS로 읽을 수 없다. `/api/auth/*` 요청에만 실리고, 그중 쿠키로 동작하는 것은 재발급(3.3)과 로그아웃(3.4)뿐이다. 재발급할 때마다 바뀐다 (회전). 0.2의 `Path=/api/auth/refresh`로는 로그아웃 요청에 쿠키가 실리지 않아 0.3에서 넓혔다 |
| 권한 표기 | `공개`: 토큰 없이 된다 / `USER` / `ADMIN` / `로그인`: USER와 ADMIN 모두. 역할이 맞지 않으면 `403 FORBIDDEN` |
| 공개 API에 잘못된 토큰 | 만료되었거나 위조된 토큰을 붙이면 공개 API도 `401 UNAUTHENTICATED`다. 익명으로 낮춰 받지 않는다 (STOMP의 OQ-39와 같은 규칙. Spring Security 리소스 서버의 기본 동작이기도 하다). 클라이언트는 아래 401 처리를 그대로 따르고, 재발급이 실패하면 토큰 없이 다시 보낸다 |

**401 처리** (클라이언트)

1. `401 UNAUTHENTICATED`를 받으면 재발급(3.3)을 한 번 하고 같은 요청을 다시 보낸다. 동시에 여러 요청이 401을 받아도 재발급은 한 번만 한다.
2. 재발급도 `401`이면 비로그인 상태로 바꾼다. 로그인이 필요한 화면이면 로그인 화면(P-02)으로 보내고, 로그인한 뒤 원래 화면으로 돌아온다 (PRD 8.2). 공개 화면이면 토큰 없이 다시 보낸다.
3. 만료 전에 미리 재발급해도 된다 (제안: 만료 60초 전).

**여러 탭의 재발급**

- 탭들은 같은 Refresh Token 쿠키를 쓴다. 두 탭이 동시에 재발급하면 먼저 처리된 쪽이 토큰을 회전시키고, 늦은 쪽은 폐기된 토큰을 보낸 셈이 되어 `401`을 받는다 (ARCH 11, B-15: 재사용 유예가 없다).
- 그래서 재발급은 탭 사이에서도 한 번에 하나만 한다. Web Locks API(`navigator.locks.request('auth-refresh', …)`)로 감싼다 (제안).
- 락을 얻은 뒤 재발급이 `401`이면 한 번만 다시 시도한다. 기다리는 동안 다른 탭이 회전시켜 쿠키가 이미 새 값으로 바뀌었을 수 있기 때문이다. 그래도 `401`이면 비로그인 상태다.

### 1.4 에러 형식

```json
{
  "error": {
    "code": "PRICE_CHANGED",
    "message": "현재가가 바뀌었습니다.",
    "lane": { "epoch": "9c41e07a5b3d2f18", "seq": 59 }
  }
}
```

- 모든 4xx·5xx 응답이 이 모양이다.
- `code`: 클라이언트는 이 값으로만 분기한다. 전체 목록은 [9장](#9-에러-코드-전체-목록).
- `message`: 사람이 읽는 한국어 문장이다. 바뀔 수 있다. 화면 문구는 클라이언트가 `code`로 정하고, 모르는 코드일 때만 `message`를 보여 준다 (제안).
- 코드에 따라 `error` 안에 추가 필드가 있다(`lane`, `fields` 등). 각 엔드포인트에 적었다. 해당하지 않는 추가 필드는 넣지 않는다.
- ARCH 6.5 초안은 `code`, `message`, `lane`을 최상위에 두었다. 이 문서는 `error` 객체 안으로 옮겼다.
- 스택 트레이스와 내부 예외 이름은 넣지 않는다 (ARCH 11).

**HTTP 상태의 뜻** (PRD 6.3의 정의를 모든 API에 적용한다)

| 상태 | 뜻 | 클라이언트 |
|---|---|---|
| 400 | 형식이 잘못되었거나 클라이언트 계산에 버그가 있다 | 폼이면 필드 에러를 표시한다. 그 밖에는 버그로 로그 |
| 401 | 인증이 필요하다 | 1.3 |
| 403 | 이 사용자는 이 일을 할 수 없다 | 안내. 재시도하지 않는다 |
| 404 | 대상이 없다 | 목록으로 돌아가거나 새로 읽는다 |
| 409 | 화면이 낡았다. 최신 상태로 갱신하면 해결된다 | 갱신 |
| 413 | 업로드가 너무 크다 | 안내 |
| 422 | 지금 상태에서는 처리할 수 없다 | 상태를 갱신하고 안내 |
| 500 | 서버 오류. `BID_RESULT_UNKNOWN`만 "결과 미정"이라는 뜻이다 | 5.1. 그 밖에는 안내 |
| 503 | 서버가 지금 받을 여유가 없다. 요청은 처리되지 않았다 | 안내. 자동으로 재시도하지 않는다 |

**필드 에러** (`400 INVALID_REQUEST`)

```json
{
  "error": {
    "code": "INVALID_REQUEST",
    "message": "입력값을 확인해 주세요.",
    "fields": [
      { "field": "startPrice", "reason": "NOT_MULTIPLE_OF_1000" },
      { "field": "images", "reason": "REQUIRED" }
    ]
  }
}
```

- `fields`는 400 응답에만 있다. 필드와 무관한 형식 오류(JSON 파싱 실패 등)면 빈 배열이다.
- `field`: 요청 필드 이름. 중첩은 `timing.softCloseMs`, 배열은 `tiers[1].increment`, 헤더는 헤더 이름(`Idempotency-Key`), 경로 변수는 변수 이름(`laneId`)이다.
- `reason` (제안): `REQUIRED`, `INVALID_FORMAT`, `TOO_SHORT`, `TOO_LONG`, `OUT_OF_RANGE`, `NOT_MULTIPLE_OF_1000`, `LESS_THAN_START_PRICE`, `MISSING_OFFSET`, `DUPLICATE`, `TOO_MANY`. 화면 문구는 클라이언트가 정한다.

### 1.5 목록과 페이지

- 목록은 커서 방식이다 (제안). 요청 `?size=20&cursor=<값>`, 응답 `{ "items": [...], "nextCursor": "..." }`.
- `size`는 기본 20, 최대 100이다. `cursor`는 클라이언트가 해석하지 않는 문자열이다. `nextCursor`가 `null`이면 마지막 페이지다.
- 정렬은 엔드포인트마다 고정이다. 쪽 번호와 전체 개수는 주지 않는다.

### 1.6 멱등 키

입찰(5.1)과 결제(6.3)에 쓴다.

- 헤더 `Idempotency-Key`. 1~64자, 영문 대소문자·숫자·`-`·`_`만 쓴다 (제안, ARCH 11). `crypto.randomUUID()`를 권장한다.
- 키는 회원 단위로 유일하다. 다른 회원의 키와 겹쳐도 상관없다.
- 같은 키에 같은 요청(경로 포함)이면 처음 결과를 돌려준다. 요청이 다르면 `422 IDEMPOTENCY_KEY_REUSED`다.
- 성공 결과는 24시간 동안 재생한다. 서버가 재시작되어도 재생한다. 거절 결과는 재시작 전까지만 재생하고, 재시작 뒤에는 새로 평가할 수 있다 (OQ-33).
- **사용자가 버튼을 누를 때마다 새 키를 만든다.** 같은 키는 "응답을 받지 못한 같은 클릭"을 다시 보낼 때만 쓴다.

---

## 2. 엔드포인트 목록

| 그룹 | 메서드와 경로 | 권한 | 화면 (PRD 8.1) | 절 |
|---|---|---|---|---|
| 인증 | `POST /api/auth/signup` | 공개 | P-02 | 3.1 |
| | `POST /api/auth/login` | 공개 | P-02 | 3.2 |
| | `POST /api/auth/refresh` | Refresh Token 쿠키 | 앱 시작, 401 처리 | 3.3 |
| | `POST /api/auth/logout` | Refresh Token 쿠키 | 공통 헤더 | 3.4 |
| | `GET /api/me` | 로그인 | 공통 헤더, P-03 | 3.5 |
| 조회 | `GET /api/sessions` | 공개 | P-01, A-02 | 4.1 |
| | `GET /api/sessions/{sessionId}` | 공개 | P-03, A-04 | 4.2 |
| | `GET /api/lanes/{laneId}/snapshot` | 공개 | P-03, A-04 (재동기화) | 4.3 |
| | `GET /api/lots/{lotId}` | 공개 | P-03 Lot 상세 모달 | 4.4 |
| | `GET /api/images/{storageKey}` | 공개 | 전체 | 4.5 |
| 입찰 | `POST /api/lanes/{laneId}/bids` | USER | P-03 | 5.1 |
| 내 정보 | `GET /api/me/bids` | USER | U-03 | 6.1 |
| | `GET /api/me/trades` | USER | U-04 | 6.2 |
| | `POST /api/trades/{tradeId}/payment` | USER (낙찰자) | U-04 | 6.3 |
| | `POST /api/items` | USER | U-01 | 6.4 |
| | `GET /api/me/items` | USER | U-02 | 6.5 |
| | `POST /api/items/{itemId}/relist` | USER (판매자) | U-02 | 6.6 |
| | `GET /api/me/notifications` | USER | U-05 | 6.7 |
| | `POST /api/me/notifications/{notificationId}/read` | USER | U-05 | 6.8 |
| 운영자: 출품물 | `GET /api/admin/items` | ADMIN | A-01, A-03 | 7.1 |
| | `GET /api/admin/items/{itemId}` | ADMIN | A-01 | 7.1 |
| | `POST /api/admin/items/{itemId}/approve` | ADMIN | A-01 | 7.1 |
| | `POST /api/admin/items/{itemId}/reject` | ADMIN | A-01 | 7.1 |
| 운영자: 회차 | `POST /api/admin/sessions` | ADMIN | A-02 | 7.2 |
| | `GET /api/admin/sessions/{sessionId}` | ADMIN | A-02, A-03 | 7.2 |
| | `PATCH /api/admin/sessions/{sessionId}` | ADMIN | A-02 | 7.2 |
| | `PUT /api/admin/sessions/{sessionId}/lanes/{laneNo}/lots` | ADMIN | A-03 | 7.2 |
| | `POST /api/admin/sessions/{sessionId}/start` | ADMIN | A-04 | 7.2 |
| 운영자: 레인 제어 | `POST /api/admin/sessions/{sessionId}/pause`, `…/resume` | ADMIN | A-04 | 7.3 |
| | `POST /api/admin/lanes/{laneId}/pause`, `…/resume` | ADMIN | A-04 | 7.3 |
| | `POST /api/admin/lots/{lotId}/force-close` | ADMIN | A-04 | 7.3 |
| 운영자: 정책·기록 | `GET /api/admin/bid-policies`, `POST /api/admin/bid-policies` | ADMIN | A-05 | 7.4 |
| | `GET /api/admin/audit-logs` | ADMIN | A-06 (Could, C-05) | 7.5 |

ARCH 0.2의 2.7에 없던 것: `GET /api/admin/items/{itemId}`(검수 상세), `GET /api/admin/sessions/{sessionId}`(편성 화면이 편성을 고치려면 `itemId`가 필요하다), `POST /api/auth/logout`과 `GET /api/me`(프론트 검토에서 더함, 부록 B). 모두 ARCH 0.3의 2.7에 반영했다.

---

## 3. 인증

### 3.1 회원가입 — `POST /api/auth/signup`

권한: 공개

**요청**

```json
{ "email": "falcon@example.com", "password": "correct-horse-1", "nickname": "송골매" }
```

| 필드 | 타입 | 필수 | 규칙 |
|---|---|:-:|---|
| `email` | string | O | 이메일 형식, 최대 255자. 대소문자를 구분하지 않고 유일하다 |
| `password` | string | O | 8~64자이며 UTF-8 최대 72바이트. 초과하면 400 INVALID_REQUEST의 password 필드 오류. 로그인에도 같은 제한을 적용한다 (M1-01 승인) |
| `nickname` | string | O | 2~20자. 중복 허용 (M1-01 승인, B-5). 문자 길이는 코드 포인트 기준이다 |

**응답** `201`

```json
{ "memberId": 101, "email": "falcon@example.com", "nickname": "송골매", "role": "USER", "bidSuspended": false, "warningCount": 0 }
```

- 응답은 `Member`다 (8장). 역할은 언제나 `USER`다 (AC-01-4).
- 가입만 하고 로그인은 하지 않는다. 클라이언트는 이어서 3.2를 부른다 (제안).

**에러**: `400 INVALID_REQUEST`(`fields`), `409 EMAIL_ALREADY_EXISTS`

### 3.2 로그인 — `POST /api/auth/login`

권한: 공개

**요청**

```json
{ "email": "falcon@example.com", "password": "correct-horse-1" }
```

**응답** `200` + `Set-Cookie: refreshToken=…`

```json
{
  "accessToken": "eyJhbGciOi…",
  "accessTokenExpiresAt": "2026-10-23T11:15:00.000Z",
  "member": {
    "memberId": 101,
    "email": "falcon@example.com",
    "nickname": "송골매",
    "role": "USER",
    "bidSuspended": false,
    "warningCount": 0
  }
}
```

- `member`는 `Member` 객체다 (8장). 3.3, 3.5와 같은 모양이다.
- `member.role`로 메뉴를 나눈다. `ADMIN`이면 입찰 버튼, 출품 메뉴, 알림함을 보여 주지 않는다 (PRD 2.1).
- `bidSuspended`: 입찰 정지 상태 (S-02). `true`면 입찰 버튼을 비활성화하고 정지 안내를 보여 준다. 정지 기간과 해제는 [OQ-26]. `ADMIN`은 언제나 `false`다.
- `warningCount`: 미결제로 쌓인 경고 수 (S-02). `ADMIN`은 0이다.

**에러**: `400 INVALID_REQUEST`, `401 INVALID_CREDENTIALS`(이메일이 없는 경우와 비밀번호가 틀린 경우를 나누지 않는다)

### 3.3 토큰 재발급 — `POST /api/auth/refresh`

권한: Refresh Token 쿠키. 본문 없음.

**응답** `200`: 3.2와 같은 모양 + 새 `Set-Cookie`. 이전 Refresh Token은 폐기된다 (ARCH 11).

**에러**: `401 UNAUTHENTICATED`(쿠키 없음, 만료, 폐기됨)

- 앱을 처음 열 때(새로고침 포함) 이 API를 한 번 불러 로그인 상태를 복구한다. `401`이면 비로그인 상태로 시작한다.
- 여러 탭이 동시에 부르는 경우는 1.3 "여러 탭의 재발급".

### 3.4 로그아웃 — `POST /api/auth/logout`

권한: Refresh Token 쿠키 (없어도 된다). 본문 없음.

**응답** `204` + 쿠키를 지우는 `Set-Cookie`(`Max-Age=0`). 쿠키의 Refresh Token은 폐기한다. 쿠키가 없거나 이미 폐기된 토큰이어도 `204`다.

- **이 API가 필요한 이유** (PRD OQ-45, AC-01-5). ARCH 0.2의 B-1은 "클라이언트가 토큰을 버린다"로 로그아웃을 대신했다. 그런데 Refresh Token은 `HttpOnly` 쿠키라(1.3) JS가 지울 수 없다. API가 없으면 로그아웃 버튼을 눌러도 새로고침하면 3.3이 성공해 다시 로그인된다.
- 클라이언트는 응답과 관계없이 메모리의 Access Token을 버리고, STOMP를 토큰 없이 다시 연결한다 (EVENTS 1.6). 다른 탭은 다음 재발급에서 `401`을 받아 비로그인 상태가 된다.
- 이미 발급된 Access Token은 만료(최대 15분)까지 유효하다. 강제로 무효화하지 않는다.

### 3.5 내 정보 — `GET /api/me`

권한: 로그인.

**응답** `200`: `Member` (3.2의 `member`와 같다).

- 입찰 정지 상태는 로그인 중에도 바뀐다 (거래가 취소되어 경고가 쌓일 때, S-02). 관전 화면(P-03)에 들어갈 때와 `TRADE_CANCELED` 알림(EVENTS 4.2)을 받았을 때 다시 읽는다.
- 입찰 응답 `403 BIDDER_SUSPENDED`를 받아도 다시 읽는다.

---

## 4. 조회 (공개)

### 4.1 회차 목록 — `GET /api/sessions`

권한: 공개. 홈(P-01)과 회차 관리(A-02).

**쿼리**

| 이름 | 필수 | 값 |
|---|:-:|---|
| `status` | O | `SCHEDULED`, `RUNNING`, `FINISHED` 중 하나. 홈은 상태별로 세 번 부른다 |
| `size`, `cursor` | | 1.5 |

정렬: `RUNNING`은 `startedAt` 내림차순, `SCHEDULED`는 `scheduledStartAt` 오름차순, `FINISHED`는 `finishedAt` 내림차순.

**응답** `200`

```json
{
  "serverTime": "2026-10-23T10:58:00.000Z",
  "items": [
    {
      "sessionId": 7,
      "status": "SCHEDULED",
      "laneCount": 4,
      "scheduledStartAt": "2026-10-23T11:00:00.000Z",
      "startedAt": null,
      "finishedAt": null,
      "lotCount": 18
    }
  ],
  "nextCursor": null
}
```

- 회차 이름은 없다 [B-4]. 화면은 예정 시작 시각으로 회차를 부른다 (예: "10월 23일 20:00 회차").
- `lotCount`: 이 회차에 편성된 Lot 수 (모든 레인 합).
- `serverTime`: 시작까지 남은 시간을 그릴 때 시계 보정 표본으로 쓴다 (EVENTS 7).
- **예정 시각이 지났는데 `SCHEDULED`인 회차가 있다.** 시작 조건을 채우지 못했거나(AC-06-3) 예정 시각에 서버가 꺼져 있었던 경우다(AC-06-4, [OQ-38]). 화면은 카운트다운 대신 "시작 대기 중"으로 표시한다. 정렬상 예정 목록의 맨 앞에 온다.
- 목록에는 실시간 이벤트가 없다. 홈은 화면에 돌아올 때 다시 읽고, 머무는 동안은 30초마다 다시 읽는다 (제안).

### 4.2 회차 상세 — `GET /api/sessions/{sessionId}`

권한: 공개 (토큰이 있으면 `me`를 채운다). 관전 화면(P-03)과 진행 제어 콘솔(A-04)을 열 때, 그리고 재연결할 때 쓴다 (EVENTS 6).

**응답** `200`

```json
{
  "serverTime": "2026-10-23T11:00:04.010Z",
  "session": {
    "sessionId": 7,
    "status": "RUNNING",
    "laneCount": 4,
    "scheduledStartAt": "2026-10-23T11:00:00.000Z",
    "startedAt": "2026-10-23T11:00:00.004Z",
    "finishedAt": null,
    "timing": { "firstBidWaitMs": 15000, "softCloseMs": 10000, "lotGapMs": 5000, "resumeMinMs": 10000 }
  },
  "lanes": [
    { "laneId": 12, "laneNo": 1, "snapshot": { "…": "4.3 응답과 같은 모양" }, "lineup": null }
  ],
  "me": { "myLotIds": [345, 351] }
}
```

| 필드 | 뜻 |
|---|---|
| `session.timing` | 회차의 시간 파라미터 (PRD 6.4). 안내 문구("입찰하면 10초로 연장")와 정지 화면의 "재개하면 N초"에 쓴다 |
| `lanes` | `laneNo` 순서 |
| `lanes[].snapshot` | 4.3 응답과 같은 모양이다. 클라이언트는 스냅샷을 캐시에 넣는 함수 하나로 둘 다 처리한다 (EVENTS 6.6) |
| `lanes[].lineup` | `SCHEDULED` 회차에서만 레인별 편성 미리보기 (P-03). 원소는 `{ lotId, seqNo, name, imageUrl, startPrice }`. 그 밖에는 `null` |
| `me` | 토큰이 있을 때만, 없으면 `null`. `myLotIds`는 이 회차에서 내가 출품한 Lot 목록이다. 입찰 버튼을 미리 비활성화한다 (AC-12-4) |

- **`SCHEDULED` 회차는 이 응답이 낡을 수 있다.** 시작 전에는 운영자가 편성과 예정 시각을 바꿀 수 있는데(PRD A2, AC-04-4) 이를 알리는 이벤트가 없다. 편성이 바뀌면 `lotId`와 `me.myLotIds`도 바뀐다 (7.2). 그래서 예정 회차 화면은 이 API를 주기적으로 다시 읽는다 (EVENTS 6.7).
- 관전 화면에 처음 들어올 때 이 API를 두 번 부른다. 첫 번째로 `laneId`를 알아내 구독하고, 두 번째 응답을 기준 상태로 쓴다 (EVENTS 6.1).

**에러**: `404 SESSION_NOT_FOUND`

### 4.3 레인 스냅샷 — `GET /api/lanes/{laneId}/snapshot`

권한: 공개 (토큰이 있으면 `me`를 채운다). 레인 하나를 재동기화할 때 쓴다 (EVENTS 6.3).

**응답** `200`

```json
{
  "laneId": 12,
  "epoch": "9c41e07a5b3d2f18",
  "seq": 58,
  "serverTime": "2026-10-23T11:00:04.010Z",
  "heartbeatIntervalMs": 5000,
  "state": { "…": "EVENTS 3.2 LaneState" },
  "lotInfo": { "lotId": 345, "name": "필름 카메라", "imageUrl": "/api/images/9f3c0d…", "startPrice": 50000 },
  "nextLotInfo": null,
  "me": { "isHighestBidder": false }
}
```

| 필드 | 뜻 |
|---|---|
| `epoch`, `seq` | 이 스냅샷이 반영한 마지막 상태 이벤트. `state`와 언제나 같은 시점의 값이다 (ARCH 7.5) |
| `serverTime` | 응답을 만든 서버 시각. 시계 보정 표본이다 (EVENTS 7) |
| `heartbeatIntervalMs` | 레인 비컨의 주기 (EVENTS 5.4) |
| `state` | 레인 동적 상태. 필드는 EVENTS 3.2 |
| `lotInfo` | `state.lot`의 정적 정보(이름, 대표 이미지, 시작가). `state.lot`이 `null`이면 `null` |
| `nextLotInfo` | Lot 간 간격 중이고 다음 Lot이 있으면 그 정적 정보 (다음 Lot 미리보기, AC-12-5). 그 밖에는 `null` |
| `me` | 토큰이 있을 때만, 없으면 `null`. `isHighestBidder`: `state.lot`의 최고 입찰자가 나인지 (AC-11-4). 간격 중이거나 끝난 레인이면 `state.lot`이 끝난 Lot이므로, `SOLD`일 때 이 값은 "내가 낙찰자인지"다. 이 값은 스냅샷 시점의 값이라 뒤따르는 이벤트로는 갱신되지 않는다. 계산 규칙은 EVENTS 부록 B |

- 실행 중이 아닌 레인(`READY`, 서버 재시작 뒤의 `FINISHED`)은 `seq`가 0이다 (ARCH 7.5).

**에러**: `404 LANE_NOT_FOUND`

### 4.4 Lot 상세 — `GET /api/lots/{lotId}`

권한: 공개. Lot 상세 모달(P-03).

**응답** `200`

```json
{
  "lotId": 345,
  "sessionId": 7,
  "laneId": 12,
  "laneNo": 1,
  "seqNo": 3,
  "status": "BIDDING",
  "name": "필름 카메라",
  "description": "1980년대 제품. 셔터 정상 작동.",
  "images": [
    { "url": "/api/images/9f3c0d…", "sortOrder": 0 },
    { "url": "/api/images/a71b22…", "sortOrder": 1 }
  ],
  "startPrice": 50000,
  "finalPrice": null,
  "closedAt": null
}
```

- 현재가와 마감 시각은 없다. 레인 상태(EVENTS 3.2)에서 읽는다. 모달이 열려 있는 동안 이 API를 다시 부를 필요가 없다.
- `images`는 `sortOrder` 순서이고 0번이 대표 이미지다.
- `finalPrice`: `SOLD`면 낙찰가, 입찰이 있었던 `UNSOLD`면 마지막 최고가, 그 밖에는 `null`.
- 최저 낙찰가, 판매자 정보, 유찰 사유는 없다 (PRD 9.6, ARCH 7.3).

**에러**: `404 LOT_NOT_FOUND`

### 4.5 이미지 — `GET /api/images/{storageKey}`

권한: 공개.

- 응답은 이미지 바이트다. `Content-Type`은 업로드 때 파일 시그니처로 확인한 형식이고, `X-Content-Type-Options: nosniff`를 붙인다 (ARCH 11).
- 키가 가리키는 파일은 바뀌지 않으므로 `Cache-Control: public, max-age=31536000, immutable` (제안).
- 클라이언트는 응답에 있는 `imageUrl`을 그대로 쓴다.

**에러**: `404 IMAGE_NOT_FOUND` (JSON 본문)

---

## 5. 입찰

### 5.1 입찰 — `POST /api/lanes/{laneId}/bids`

권한: USER (PRD 6.3 조건 1)

**요청**

```
POST /api/lanes/12/bids
Authorization: Bearer eyJhbGciOi…
Idempotency-Key: 3f0b2a5e-8c1d-4e7a-9b6f-2d4c8e1a7f30
Content-Type: application/json

{ "lotId": 345, "expectedPrice": 50000, "amount": 60000 }
```

| 필드 | 값 | 화면 상태의 출처 (EVENTS 3.2) |
|---|---|---|
| `Idempotency-Key` (헤더) | 버튼을 누를 때마다 새로 만든 키 (1.6) | — |
| `lotId` | 화면에 보이는 Lot | `state.lot.lotId` |
| `expectedPrice` | 화면에 보이는 현재가. 첫 입찰이면 시작가다 (OQ-15) | `state.lot.currentPrice` |
| `amount` | 다음 호가 | `state.lot.nextPrice`. 클라이언트가 호가를 계산하지 않는다 (ARCH 7.2) |

**응답** `200`

```json
{
  "bidId": 90817,
  "laneId": 12,
  "lotId": 345,
  "amount": 60000,
  "acceptedAt": "2026-10-23T11:00:03.120Z",
  "deadlineAt": "2026-10-23T11:00:13.120Z",
  "epoch": "9c41e07a5b3d2f18",
  "seq": 58
}
```

| 필드 | 뜻 |
|---|---|
| `bidId` | 이 입찰의 ID. 레인 상태의 `state.lot.lastBidId`와 비교해 "내가 최고 입찰자"인지 판정한다 (ARCH 2.5) |
| `acceptedAt` | 레인 큐가 이 입찰을 처리한 서버 시각 (PRD C3) |
| `deadlineAt` | 이 입찰을 반영한 마감 시각. 기록용이다. 카운트다운은 레인 상태로 그린다 |
| `epoch`, `seq` | 이 입찰이 만든 `BID_ACCEPTED` 이벤트의 epoch와 seq |

- `serverTime`은 없다. 입찰 응답은 시계 보정에 쓰지 않는다 (ARCH 7.7).
- 같은 키로 다시 보내 받은 응답(재생)은 처음 본문 그대로다. 그 사이 서버가 재시작되었다면 `epoch`가 지금과 다르다.
- 성공 응답으로 레인 화면 상태를 바꾸지 않는다. 현재가와 마감 시각은 `BID_ACCEPTED` 이벤트가 바꾼다 (ARCH 2.5).

**검증 순서** (PRD 6.3, ARCH 6.2): 인증·역할 → 형식 → 레인 존재 → 2 멱등 → 3 입찰 정지 → 4 본인 출품 → 큐 상한 → 5~9. 처음 실패한 사유 하나로 거절한다 (AC-09-3).

**거절과 결과 미정**

| 순서 | 코드 | HTTP | 조건 | `error.lane` | 클라이언트 처리 |
|---|---|---|---|:-:|---|
| 1 | `UNAUTHENTICATED` | 401 | 토큰 없음, 만료, 위조 | | 재발급 뒤 같은 키로 한 번 다시 보낸다. 처리 전에 거절되었으므로 키는 기록되지 않았다. 재발급이 실패하면 로그인 화면 |
| 1 | `FORBIDDEN` | 403 | `USER`가 아님 (`ADMIN` 포함) | | 버그. ADMIN에게는 버튼이 처음부터 없다 |
| — | `INVALID_REQUEST` | 400 | 헤더·필드 누락, 형식 오류 | | 버그로 간주, 로그 |
| — | `LANE_NOT_FOUND` | 404 | 없는 `laneId` | | 회차 상세를 다시 읽는다 |
| 2 | `IDEMPOTENCY_KEY_REUSED` | 422 | 처리한 키에 다른 요청 | | 버그로 간주. 키는 클릭마다 새로 만든다 |
| 3 | `BIDDER_SUSPENDED` | 403 | 입찰 정지 회원 (S-02) | | 정지 안내 |
| 4 | `SELF_BID_FORBIDDEN` | 403 | 본인 출품 Lot | | 버그. `me.myLotIds`로 미리 막는다 |
| 4~5 | `LANE_OVERLOADED` | 503 | 레인 큐 상한 도달. 처리하지 않았고 키도 기록하지 않았다 | | "혼잡" 안내. 자동 재시도 금지. 사용자가 다시 누르면 새 키로 새 요청 |
| 5 | `LANE_NOT_RUNNING` | 422 | 레인이 `READY`, `PAUSED`, `FINISHED` | O | 아래 "lane을 받으면" |
| 5 | `LOT_NOT_BIDDING` | 422 | Lot 간 간격 중, 또는 처리 시각이 이미 마감 시각을 지남 | O | 아래 "lane을 받으면" |
| 6 | `LOT_MISMATCH` | 409 | `lotId`가 지금 `BIDDING`인 Lot이 아님 | O | 아래 "lane을 받으면" |
| 7 | `PRICE_CHANGED` | 409 | `expectedPrice` ≠ 서버 현재가 | O | 아래 "lane을 받으면". 자동 재입찰 금지 (PRD B4) |
| 8 | `INVALID_BID_AMOUNT` | 400 | `amount` ≠ 다음 호가 | | 버그로 간주, 로그. 레인 스냅샷을 다시 받는다 |
| 9 | `ALREADY_HIGHEST_BIDDER` | 409 | 이미 최고 입찰자 | O | 아래 "lane을 받으면" |
| — | `BID_RESULT_UNKNOWN` | 500 | 결과 미정. 입찰은 처리되었을 수도 있다 (OQ-44) | | 같은 키로 재전송 (아래) |

**`error.lane`을 받으면** (ARCH 2.5)

- `error.lane`은 `{ "epoch", "seq" }`이고, 서버가 거절을 판정한 순간 그 레인의 마지막 상태 이벤트를 가리킨다.
- 스냅샷을 부르지 않는다. 같은 epoch에서 이 `seq`까지 레인 이벤트가 반영되기를 기다린다. 시간 제한은 두지 않는다. 그 사이 갭이 보이면 재동기화가 처리하고, 이벤트를 잃었다면 레인 비컨이 알려 준다 (EVENTS 5.4).
- `epoch`가 지금 레인 상태와 다르면 기다리지 않는다. 재동기화가 처리한다.

**재전송 규칙** (PRD 6.3 조건 2, 9.4-5, ARCH 7.6)

| 받은 것 | 재전송 | 키 |
|---|---|---|
| 응답 없음 (네트워크 오류, 시간 제한) | 처음 요청 뒤 10초 안에서 | 같은 키, 같은 본문 |
| `500 BID_RESULT_UNKNOWN` | 같음 | 같은 키, 같은 본문 |
| `401 UNAUTHENTICATED` | 재발급 뒤 1번 | 같은 키, 같은 본문 |
| 그 밖의 4xx, `503` | 하지 않는다 | — |

- 시도마다 시간 제한은 5초다 (제안). 서버의 결과 대기 상한은 이보다 짧다 (ARCH 6.2, 값은 측정).
- 재전송 간격은 0.5초, 1초, 2초, …로 늘린다 (제안). 처음 요청 뒤 10초가 지나면 멈춘다.
- 재전송 본문은 처음과 같아야 한다. 그 사이 화면의 현재가가 바뀌었어도 처음 값을 그대로 보낸다. 바꾸면 `IDEMPOTENCY_KEY_REUSED`다.
- 10초 안에 결과를 받지 못하면 레인 스냅샷(4.3)으로 확인한다 (PRD 9.4-5).
  - `me.isHighestBidder`가 `true`이고 `state.lot.lotId`가 보낸 `lotId`와 같으면 성공으로 확정한다.
  - `false`이면 확정할 수 없다. 실패했을 수도 있고, 성공한 뒤 다른 입찰에 밀렸을 수도 있다. "결과는 내 입찰 내역에서 확인" 안내를 띄운다 (6.1). 서버 재시작 뒤라면 레인이 `PAUSED`라 그 사이 밀릴 수 없으므로, 이때의 `false`는 실패다.
- 재전송하는 동안 그 레인의 입찰 버튼은 비활성화한다. 버튼 상태 전체는 EVENTS 부록 B.

**화면 상태와의 관계**

- "내가 최고 입찰자" 판정, 입찰 버튼 활성화, 낙찰·패찰 표시는 이 응답과 레인 상태를 합쳐 계산한다. 계산 규칙은 EVENTS 부록 B에 모았다.

---

## 6. 내 정보

### 6.1 내 입찰 내역 — `GET /api/me/bids`

권한: USER. U-03. 내가 수락된 입찰을 1건 이상 넣은 Lot마다 한 줄이다. 거절된 입찰은 기록이 없으므로 나오지 않는다.

**쿼리**: `size`, `cursor`. 정렬: 그 Lot에 내가 마지막으로 입찰한 시각의 내림차순.

**응답** `200`

```json
{
  "items": [
    {
      "lotId": 345,
      "sessionId": 7,
      "laneId": 12,
      "laneNo": 1,
      "name": "필름 카메라",
      "imageUrl": "/api/images/9f3c0d…",
      "lotStatus": "SOLD",
      "myHighestAmount": 60000,
      "myBidCount": 2,
      "myLastBidAt": "2026-10-23T11:00:03.120Z",
      "finalPrice": 60000,
      "result": "WON",
      "tradeId": 55
    }
  ],
  "nextCursor": null
}
```

| `result` | 조건 | 화면 |
|---|---|---|
| `LEADING` | Lot이 `BIDDING`, 내가 최고 입찰자 | 진행 중 (최고 입찰) |
| `OUTBID` | Lot이 `BIDDING`, 내가 최고 입찰자가 아님 | 진행 중 (밀림) |
| `WON` | `SOLD`, 내가 낙찰자 | 낙찰. `tradeId`로 U-04 이동 |
| `LOST` | `SOLD`, 내가 낙찰자가 아님 | 패찰 |
| `UNSOLD` | `UNSOLD` (최저가 미달 또는 강제 종료) | 화면 문구는 [OQ-25] |

- `finalPrice`: Lot이 끝났으면 4.4와 같은 규칙, 진행 중이면 `null`.
- `tradeId`: `WON`이면 거래 ID, 그 밖에는 `null`.

### 6.2 내 거래 — `GET /api/me/trades`

권한: USER. U-04.

**쿼리**: `status`(선택: `AWAITING_PAYMENT`, `COMPLETED`, `CANCELED`), `size`, `cursor`. 정렬: 생성 시각 내림차순.

**응답** `200`

```json
{
  "serverTime": "2026-10-23T11:05:00.000Z",
  "items": [
    {
      "tradeId": 55,
      "lotId": 345,
      "sessionId": 7,
      "name": "필름 카메라",
      "imageUrl": "/api/images/9f3c0d…",
      "price": 60000,
      "status": "AWAITING_PAYMENT",
      "paymentDueAt": "2026-10-24T11:00:13.200Z",
      "createdAt": "2026-10-23T11:00:13.200Z",
      "completedAt": null,
      "canceledAt": null
    }
  ],
  "nextCursor": null
}
```

- 결제 기한이 지났지만 만료 작업이 아직 돌지 않은 거래는 잠시 `AWAITING_PAYMENT`로 보일 수 있다. 화면은 `paymentDueAt`이 지났으면 결제 버튼을 비활성화한다. 기한 경계의 판정은 [OQ-36].
- `serverTime`: 결제 기한 카운트다운의 시계 보정 표본이다. 이 화면에는 레인 구독이 없어 다른 표본이 없을 수 있다 (EVENTS 7.2). 결제 기한을 몇 분으로 줄인 시연(PRD 6.4)에서 필요하다.

### 6.3 모의 결제 — `POST /api/trades/{tradeId}/payment`

권한: USER (그 거래의 낙찰자). `Idempotency-Key` 필수 (1.6). 본문 없음.

**응답** `200`

```json
{ "tradeId": 55, "status": "COMPLETED", "completedAt": "2026-10-23T11:05:00.000Z" }
```

- 가짜 결제 모듈은 언제나 승인한다 (ARCH 3.4). 결제 수단을 입력받지 않는다.
- 응답을 받지 못했거나 5xx면 같은 키로 다시 보낸다. 결제는 한 번만 처리된다 (AC-16-2).

**에러**

| 코드 | HTTP | 조건 |
|---|---|---|
| `INVALID_REQUEST` | 400 | 멱등 키 누락·형식 오류 |
| `NOT_TRADE_BUYER` | 403 | 낙찰자가 아님 (AC-16-4) |
| `TRADE_NOT_FOUND` | 404 | 없는 거래 |
| `TRADE_NOT_AWAITING_PAYMENT` | 422 | 이미 `COMPLETED` 또는 `CANCELED`. `error.tradeStatus`에 현재 상태 |
| `PAYMENT_DEADLINE_PASSED` | 422 | 결제 기한이 지남. 경계는 [OQ-36] |
| `IDEMPOTENCY_KEY_REUSED` | 422 | 다른 거래에 쓴 키 |

### 6.4 출품 등록 — `POST /api/items`

권한: USER (`ADMIN`은 `403`, AC-02-7). U-01. `multipart/form-data`.

**파트**

| 파트 | 종류 | 필수 | 규칙 |
|---|---|:-:|---|
| `images` | 파일, 여러 개 | O | 1~10장, 장당 10MB 이하, JPEG·PNG·WebP (제안, [B-6]). 형식은 확장자가 아니라 파일 시그니처로 확인한다 (ARCH 11) |
| `name` | 텍스트 | O | 1~100자 |
| `description` | 텍스트 | | 최대 2,000자 (제안) |
| `startPrice` | 텍스트 (정수) | O | 1,000원 이상 10억 원 이하, 1,000원 단위 (AC-02-3). 상한은 (제안) |
| `reservePrice` | 텍스트 (정수) | | 1,000원 단위, `startPrice` 이상 (AC-02-4). 비우면 `startPrice`와 같은 값으로 저장한다 (AC-02-5). 공개되지 않는다 |

- `images`는 보낸 순서가 표시 순서이고, 첫 장이 대표 이미지다.
- 이미지 상한 값은 폼이 미리 검사하고 안내 문구에 쓰라고 적은 것이다. PRD에 값이 없어 (제안)이고, 바뀌면 이 표를 고친다. 파일 선택기는 `accept="image/jpeg,image/png,image/webp"`.
- 장수 초과는 `400`(`fields[].reason = TOO_MANY`), 형식 불일치는 `400 UNSUPPORTED_IMAGE_TYPE`, 크기 초과는 `413 IMAGE_TOO_LARGE`다.

**응답** `201`

```json
{ "itemId": 31, "name": "필름 카메라", "reviewStatus": "PENDING_REVIEW", "createdAt": "2026-10-23T09:00:00.000Z" }
```

**에러**: `400 INVALID_REQUEST`(`fields`), `400 UNSUPPORTED_IMAGE_TYPE`, `413 IMAGE_TOO_LARGE`, `403 FORBIDDEN`

### 6.5 내 출품 목록 — `GET /api/me/items`

권한: USER. U-02.

**쿼리**: `size`, `cursor`. 정렬: 등록 시각 내림차순.

**응답** `200`

```json
{
  "items": [
    {
      "itemId": 31,
      "name": "필름 카메라",
      "imageUrl": "/api/images/9f3c0d…",
      "startPrice": 50000,
      "reviewStatus": "APPROVED",
      "stage": "UNSOLD",
      "rejectReason": null,
      "reviewedAt": "2026-10-23T09:30:00.000Z",
      "createdAt": "2026-10-23T09:00:00.000Z",
      "currentLot": {
        "lotId": 345,
        "sessionId": 7,
        "laneNo": 1,
        "seqNo": 3,
        "status": "UNSOLD",
        "scheduledStartAt": "2026-10-23T11:00:00.000Z",
        "finalPrice": 60000,
        "tradeStatus": null
      }
    }
  ],
  "nextCursor": null
}
```

- `reviewStatus`: 검수 상태(`PENDING_REVIEW`, `APPROVED`, `REJECTED`). 반려면 `rejectReason`을 보여 준다 (AC-03-2).
- `stage`: 출품물의 진행 단계. 서버가 계산한다. 화면의 상태 표시와 재출품 버튼은 이 값으로만 정한다. 정의는 아래 "출품물 진행 단계".
- `currentLot`: `stage`를 정한 Lot. `stage`가 `SCHEDULED`, `ON_AUCTION`, `SOLD`, `UNSOLD`일 때만 있고, 그 밖에는 `null`이다. `finalPrice`는 4.4와 같은 규칙, `tradeStatus`는 `SOLD`일 때 거래 상태(6.2)다.

**출품물 진행 단계 (`stage`)** (PRD OQ-46, ARCH 4.4)

검수 상태만으로는 화면을 그릴 수 없다. 낙찰되거나 유찰된 뒤에도 Item의 검수 상태는 `APPROVED`로 남기 때문이다 (ARCH 4.4 `item`: 경매 결과는 Lot에서 읽는다). 그래서 서버가 단계를 계산해 준다.

| `stage` | 조건 | U-02 표시 | 편성 가능 (7.2) |
|---|---|---|:-:|
| `PENDING_REVIEW` | 검수 상태가 `PENDING_REVIEW` (재출품 직후 포함) | 검수 대기 | X |
| `REJECTED` | 검수 상태가 `REJECTED` | 반려 + 사유 | X |
| `APPROVED` | 승인됨, 이번 승인 뒤 편성된 Lot 없음 | 승인됨 · 편성 대기 | O |
| `SCHEDULED` | 이번 승인 뒤의 Lot이 `PENDING` | 편성됨 (회차 시각, 레인, 순번) | 같은 레인 재편성만 O. 다른 곳은 [OQ-28] |
| `ON_AUCTION` | 그 Lot이 `BIDDING` | 경매 중 → P-03 | X |
| `SOLD` | 그 Lot이 `SOLD` | 낙찰 (낙찰가, 거래 상태) | X |
| `UNSOLD` | 그 Lot이 `UNSOLD` | 유찰 + 재출품 버튼 | X (재출품 → 재승인 뒤 다시 `APPROVED`) |

- "이번 승인 뒤의 Lot" = 생성 시각이 이 Item의 마지막 검수 시각(`reviewedAt`) 이후인 Lot 중 가장 최근 것이다. 재출품해서 다시 승인받으면 지난 유찰 Lot은 이 범위에서 빠지므로 단계가 `APPROVED`로 돌아온다. 지금 스키마(`item.reviewed_at`, `lot.created_at`)로 계산할 수 있다.
- 편성에서 빠진 Lot은 지워지므로(ARCH 4.4) 단계도 `APPROVED`로 돌아온다.

### 6.6 재출품 — `POST /api/items/{itemId}/relist`

권한: USER (그 Item의 판매자). 본문 없음. 가격을 바꿀 수 있는지는 [OQ-28]이고, 지금은 이전 가격 그대로 검수 대기로 돌아간다 (S-05).

**응답** `200`

```json
{ "itemId": 31, "reviewStatus": "PENDING_REVIEW", "stage": "PENDING_REVIEW" }
```

**에러**: `403 NOT_ITEM_SELLER`, `404 ITEM_NOT_FOUND`, `422 RELIST_NOT_ALLOWED`(`stage`가 `UNSOLD`가 아님. `error.stage`에 현재 단계)

### 6.7 알림함 — `GET /api/me/notifications`

권한: USER. U-05.

**쿼리**: `unreadOnly`(선택, 기본 `false`), `size`, `cursor`. 정렬: 생성 시각 내림차순.

**응답** `200`

```json
{
  "items": [
    {
      "id": 901,
      "type": "OUTBID",
      "payload": { "…": "EVENTS 4.2" },
      "createdAt": "2026-10-23T11:00:05.300Z",
      "readAt": null
    }
  ],
  "nextCursor": null,
  "unreadCount": 3
}
```

- 알림 원소는 개인 큐로 오는 메시지와 같은 모양이다. `type`별 `payload`와 눌렀을 때 이동할 화면은 EVENTS 4.2.
- `unreadCount`: 읽지 않은 알림의 전체 수. 쿼리·페이지와 무관하다. 헤더의 알림 배지에 쓴다. 배지만 필요하면 `size=1`로 부른다.
- 개인 큐로 알림을 받으면 목록 맨 앞에 넣고 `unreadCount`를 1 올린다. 연결하거나 재연결할 때마다 이 API를 다시 읽어 맞춘다 (EVENTS 4.3).

### 6.8 알림 읽음 — `POST /api/me/notifications/{notificationId}/read`

권한: USER. 본문 없음.

**응답** `204`. 이미 읽은 알림이어도 `204`다.

**에러**: `404 NOTIFICATION_NOT_FOUND`(없거나 내 알림이 아님)

---

## 7. 운영자 (ADMIN)

운영자 API의 에러 코드는 PRD 6.1이 "1주차 OpenAPI 계약에서 정한다"고 남긴 것이다. 이 절에서 정한다.

### 7.1 출품물 검수

#### `GET /api/admin/items`

A-01(검수 대기 목록), A-03(편성할 출품물 목록).

**쿼리**: `stage`(필수: 6.5의 `stage` 값 하나), `size`, `cursor`. 정렬: `PENDING_REVIEW`는 등록 시각 오름차순(먼저 온 것부터), 그 밖은 내림차순.

- A-01 검수 대기 목록은 `stage=PENDING_REVIEW`, A-03 편성할 출품물 목록은 `stage=APPROVED`다.
- ARCH 0.2의 2.7은 `?reviewStatus=`였다. 검수 상태로 거르면 이미 낙찰·유찰된 Item까지 편성 목록에 나온다 (6.5 "출품물 진행 단계", PRD OQ-46).

**응답** `200`

```json
{
  "items": [
    {
      "itemId": 31,
      "name": "필름 카메라",
      "imageUrl": "/api/images/9f3c0d…",
      "startPrice": 50000,
      "sellerNickname": "송골매",
      "reviewStatus": "PENDING_REVIEW",
      "stage": "PENDING_REVIEW",
      "createdAt": "2026-10-23T09:00:00.000Z",
      "reviewedAt": null
    }
  ],
  "nextCursor": null
}
```

- 운영자에게는 판매자 닉네임을 마스킹하지 않는다 (제안).

#### `GET /api/admin/items/{itemId}`

A-01 검수 상세.

**응답** `200`

```json
{
  "itemId": 31,
  "name": "필름 카메라",
  "description": "1980년대 제품. 셔터 정상 작동.",
  "images": [ { "url": "/api/images/9f3c0d…", "sortOrder": 0 } ],
  "startPrice": 50000,
  "sellerNickname": "송골매",
  "reviewStatus": "PENDING_REVIEW",
  "stage": "PENDING_REVIEW",
  "rejectReason": null,
  "createdAt": "2026-10-23T09:00:00.000Z",
  "reviewedAt": null,
  "lots": [ { "lotId": 210, "sessionId": 5, "laneNo": 2, "seqNo": 4, "status": "UNSOLD", "finalPrice": null } ]
}
```

- `lots`: 이 Item의 지난 Lot (재출품 이력). 최신순.
- 최저 낙찰가는 없다. 운영자에게 보여 줄지는 [B-7].

**에러**: `404 ITEM_NOT_FOUND`

#### `POST /api/admin/items/{itemId}/approve`

**응답** `200`: `{ "itemId": 31, "reviewStatus": "APPROVED", "reviewedAt": "…" }`. 판매자에게 `REVIEW_RESULT` 알림이 간다.

#### `POST /api/admin/items/{itemId}/reject`

**요청**: `{ "reason": "이미지가 흐려 상태를 확인할 수 없습니다." }` (1~500자)

**응답** `200`: `{ "itemId": 31, "reviewStatus": "REJECTED", "reviewedAt": "…" }`. 판매자에게 `REVIEW_RESULT` 알림이 간다.

**에러** (두 API 공통): `404 ITEM_NOT_FOUND`, `422 ITEM_NOT_PENDING_REVIEW`(이미 검수함. `error.reviewStatus`에 현재 상태), `400 INVALID_REQUEST`(반려 사유)

### 7.2 회차 관리

#### `POST /api/admin/sessions` — 회차 생성

A-02.

**요청**

```json
{
  "laneCount": 4,
  "scheduledStartAt": "2026-10-23T20:00:00.000+09:00",
  "timing": { "firstBidWaitMs": 15000, "softCloseMs": 10000, "lotGapMs": 5000, "resumeMinMs": 10000 }
}
```

| 필드 | 필수 | 규칙 |
|---|:-:|---|
| `laneCount` | O | 1~4 (AC-04-2) |
| `scheduledStartAt` | O | 오프셋 필수 (AC-04-5) |
| `timing` | | 통째로 생략하거나 일부만 넣으면 빠진 값은 기본값(15초 / 10초 / 5초 / 10초)으로 채운다 (AC-04-3). 1 이상의 정수. 허용 범위는 [OQ-14] |

**응답** `201`: `GET /api/admin/sessions/{sessionId}`와 같은 모양.

**에러**: `400 INVALID_REQUEST`(`fields`), `422 SCHEDULED_START_IN_PAST`

- `SCHEDULED_START_IN_PAST`: 예정 시각이 서버의 현재 시각 이전이다 (PRD OQ-48, AC-04-6). 받아 주면 그 회차는 자동으로 시작하지 않는다. 지난 예정 시각은 "놓친" 것으로 보기 때문이다 (AC-06-4, [OQ-38]). 운영자가 시각을 잘못 넣은 것을 모른 채 기다리게 되므로 거절한다.

#### `GET /api/admin/sessions/{sessionId}` — 회차와 편성

A-02, A-03. 편성을 고치는 데 필요한 `itemId`를 담는다. 진행 중인 레인의 실시간 상태는 이 API가 아니라 4.2와 레인 이벤트로 본다.

**응답** `200`

```json
{
  "session": {
    "sessionId": 7,
    "status": "SCHEDULED",
    "laneCount": 4,
    "scheduledStartAt": "2026-10-23T11:00:00.000Z",
    "startedAt": null,
    "finishedAt": null,
    "timing": { "firstBidWaitMs": 15000, "softCloseMs": 10000, "lotGapMs": 5000, "resumeMinMs": 10000 },
    "bidPolicyVersion": null
  },
  "startable": false,
  "emptyLaneNos": [3],
  "lanes": [
    {
      "laneId": 12,
      "laneNo": 1,
      "laneStatus": "READY",
      "lots": [
        {
          "lotId": 345,
          "seqNo": 1,
          "itemId": 31,
          "name": "필름 카메라",
          "imageUrl": "/api/images/9f3c0d…",
          "startPrice": 50000,
          "sellerNickname": "송골매",
          "status": "PENDING"
        }
      ]
    }
  ]
}
```

| 필드 | 뜻 |
|---|---|
| `bidPolicyVersion` | 회차 시작 때 고정된 호가 정책 버전. `SCHEDULED`면 `null` (AC-06-5) |
| `startable` | `SCHEDULED`이고 모든 레인에 Lot이 1개 이상 있으면 `true` (A-03 시작 조건 표시) |
| `emptyLaneNos` | Lot이 없는 레인 번호 |

**에러**: `404 SESSION_NOT_FOUND`

#### `PATCH /api/admin/sessions/{sessionId}` — 예정 시각·시간 파라미터 변경

A-02. `SCHEDULED`에서만 된다.

**요청**: `{ "scheduledStartAt": "…+09:00" }` 또는 `{ "timing": { "softCloseMs": 8000 } }`. 넣은 필드만 바꾼다.

- 예정 시각을 바꾸면 새 시각에 자동 시작한다 (AC-04-4).
- 시간 파라미터를 언제까지, 어느 범위로 바꿀 수 있는지는 [OQ-14].

**응답** `200`: `GET /api/admin/sessions/{sessionId}`와 같은 모양.

**에러**: `400 INVALID_REQUEST`, `404 SESSION_NOT_FOUND`, `422 SESSION_NOT_SCHEDULED`, `422 SCHEDULED_START_IN_PAST` (회차 생성과 같다)

#### `PUT /api/admin/sessions/{sessionId}/lanes/{laneNo}/lots` — 레인 편성

A-03. 레인 하나의 Lot 순서 전체를 바꾼다 (ARCH 2.7).

**요청**

```json
{ "itemIds": [31, 40, 52] }
```

- 배열 순서가 순번(`seqNo` 1, 2, 3…)이다. 빈 배열이면 레인을 비운다.
- 같은 `itemId`가 배열에 두 번 있으면 `400`(`fields[].reason = DUPLICATE`).
- 넣을 수 있는 Item은 `stage`가 `APPROVED`인 것과, 지금 이 레인에 편성되어 있는 것(`SCHEDULED`)이다 (6.5). 낙찰·유찰·경매 중인 Item은 거절한다. 유찰된 Item은 판매자가 재출품하고 다시 승인받아야 편성할 수 있다 (S-05).
- 다른 레인·회차에 이미 편성된(`SCHEDULED`) Item을 막을지는 [OQ-28]이다. 지금은 막지 않는다 (ARCH 4.6). 편성 화면의 후보 목록은 `stage=APPROVED`만 보여 주므로, 화면으로는 중복 편성이 생기지 않는다.
- 레인의 Lot을 지우고 다시 만들므로 **호출할 때마다 `lotId`가 새로 생긴다** (ARCH 4.4). 편성 화면은 응답의 `lotId`로 바꿔 끼운다.

**응답** `200`

```json
{ "laneId": 12, "laneNo": 1, "laneStatus": "READY", "lots": [ { "…": "GET /api/admin/sessions/{id}의 lots 원소" } ] }
```

**에러**

| 코드 | HTTP | 조건 |
|---|---|---|
| `INVALID_REQUEST` | 400 | 형식, 중복 |
| `SESSION_NOT_FOUND` | 404 | |
| `LANE_NOT_FOUND` | 404 | `laneNo`가 1~`laneCount` 밖 |
| `SESSION_NOT_SCHEDULED` | 422 | 회차가 이미 시작함. 편성이 잠겼다 (AC-05-3) |
| `ITEM_NOT_FOUND` | 404 | 없는 `itemId`가 있다. `error.itemIds`에 해당 ID |
| `ITEM_NOT_ASSIGNABLE` | 422 | 편성할 수 없는 Item이 있다. 검수 전·반려(AC-05-2)와 낙찰·유찰·경매 중을 포함한다. `error.items`에 `[{ "itemId", "stage" }]` |

#### `POST /api/admin/sessions/{sessionId}/start` — 지금 시작

A-04 (AC-06-2). 본문 없음.

**응답** `200`

```json
{ "sessionId": 7, "status": "RUNNING", "startedAt": "2026-10-23T10:55:00.120Z" }
```

- 레인 상태는 레인마다 오는 `LOT_STARTED`(seq 1)로 받는다 (EVENTS 3.4).

**에러**

| 코드 | HTTP | 조건 |
|---|---|---|
| `SESSION_NOT_FOUND` | 404 | |
| `SESSION_NOT_SCHEDULED` | 422 | 이미 시작함 (자동 시작과 겹친 경우 포함) |
| `SESSION_START_CONDITION_NOT_MET` | 422 | Lot이 없는 레인이 있다 (AC-06-3). `error.emptyLaneNos` |

### 7.3 레인 제어

- 운영자 명령은 입찰과 같은 레인 큐를 지나고, 처리 결과를 기다려 응답한다 (PRD C2, ARCH 1.1). 큐 상한(`503`)은 운영자 명령에 적용하지 않는다 (ARCH 5.2).
- 응답의 `epoch`·`seq`는 그 명령이 만든 마지막 레인 이벤트를 가리킨다. 콘솔 화면도 입찰 화면과 같이 레인 이벤트로만 상태를 바꾼다. 응답은 "명령이 처리되었다"는 확인이다.
- 거절 응답에는 `error.lane`(`epoch`, `seq`)과 현재 상태(`error.laneStatus` 또는 `error.lotStatus`)를 넣는다.

#### `POST /api/admin/lanes/{laneId}/pause`, `POST /api/admin/lanes/{laneId}/resume`

**응답** `200`

```json
{ "laneId": 12, "laneStatus": "PAUSED", "epoch": "9c41e07a5b3d2f18", "seq": 61 }
```

**에러**: `404 LANE_NOT_FOUND`, `422 LANE_NOT_RUNNING`(정지 요청인데 `RUNNING`이 아님), `422 LANE_NOT_PAUSED`(재개 요청인데 `PAUSED`가 아님)

#### `POST /api/admin/sessions/{sessionId}/pause`, `POST /api/admin/sessions/{sessionId}/resume`

회차 일괄 정지·재개 (M-13). 대상 레인(정지: `RUNNING`, 재개: `PAUSED`)마다 명령을 넣고 레인별 결과를 모아 돌려준다 (ARCH B-14).

**응답** `200`

```json
{
  "sessionId": 7,
  "results": [
    { "laneId": 12, "laneNo": 1, "outcome": "APPLIED", "laneStatus": "PAUSED", "epoch": "9c41e07a5b3d2f18", "seq": 61 },
    { "laneId": 14, "laneNo": 3, "outcome": "SKIPPED", "laneStatus": "FINISHED", "epoch": "9c41e07a5b3d2f18", "seq": 120 }
  ]
}
```

- `outcome`: `APPLIED` 또는 `SKIPPED`. `FINISHED` 레인은 언제나 `SKIPPED`다 (AC-13-3).
- 개별로 정지해 둔 레인이 일괄 재개에 포함되는지는 [OQ-13]. 콘솔은 레인 상태의 `pauseCause`(EVENTS 3.2)로 개별 정지(`OPERATOR`)와 일괄 정지(`SESSION_BULK`), 재시작 복구 정지(`RECOVERY`)를 구분해 보여 준다. 어느 쪽으로 정해지든 콘솔이 "이 버튼을 누르면 어느 레인이 재개되는지"를 미리 보여 줄 수 있다.

**에러**: `404 SESSION_NOT_FOUND`, `422 SESSION_NOT_RUNNING`

#### `POST /api/admin/lots/{lotId}/force-close` — 강제 종료

M-14. 본문 없음.

**응답** `200`

```json
{ "lotId": 345, "laneId": 12, "lotStatus": "UNSOLD", "laneStatus": "PAUSED", "epoch": "9c41e07a5b3d2f18", "seq": 62 }
```

- 강제 종료한 Lot은 최고가와 관계없이 `UNSOLD`다 (AC-14-1).
- 정지 중인 레인의 마지막 Lot이면 레인이 바로 `FINISHED`가 되고, 이벤트가 2개(`LOT_CLOSED`, `LANE_FINISHED`) 나간다. 응답의 `seq`는 뒤의 것이다 (AC-14-4).
- `RUNNING` 레인에서 강제 종료한 뒤 다음 Lot이 간격 뒤에 자동으로 시작하는지는 [OQ-06].

**에러**: `404 LOT_NOT_FOUND`, `422 LOT_NOT_BIDDING`(대상이 `BIDDING`이 아님. `PENDING` Lot은 [OQ-41])

### 7.4 호가 정책

#### `GET /api/admin/bid-policies`

A-05. 버전 내림차순, 페이지 없음 (버전 수가 적다).

**응답** `200`

```json
{
  "items": [
    {
      "policyId": 1,
      "version": 1,
      "tiers": [
        { "minPrice": 0, "increment": 1000 },
        { "minPrice": 10000, "increment": 10000 },
        { "minPrice": 100000, "increment": 25000 }
      ],
      "createdAt": "2026-10-23T00:00:00.000Z",
      "createdByNickname": null,
      "isLatest": true
    }
  ]
}
```

- `isLatest`: 다음에 시작하는 회차에 고정될 버전 (AC-08-2). 진행 중인 회차는 시작 때의 버전을 쓴다.
- `createdByNickname`: 시드로 넣은 v1은 `null`.

#### `POST /api/admin/bid-policies`

**요청**: `{ "tiers": [ { "minPrice": 0, "increment": 1000 }, … ] }`

- 첫 구간의 `minPrice`는 0이어야 한다 (ARCH 4.4). `minPrice`는 엄격히 증가한다. `increment`는 1,000원 단위의 양수다.
- 버전 번호는 서버가 매긴다.

**응답** `201`: 목록 원소와 같은 모양. **에러**: `400 INVALID_REQUEST`(`fields`)

### 7.5 감사 로그 (Could, C-05)

#### `GET /api/admin/audit-logs`

**쿼리**: `targetType`(선택: `ITEM`, `SESSION`, `LANE`, `LOT`, `BID_POLICY`), `targetId`(선택), `size`, `cursor`. 정렬: 시각 내림차순.

**응답** `200`

```json
{
  "items": [
    {
      "id": 77,
      "adminNickname": "운영자",
      "action": "LANE_PAUSE",
      "targetType": "LANE",
      "targetId": 12,
      "detail": { "remainingMs": 7000 },
      "occurredAt": "2026-10-23T11:00:05.000Z"
    }
  ],
  "nextCursor": null
}
```

- `action` 값 (제안): `ITEM_APPROVE`, `ITEM_REJECT`, `SESSION_CREATE`, `SESSION_UPDATE`, `LINEUP_UPDATE`, `SESSION_START`, `SESSION_PAUSE`, `SESSION_RESUME`, `LANE_PAUSE`, `LANE_RESUME`, `LOT_FORCE_CLOSE`, `BID_POLICY_CREATE`. 자동 시작은 운영자 조치가 아니라 기록하지 않는다.
- `detail`은 `action`마다 다른 JSON이다. 화면은 보기용으로만 쓴다.

---

## 8. 공통 객체

여러 응답에 나오는 객체다.

| 객체 | 필드 | 나오는 곳 |
|---|---|---|
| `Member` | `memberId`, `email`, `nickname`, `role`, `bidSuspended`, `warningCount` | 3.1, 3.2, 3.3, 3.5 |
| `LotInfo` | `lotId`, `name`, `imageUrl`, `startPrice` | 4.3, EVENTS 3.4 |
| `LaneSnapshot` | 4.3 응답 전체 | 4.2, 4.3 |
| `LaneState` | EVENTS 3.2 | 4.3, EVENTS |
| `Notification` | `id`, `type`, `payload`, `createdAt`, `readAt` | 6.7, EVENTS 4 |
| `Timing` | `firstBidWaitMs`, `softCloseMs`, `lotGapMs`, `resumeMinMs` | 4.2, 7.2 |
| `ItemStage` | `PENDING_REVIEW`, `REJECTED`, `APPROVED`, `SCHEDULED`, `ON_AUCTION`, `SOLD`, `UNSOLD` | 6.5, 6.6, 7.1, 7.2 |

---

## 9. 에러 코드 전체 목록

| 코드 | HTTP | 뜻 | 나오는 곳 |
|---|---|---|---|
| `INVALID_REQUEST` | 400 | 형식 오류. `fields` 포함 | 전체 |
| `INVALID_BID_AMOUNT` | 400 | 입찰 금액 ≠ 다음 호가 | 5.1 |
| `UNSUPPORTED_IMAGE_TYPE` | 400 | 허용하지 않는 이미지 형식 (파일 시그니처 기준) | 6.4 |
| `UNAUTHENTICATED` | 401 | 토큰 없음·만료·위조, Refresh Token 무효 | 전체 |
| `INVALID_CREDENTIALS` | 401 | 로그인 실패 | 3.2 |
| `FORBIDDEN` | 403 | 역할이 맞지 않음 | 전체 |
| `BIDDER_SUSPENDED` | 403 | 입찰 정지 회원 | 5.1 |
| `SELF_BID_FORBIDDEN` | 403 | 본인 출품 Lot에 입찰 | 5.1 |
| `NOT_TRADE_BUYER` | 403 | 낙찰자가 아닌 회원의 결제 | 6.3 |
| `NOT_ITEM_SELLER` | 403 | 판매자가 아닌 회원의 재출품 | 6.6 |
| `NOT_FOUND` | 404 | 없는 경로 | 전체 |
| `SESSION_NOT_FOUND` | 404 | | 4.2, 7.2, 7.3 |
| `LANE_NOT_FOUND` | 404 | | 4.3, 5.1, 7.2, 7.3 |
| `LOT_NOT_FOUND` | 404 | | 4.4, 7.3 |
| `ITEM_NOT_FOUND` | 404 | | 6.6, 7.1, 7.2 |
| `TRADE_NOT_FOUND` | 404 | | 6.3 |
| `NOTIFICATION_NOT_FOUND` | 404 | | 6.8 |
| `IMAGE_NOT_FOUND` | 404 | | 4.5 |
| `EMAIL_ALREADY_EXISTS` | 409 | | 3.1 |
| `LOT_MISMATCH` | 409 | `lotId`가 지금 BIDDING Lot이 아님 | 5.1 |
| `PRICE_CHANGED` | 409 | 현재가가 바뀜 | 5.1 |
| `ALREADY_HIGHEST_BIDDER` | 409 | 이미 최고 입찰자 | 5.1 |
| `IMAGE_TOO_LARGE` | 413 | 이미지 한 장 또는 요청 전체가 상한 초과 | 6.4 |
| `IDEMPOTENCY_KEY_REUSED` | 422 | 처리한 키에 다른 요청 | 5.1, 6.3 |
| `LANE_NOT_RUNNING` | 422 | 레인이 `RUNNING`이 아님 | 5.1, 7.3 |
| `LANE_NOT_PAUSED` | 422 | 레인이 `PAUSED`가 아님 | 7.3 |
| `LOT_NOT_BIDDING` | 422 | Lot이 `BIDDING`이 아니거나 마감 시각이 지남 | 5.1, 7.3 |
| `TRADE_NOT_AWAITING_PAYMENT` | 422 | 결제 대기 거래가 아님 | 6.3 |
| `PAYMENT_DEADLINE_PASSED` | 422 | 결제 기한이 지남 | 6.3 |
| `RELIST_NOT_ALLOWED` | 422 | 재출품할 수 없는 Item | 6.6 |
| `ITEM_NOT_PENDING_REVIEW` | 422 | 이미 검수한 Item | 7.1 |
| `ITEM_NOT_ASSIGNABLE` | 422 | 편성할 수 없는 Item (`stage` 기준) | 7.2 |
| `SESSION_NOT_SCHEDULED` | 422 | 회차가 `SCHEDULED`가 아님 | 7.2 |
| `SCHEDULED_START_IN_PAST` | 422 | 예정 시각이 이미 지남 (PRD OQ-48) | 7.2 |
| `SESSION_NOT_RUNNING` | 422 | 회차가 `RUNNING`이 아님 | 7.3 |
| `SESSION_START_CONDITION_NOT_MET` | 422 | Lot이 없는 레인이 있음 | 7.2 |
| `INTERNAL_ERROR` | 500 | 예상하지 못한 서버 오류 | 전체 |
| `BID_RESULT_UNKNOWN` | 500 | 입찰 결과 미정 | 5.1 |
| `LANE_OVERLOADED` | 503 | 레인 큐 상한 | 5.1 |

---

## 10. 미결 사항이 계약에 주는 영향

응답 모양은 지금 고정하고, 결정되면 값의 의미나 이벤트 순서만 바뀌게 했다.

| 미결 | 지금 계약 | 결정되면 바뀌는 것 | 클라이언트가 지금 할 일 |
|---|---|---|---|
| [OQ-06] RUNNING 강제 종료 뒤 진행 | `LOT_CLOSED` 다음 이벤트가 정해지지 않음 | `LOT_STARTED`(자동 진행) 또는 `LANE_PAUSED`가 뒤따른다 | 이벤트를 순서대로 반영하기만 하면 어느 쪽이든 맞다 |
| [OQ-11] 복구 재개 시 입찰 0건 Lot | 재개 뒤 `deadlineAt`이 알려 준다 | 값만 | 없음 |
| [OQ-13] 일괄 재개 범위 | `results[].outcome`, 레인 상태의 `pauseCause` | 개별 정지 레인이 `SKIPPED`일 수 있다 | `results`를 그대로 표시. 콘솔은 `pauseCause`로 정지 원인을 보여 준다 |
| [OQ-14] 시간 파라미터 범위 | 1 이상만 검사 | `400`(`OUT_OF_RANGE`), 시작 직전 변경 금지면 `422` | 필드 에러 표시 |
| [OQ-22] 최저가 미달 배지 | `reserveNotMet` 불리언 | 값이 `true`가 되는 조건 | 값대로 배지 표시 |
| [OQ-25] 패찰 정의 | `result`의 `LOST`, `UNSOLD` | 화면 문구, 알림 종류가 늘 수 있다 | 문구를 한곳에 모아 둔다 |
| [OQ-26] 입찰 정지 | `BIDDER_SUSPENDED` | 해제 시각 필드가 더해질 수 있다 | 없음 |
| [OQ-28] 출품물 수정·중복 편성·재출품 가격 | 재출품 본문 없음. 다른 곳에 편성된 Item도 받는다 | 재출품 본문(`startPrice`, `reservePrice`)이 더해질 수 있다. 중복 편성을 막으면 `ITEM_NOT_ASSIGNABLE`의 범위가 넓어진다 | 편성 후보는 `stage=APPROVED`만 보여 준다 |
| [OQ-36] 결제 기한 경계 | `PAYMENT_DEADLINE_PASSED` | 판정 기준 시각 | 기한이 지나면 버튼 비활성화 |
| [OQ-38] 놓친 예정 시각 | 예정 시각이 지나도 `SCHEDULED`일 수 있다 | 판정 기준 | 4.2의 `status`대로 표시 |
| [OQ-41] PENDING Lot 강제 종료 | `422 LOT_NOT_BIDDING` | 허용하면 새 이벤트가 필요하다 | 없음 |
| [B-4] 회차 이름 | 없음 | `title` 필드가 더해질 수 있다 | 예정 시각으로 부른다 |
| [B-6] 이미지 상한 | 10장, 장당 10MB, JPEG·PNG·WebP (제안) | 값 | 폼에서 미리 검사 |
| [B-7] 판매자·운영자에게 최저 낙찰가 | 어디에도 없음 | `/api/me/items`, `/api/admin/items/{id}`에 `reservePrice`가 더해질 수 있다 | 없음 |

---

## 11. 계약을 쓰며 찾은 질문

0.2에서 PRD·ARCHITECTURE에 올린 질문이다. 모두 PRD 0.5에서 이 문서의 제안대로 결정되었다 (2026-10-23).

| ID | PRD | 질문 | 결정 | 왜 문제였나 |
|---|---|---|---|---|
| CQ-01 | OQ-45 | 로그아웃 API를 두는가? | 둔다: `POST /api/auth/logout` (3.4). M-01, AC-01-5 | ARCH 0.2의 B-1은 "클라이언트가 토큰을 버린다"로 로그아웃을 대신했다. 그런데 ARCH 11은 Refresh Token을 `HttpOnly` 쿠키에 둔다. JS는 이 쿠키를 지울 수 없으므로, 로그아웃 버튼을 눌러도 새로고침하면 다시 로그인된다. 두 결정이 함께 성립하지 않았다 |
| CQ-02 | OQ-46 | 출품물이 낙찰·유찰된 뒤 "편성 가능"을 어떻게 판정하는가? | 서버가 `stage`를 계산한다 (6.5). 편성은 `APPROVED`와 같은 레인의 `SCHEDULED`만 받는다. 단계는 컬럼 없이 `item.reviewed_at`과 `lot.created_at`으로 계산한다 (ARCH 4.4). AC-05-4 | `item.review_status`는 경매 결과와 무관하게 `APPROVED`로 남는다. ARCH 0.2의 `?reviewStatus=APPROVED`로 편성 후보를 거르면 낙찰된 출품물이 다시 경매에 올라갈 수 있었다. 편성 API도 "승인됨"만 검사하면 같은 일이 생긴다 |
| CQ-03 | OQ-47 | 진행 중·종료된 회차의 지난 Lot 결과 목록을 보여 주는가? | 이번 범위에서 뺀다 (PRD 5장 Non-goals) | PRD P-03은 현재 Lot만 요구한다. 종료된 회차(P-01)를 열면 레인마다 마지막 Lot만 보인다. 다시 넣는다면 `GET /api/sessions/{id}/lots`(Lot별 순번, 상태, 최종가) 하나로 된다 |
| CQ-04 | OQ-48 | 지난 시각을 예정 시각으로 넣으면 거절하는가? | 거절한다: `422 SCHEDULED_START_IN_PAST` (7.2). AC-04-6 | PRD에 규칙이 없었다. 받아 주면 그 회차는 놓친 회차가 되어 자동으로 시작하지 않는다 (AC-06-4). [OQ-38]은 미결로 남지만, 처음부터 지난 시각인 회차는 생기지 않는다 |

---

## 12. PRD·ARCHITECTURE와 달라진 점

계약을 확정하면서 바꾸거나 더한 것이다. 모두 PRD 0.5와 ARCHITECTURE 0.3에 반영했다. 반영 뒤에는 원래 문서와 이 문서가 같은 말을 한다. 형식이 다르면 계약 문서가 우선이다 (ARCH 머리말).

| 문서와 절 | 원래 (PRD 0.4, ARCH 0.2) | 이 계약 | 반영한 곳 |
|---|---|---|---|
| ARCH 6.5 에러 응답 | `code`, `message`, `lane`이 최상위 | `{ "error": { "code", "message", … } }` (1.4) | ARCH 6.5, PRD 6.3 |
| ARCH 2.7 엔드포인트 | — | `POST /api/auth/logout`, `GET /api/me`, `GET /api/admin/items/{id}`, `GET /api/admin/sessions/{id}`를 더함 | ARCH 2.7 |
| ARCH 2.7 검수·편성 목록 | `GET /api/admin/items?reviewStatus=` | `?stage=` (7.1, PRD OQ-46) | ARCH 2.7, 4.4, 4.5 |
| ARCH 7.2 메시지 봉투 | `state`에 간격 종료 시각과 정지 원인이 없음 | `state.gapEndsAt`, `state.pauseCause`를 더함 (EVENTS 3.2) | ARCH 7.2, 7.3 |
| ARCH 7.6 동기화 절차 | 구독 → 회차 상세 | 첫 진입은 회차 상세 → 구독 → 회차 상세 (EVENTS 6.1). 구독하려면 `laneId`를 먼저 알아야 한다 | ARCH 7.5, 7.6 |
| ARCH 7.7 시계 보정 | 표본은 레인 메시지와 스냅샷 | 회차 목록, 회차 상세, 내 거래 응답에도 `serverTime`을 넣음 | ARCH 7.7 |
| ARCH 11 Refresh Token | 회전, 재사용 유예 없음. 쿠키 경로 `/api/auth/refresh` | 클라이언트가 탭 사이에서 재발급을 직렬화한다 (1.3). 로그아웃이 쿠키를 받도록 경로를 `/api/auth`로 넓힘 | ARCH 11 |
| PRD 6.1 운영자 API 에러 코드 | "1주차 OpenAPI 계약에서 정한다" | 7장과 9장 | PRD 6.1, ARCH 5.3 |
| CQ-01~04 | — | 11장 | PRD OQ-45~48, M-01, M-05, 5장, 6.1, AC-01-5, AC-04-6, AC-05-4, ARCH 부록 B (B-1, B-13, B-14 해소) |

---

## 부록 A. 화면별 데이터 출처

PRD 8.1의 화면마다 무엇을 어디서 읽는지 적었다. 프론트 관점 검토(부록 B)의 점검표이기도 하다.

| 화면 | 화면 요소 (PRD 8.1, AC-12) | 읽는 곳 |
|---|---|---|
| 공통 | 로그인 상태, 닉네임, 역할별 메뉴, 알림 배지 | 3.3(앱 시작), 3.5, 6.7 `unreadCount`, EVENTS 4 |
| P-01 홈 | 진행 중·예정·종료 회차, 시작 시각, 시작까지 남은 시간 | 4.1 × 3 (`status`별), `serverTime` |
| P-02 로그인·가입 | 폼, 필드 에러 | 3.1, 3.2, 1.4 `fields` |
| P-03 관전 | 레인 패널(이미지·이름, 현재가, 다음 호가, 남은 시간, 마스킹 최고 입찰자, 최저가 미달, 레인 상태, N번째/남은 수) | 4.2 → EVENTS 3.2 `state`, `lotInfo` |
| | 입찰 버튼, "내가 최고 입찰자", 로그인 유도, 본인 출품 비활성화 | 5.1, 4.2 `me.myLotIds`, 3.5 `bidSuspended`, EVENTS 부록 B |
| | 결과 표시, 다음 Lot 미리보기, 간격 카운트다운 | EVENTS 3.4 `LOT_CLOSED`, `state.gapEndsAt`, `nextLotInfo` |
| | 정지 표시, "재개하면 N초" | EVENTS 3.2 `pauseCause`, `pausedRemainingMs`, 4.2 `timing.resumeMinMs` |
| | 낙찰·패찰 표시 | EVENTS 부록 B.4 |
| | Lot 상세 모달 | 4.4 |
| | 예정 회차: 시작까지 남은 시간, 편성 미리보기 | 4.2 `lineup`, `scheduledStartAt`, EVENTS 6.7 |
| U-01 출품 등록 | 이미지, 이름·설명, 시작가, 최저 낙찰가 | 6.4 |
| U-02 내 출품 | 검수 상태, 반려 사유, 경매 결과, 재출품 버튼 | 6.5 `stage`, `currentLot`, 6.6 |
| U-03 내 입찰 내역 | Lot별 내 최고 입찰가, 낙찰·패찰·진행 중 | 6.1 `result` |
| U-04 거래·결제 | 결제 대기 거래, 기한, 결제 버튼, 상태 | 6.2, 6.3, `serverTime` |
| U-05 알림함 | 목록, 읽음 처리, 눌러서 이동 | 6.7, 6.8, EVENTS 4.2 |
| A-01 검수 | 대기 목록, 이미지·정보, 승인·반려 | 7.1 (`stage=PENDING_REVIEW`) |
| A-02 회차 관리 | 회차 목록, 생성, 예정 시각 변경 | 4.1, 7.2 |
| A-03 편성 | 편성 후보, 레인별 순번, 시작 조건, 잠금 | 7.1 (`stage=APPROVED`), 7.2 `startable`, `emptyLaneNos`, `session.status` |
| A-04 진행 제어 | 지금 시작, 레인별 상태·현재 Lot·현재가·남은 시간, 정지·재개, 일괄, 강제 종료 | 4.2 + EVENTS(P-03과 같다), 7.2 start, 7.3 |
| A-05 호가 정책 | 버전 목록, 새 버전 | 7.4 |
| A-06 감사 로그 | 조치 이력 | 7.5 |

---

## 부록 B. 프론트 관점 검토

0.1 초안(이 문서와 EVENTS.md)을 "이 두 문서만 보고 화면을 만들 수 있는가"로 검토했다. 부록 A의 화면을 하나씩 그려 보며, 필요한 값이 어디서 오는지와 상태가 바뀌는 경로를 따라갔다. 0.2에서 보완한 것이다.

### B.1 화면을 만들 수 없던 것

| # | 화면 | 빠진 것 | 보완 |
|---|---|---|---|
| F-01 | A-03, U-02 | 편성 후보와 출품물 상태를 판정할 값이 없었다. 검수 상태는 낙찰·유찰 뒤에도 `APPROVED`라, 후보 목록에 낙찰된 출품물이 섞이고 편성 API도 그것을 받았다. U-02는 "유찰"과 "재승인되어 편성 대기"를 구분할 수 없었다 | `stage` (6.5), `?stage=` (7.1), `ITEM_NOT_ASSIGNABLE` (7.2), CQ-02 → PRD OQ-46 |
| F-02 | 공통 | 로그아웃할 수 없었다 (HttpOnly 쿠키) | 3.4, CQ-01 → PRD OQ-45 |
| F-03 | P-03 | 첫 진입 때 구독할 `laneId`를 모르는데 절차는 "구독 먼저"였다 | EVENTS 6.1을 두 단계로 |
| F-04 | P-03 | Lot 간 간격(5초)의 카운트다운을 그릴 값이 없었다 | `state.gapEndsAt` (EVENTS 3.2) |
| F-05 | A-04 | 정지 원인을 알 수 없었다. 재시작으로 정지된 레인과 운영자가 정지한 레인, 일괄 재개 대상([OQ-13])을 구분하지 못했다 | `state.pauseCause` (EVENTS 3.2) |
| F-06 | 공통 | 알림 배지의 미읽음 수가 없었다 | `unreadCount` (6.7) |
| F-07 | P-03 | 입찰 정지 상태를 미리 알 수 없어, 누른 뒤에야 `BIDDER_SUSPENDED`로 알았다 | `Member.bidSuspended`, `GET /api/me` (3.5) |
| F-08 | U-04 | 결제 기한 카운트다운에 쓸 시계 보정 표본이 없었다 (레인 구독이 없는 화면) | `serverTime` (6.2) |

### B.2 만들 수는 있지만 구현마다 달라질 것

| # | 내용 | 보완 |
|---|---|---|
| F-09 | "내가 최고 입찰자", 입찰 버튼 활성화, 낙찰·패찰 표시의 판정이 API·EVENTS·ARCH에 흩어져 있었다. 특히 스냅샷의 `me.isHighestBidder`를 이벤트가 이어서 갱신하지 않는다는 점이 빠져 있었다 | EVENTS 부록 B로 모음 |
| F-10 | 연속 클릭으로 키가 둘 생기는 경우, 409 뒤 기다리는 동안, 카운트다운 0, 연결 끊김 때의 버튼 상태가 정해지지 않았다 | EVENTS 부록 B.3 |
| F-11 | 예정 회차의 편성·예정 시각이 바뀌어도 화면이 모른다. 예정 시각이 지나도 시작하지 않는 회차의 표시도 없었다 | 4.1, 4.2, EVENTS 6.7 |
| F-12 | 여러 탭이 동시에 재발급하면 한 탭이 로그아웃된다 | 1.3 "여러 탭의 재발급" |
| F-13 | 만료된 토큰으로 공개 API를 부르면 무엇이 오는지 없었다 | 1.3 |
| F-14 | 이미지 장수·크기·형식 상한이 없어 폼이 미리 검사하지 못했다 | 6.4 (제안 값) |
| F-15 | 재전송 창이 닫힌 뒤 `me.isHighestBidder = false`를 실패로 볼지 모호했다 | 5.1 |
| F-16 | 지난 시각으로 회차를 만들면 아무 신호 없이 자동 시작되지 않는다 | `SCHEDULED_START_IN_PAST` (7.2), CQ-04 → PRD OQ-48 |
| F-17 | 비로그인 사용자가 언제 WebSocket을 연결하는지, ADMIN이 알림함을 부르는지가 없었다 | EVENTS 1.1, 6.1 |
| F-18 | 재동기화 뒤에는 `LOT_CLOSED.result`가 없는데 "결과 표시는 `result`로"라고 적혀 있었다 | EVENTS 3.4: 결과는 `state.lot`으로 그린다 |

### B.3 검토하고 그대로 둔 것

| 내용 | 이유 |
|---|---|
| 종료 회차의 지난 Lot 결과 목록 | PRD 화면 요구에 없다. CQ-03으로 올렸고, PRD OQ-47에서 범위 밖으로 결정되었다 |
| 새로고침한 뒤 간격 중 "패찰" 표시 | 그 Lot에 내가 입찰했는지는 탭이 기억하는 값으로만 안다. 새로고침하면 낙찰(`me.isHighestBidder`)만 보이고 패찰은 U-03에서 본다. 스냅샷에 넣으면 스냅샷마다 DB 조회가 생겨 재연결 폭주에 약해진다 (ARCH 7.5) |
| 썸네일 크기 이미지 | PRD에 없다. 원본을 그대로 쓴다 |
| "모두 읽음" | PRD U-05는 읽음 처리만 요구한다 |
| 회차 이름 | [B-4]. 예정 시각으로 부른다 |

