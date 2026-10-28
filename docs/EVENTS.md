# Four-Lane Auction — 실시간 이벤트 계약 (WebSocket / STOMP)

| 항목 | 내용 |
|---|---|
| 버전 | 0.4 (초안) |
| 작성일 | 2026-10-23 |
| 변경 이력 | 0.4 M1-08 승인: 예정 상세 60초, 0초/10초 대기 문구, READY 배지 false와 단계별 구현 범위 명시. 이벤트 필드·종류 변경 없음 / 0.1 최초 작성 / 0.2 프론트 관점 검토 반영 ([API.md 부록 B](API.md#부록-b-프론트-관점-검토)): `state.gapEndsAt`·`state.pauseCause` 추가, 첫 진입 절차를 두 단계로, 예정 회차 화면 규칙(6.7), 연결 끊김 표시, 연결 시점, 화면 상태 계산([부록 B](#부록-b-화면-상태-계산)) / 0.3 0.2의 변경(`gapEndsAt`, `pauseCause`, 첫 진입 절차, 시계 보정 표본)을 PRD 0.5·ARCHITECTURE 0.3에 반영함. 메시지 모양은 바뀌지 않음 |
| 근거 문서 | [PRD.md](PRD.md) 0.5, [ARCHITECTURE.md](ARCHITECTURE.md) 0.3 (7장, 8장), [CHALLENGES.md](CHALLENGES.md) 0.2 (CH-01, CH-07, CH-08) |
| 짝 문서 | [API.md](API.md) (REST 계약) |
| 상태 | 초안. 1주차 계약이다. springdoc는 STOMP 메시지를 문서화하지 않으므로 이 문서가 메시지 스키마의 원본이다 (ARCH 1.3) |

---

## 0. 이 문서를 읽는 법

- 표기(`(제안)`, `[OQ-nn]`, `[B-nn]`, 절 번호)와 값 형식(시각, 금액, ID, 마스킹)은 [API.md 0장, 1.2](API.md#12-값-형식)와 같다. `API 4.3`은 짝 문서의 절이다.
- 하위 호환 규칙도 API.md 0장과 같다. 클라이언트는 모르는 필드를 무시하고, 모르는 `type`의 메시지는 버린다 (ARCH 7.3).
- **메시지는 서버 → 클라이언트 방향으로만 흐른다.** 입찰과 운영자 명령은 REST다 (ARCH 2.1).

---

## 1. 연결

### 1.1 엔드포인트

| 항목 | 값 |
|---|---|
| URL | `wss://<호스트>/ws`. 개발 중에는 `ws://localhost:<Vite 포트>/ws`이고 Vite 프록시가 백엔드로 넘긴다 (ARCH 1.4). 프록시 설정에 `ws: true`가 필요하다 |
| 전송 | 순수 WebSocket. SockJS를 쓰지 않는다 (ARCH 7.1) |
| 프로토콜 | STOMP 1.2 |
| 클라이언트 라이브러리 | `@stomp/stompjs` (ARCH 1.3) |
| 연결 수 | 탭당 1개. 레인 여러 개와 개인 큐를 한 연결에서 구독한다 |
| 연결 시점 (제안) | **USER로 로그인했으면** 앱이 떠 있는 동안 연결을 유지한다. 어느 화면에서든 개인 알림을 받기 위해서다. **비로그인과 ADMIN은** 관전 화면(P-03)이나 진행 제어 콘솔(A-04)에 있을 때만 연결하고, 떠나면 끊는다. 연결 5,000개 목표(PRD 9.2)에 홈 화면의 빈 연결을 더하지 않기 위해서다 |
| 출처 | 허용한 Origin의 핸드셰이크만 받는다 (ARCH 8.1, 11) |

- 같은 회원이 탭을 여러 개 열면 연결도 여러 개다. 개인 알림은 그 회원의 모든 연결로 간다.

- 핸드셰이크(`GET /ws` Upgrade)에는 토큰을 싣지 않는다. URL 쿼리에도 넣지 않는다. 접근 로그에 남기 때문이다 (ARCH 8.1).

### 1.2 CONNECT

```
CONNECT
accept-version:1.2
host:auction.example.com
heart-beat:10000,10000
Authorization:Bearer eyJhbGciOi…

^@
```

| 헤더 | 값 |
|---|---|
| `Authorization` | 로그인 상태면 `Bearer <accessToken>`. **비로그인이면 헤더를 아예 넣지 않는다.** 빈 값이나 `Bearer `만 넣으면 형식 오류로 거절된다 |
| `heart-beat` | `10000,10000` (제안, ARCH 7.8). STOMP 하트비트는 연결이 살아 있는지만 알린다. 레인 메시지 유실은 레인 비컨이 알린다 (5.4) |

- `@stomp/stompjs`에서는 `connectHeaders`에 넣는다. 재연결 때도 최신 토큰을 넣어야 하므로 `beforeConnect`에서 토큰을 확인해 헤더를 채운다. 토큰이 만료되었거나 60초 안에 만료되면 먼저 재발급한다 (API 3.3, PRD 9.4-1).

### 1.3 연결 결과

| CONNECT | 서버 | 클라이언트 |
|---|---|---|
| `Authorization` 없음 | `CONNECTED`. 익명 연결 | 레인 토픽만 구독한다 |
| 유효한 Access Token | `CONNECTED`. 연결의 신원 = 그 회원 | 레인 토픽과 개인 큐를 구독한다 |
| 만료된 토큰 | `ERROR`(`message:TOKEN_EXPIRED`) 뒤 연결 종료 | 재발급 → 새 토큰으로 다시 연결. 재발급이 실패하면 비로그인 상태로 바꾸고 토큰 없이 연결 |
| 위조·형식 오류 토큰 | `ERROR`(`message:TOKEN_INVALID`) 뒤 연결 종료 | 재발급을 한 번 시도한다. 실패하면 비로그인 상태로 토큰 없이 연결 |

- 만료되었거나 잘못된 토큰을 익명 연결로 낮춰 받지 않는다 (OQ-39). 낮춰 받으면 사용자는 개인 알림이 끊긴 것을 모른다.
- `ERROR`를 받은 뒤 같은 토큰으로 다시 CONNECT하지 않는다. 재연결 루프에 빠진다.
- `message` 헤더 값 `TOKEN_EXPIRED`, `TOKEN_INVALID`는 (제안)이다 (ARCH 8.2).

### 1.4 구독 허용 범위 (비로그인 관전)

| 목적지 | 익명 연결 | 인증된 연결 |
|---|:-:|:-:|
| `/topic/lanes/{laneId}` | O | O |
| `/user/queue/notifications` | X | O |
| 그 밖의 모든 목적지 (`/queue/**` 직접 구독 등) | X | X |

- 비로그인 사용자는 모든 회차의 모든 레인을 실시간으로 관전할 수 있다 (PRD 2.2, AC-11-1). REST의 공개 조회(API 4장)와 합치면 관전 화면 전체를 그릴 수 있다. 입찰, 개인 알림, `me` 정보만 없다.
- 거절된 SUBSCRIBE에는 서버가 `ERROR`(`message:SUBSCRIPTION_DENIED`, 제안)를 보내고 연결을 닫는다 (ARCH 8.2). 정상 클라이언트에서는 일어나지 않는 일이므로 클라이언트 버그로 본다. 그 구독을 빼고 다시 연결한다.
- 개인 큐는 USER만 구독한다. ADMIN은 받을 알림 종류가 없다 (4.2). 구독 자체는 거절되지 않는다.

### 1.5 클라이언트가 보내는 프레임

| 프레임 | 쓰임 |
|---|---|
| `CONNECT` | 1.2 |
| `SUBSCRIBE` | `id`는 레인이면 `lane-{laneId}`, 개인 큐면 `notifications` (제안). `receipt`는 요청하지 않는다 (ARCH 7.4) |
| `UNSUBSCRIBE` | 관전 화면을 떠날 때 |
| `DISCONNECT` | 로그아웃, 앱 종료 |
| `SEND` | **쓰지 않는다.** 모든 목적지에서 거절된다 (ARCH 8.2) |

- 서버의 수신 프레임 크기 상한은 작다 (제안: 8KB, ARCH 7.8).

### 1.6 연결 중 토큰과 신원

- 연결 중에 Access Token이 만료되어도 연결과 개인 큐 구독은 유지된다 (OQ-39, AC-11-6). 이 때문에 재연결할 필요가 없다. 입찰은 REST라 요청마다 토큰을 검증한다.
- 연결의 신원은 CONNECT 때 정해지고 바뀌지 않는다. 로그인하거나 로그아웃하면 연결을 끊고 새 신원으로 다시 연결한다.

---

## 2. 목적지

| 목적지 | 메시지 | 절 |
|---|---|---|
| `/topic/lanes/{laneId}` | 레인 이벤트 6종 + 레인 비컨 | 3 |
| `/user/queue/notifications` | 개인 알림 | 4 |

- 회차 단위 토픽은 없다 (ARCH 7.1). 회차 시작은 레인마다 오는 첫 `LOT_STARTED`로, 회차 종료는 모든 레인의 `LANE_FINISHED`로 안다. 관전 화면은 예정 회차에서도 레인 토픽을 미리 구독해 두므로 시작하는 순간을 받는다.
- 메시지 본문은 JSON(`content-type:application/json`)이다.

---

## 3. 레인 이벤트 (`/topic/lanes/{laneId}`)

### 3.1 봉투

모든 레인 이벤트에 공통인 필드다 (ARCH 7.2). 비컨(`HEARTBEAT`)은 일부만 싣는다.

| 필드 | 타입 | `HEARTBEAT` | 뜻 |
|---|---|:-:|---|
| `type` | string | O | 이벤트 종류 (3.4) |
| `laneId` | number | O | |
| `epoch` | string | O | 서버가 부팅할 때마다 바뀌는 값. 같은지만 비교하고 의미는 해석하지 않는다 (5.1) |
| `seq` | number | O | 레인별로 epoch 안에서 1씩 증가하는 번호 (5.2). 비컨은 마지막 상태 이벤트의 값을 그대로 싣는다 |
| `occurredAt` | 시각 | X | 이 이벤트를 만든 트랜잭션이 커밋된 직후의 서버 시각. 브로드캐스트 지연(커밋 → 수신, PRD 9.1)을 재는 기준이다. 화면 표시에는 쓰지 않는다 |
| `serverTime` | 시각 | O | 메시지를 만든 서버 시각. 시계 보정 표본이다 (7) |
| `state` | `LaneState` | X | 이 이벤트를 반영한 뒤의 레인 동적 상태 전체 (3.2). 메시지 하나만 받아도 동적 상태는 맞다 |

이벤트마다 더 붙는 필드는 3.4에 있다.

### 3.2 `LaneState`

```json
{
  "laneStatus": "RUNNING",
  "position": 3,
  "remainingLots": 9,
  "gapEndsAt": null,
  "pauseCause": null,
  "lot": {
    "lotId": 345,
    "status": "BIDDING",
    "currentPrice": 60000,
    "nextPrice": 70000,
    "bidCount": 2,
    "lastBidId": 90817,
    "deadlineAt": "2026-10-23T11:00:13.120Z",
    "pausedRemainingMs": null,
    "highestBidder": "송**",
    "reserveNotMet": true
  }
}
```

| 필드 | 타입 | 뜻 |
|---|---|---|
| `laneStatus` | `READY` \| `RUNNING` \| `PAUSED` \| `FINISHED` | 레인 상태 (PRD 6.1) |
| `position` | number | 현재 Lot의 순번(1부터). "현재 N번째" (T3). Lot 간 간격 중에는 방금 끝난 Lot의 순번이다. `lot`이 `null`이면 0 |
| `remainingLots` | number | 현재 Lot 뒤에 남은 Lot 수. "남은 Lot 수" (T3). 전체 Lot 수는 `position + remainingLots`. `lot`이 `null`이면 0 |
| `gapEndsAt` | 시각 \| null | Lot 간 간격이 끝나는 시각. 이 시각에 다음 Lot이 열리거나(T4) 레인이 끝난다(마지막 Lot 뒤). 레인이 `RUNNING`이고 간격 단계일 때만 값이 있다. 간격 중 정지하면 `null`이 되고, 재개하면 재개 시각 + 5초로 다시 잡힌다 (OQ-10). (0.2에서 더함) |
| `pauseCause` | `OPERATOR` \| `SESSION_BULK` \| `RECOVERY` \| null | 정지 원인. 레인이 `PAUSED`일 때만 값이 있다. `OPERATOR`: 운영자의 레인 정지, `SESSION_BULK`: 회차 일괄 정지, `RECOVERY`: 서버 재시작 복구 (ARCH 4.4 `lane.pause_cause`). 재개 규칙이 원인에 따라 다르다(T5, T6). 회원 정보는 없다. (0.2에서 더함) |
| `lot` | object \| null | 현재 Lot의 동적 상태. 편성된 Lot이 없는 `READY` 레인이면 `null` |
| `lot.lotId` | number | 입찰 요청의 `lotId` |
| `lot.status` | `PENDING` \| `BIDDING` \| `SOLD` \| `UNSOLD` | Lot 상태 (PRD 6.2) |
| `lot.currentPrice` | number | 현재가. 입찰이 없으면 시작가. 입찰 요청의 `expectedPrice` |
| `lot.nextPrice` | number \| null | 다음 호가. 서버가 회차에 고정된 정책으로 계산한다. 입찰 요청의 `amount`. `BIDDING`이 아니면 `null` |
| `lot.bidCount` | number | 수락된 입찰 수. 0이면 첫 입찰 대기 단계다 (OQ-01) |
| `lot.lastBidId` | number \| null | 마지막으로 수락된 입찰의 ID. 입찰이 없으면 `null`. 입찰 성공 응답의 `bidId`와 비교한다 (ARCH 2.5) |
| `lot.deadlineAt` | 시각 \| null | 마감 시각(절대 시각). 레인이 `RUNNING`이고 Lot이 `BIDDING`일 때만 값이 있다 |
| `lot.pausedRemainingMs` | number \| null | 입찰 단계에서 정지할 때 저장한 남은 시간 (T5). 그 밖에는 `null` |
| `lot.highestBidder` | string \| null | 최고 입찰자의 마스킹 닉네임. 입찰이 없으면 `null`. 회원 ID는 없다 (PRD 9.6) |
| `lot.reserveNotMet` | boolean | "최저가 미달" 배지. `true`가 되는 조건은 [OQ-22]다. 최저 낙찰가 값은 어디에도 없다 |

- Lot의 정적 정보(이름, 이미지, 시작가)는 매 메시지에 싣지 않는다. `LOT_STARTED`의 `lotInfo`와 스냅샷의 `lotInfo`에만 있다 (ARCH 7.2).

### 3.3 레인 단계별 `state` 모양

엔진은 `RUNNING`을 입찰 단계와 간격 단계로 나눠 다룬다 (ARCH 5.4). 메시지에는 단계 필드가 없고, `laneStatus`와 `lot.status`로 가른다.

| 단계 | `laneStatus` | `lot` | `lot.status` | `deadlineAt` | `gapEndsAt` | `pausedRemainingMs` | `pauseCause` | `nextPrice` | 입찰 |
|---|---|---|---|---|---|---|---|---|---|
| 편성 전 | `READY` | `null` | — | — | `null` | — | `null` | — | 불가 |
| 시작 전 | `READY` | 1번 Lot | `PENDING` | `null` | `null` | `null` | `null` | `null` | 불가 (`LANE_NOT_RUNNING`) |
| 입찰 중 | `RUNNING` | 현재 Lot | `BIDDING` | 값 | `null` | `null` | `null` | 값 | 가능 |
| 간격 | `RUNNING` | 방금 끝난 Lot | `SOLD` / `UNSOLD` | `null` | 값 | `null` | `null` | `null` | 불가 (`LOT_NOT_BIDDING`) |
| 입찰 중 정지 | `PAUSED` | 현재 Lot | `BIDDING` | `null` | `null` | 값. `RECOVERY`면 `null` | 값 | 값 | 불가 (`LANE_NOT_RUNNING`) |
| 간격 중 정지 | `PAUSED` | 방금 끝난 Lot | `SOLD` / `UNSOLD` | `null` | `null` | `null` | 값 | `null` | 불가 (`LANE_NOT_RUNNING`) |
| 종료 | `FINISHED` | 마지막 Lot | `SOLD` / `UNSOLD` | `null` | `null` | `null` | `null` | `null` | 불가 (`LANE_NOT_RUNNING`) |

- 단계를 가르는 함수는 부록 B.1에 있다.

- "시작 전"의 `nextPrice`가 `null`인 이유: 호가 정책은 회차가 시작하는 순간 고정된다 (AC-06-5). 그 전에는 다음 호가가 확정되지 않는다.
- 간격·종료 단계의 `lot`은 끝난 Lot이므로 `currentPrice`가 최종가, `highestBidder`가 낙찰자(SOLD) 또는 마지막 최고 입찰자(UNSOLD)다.

### 3.4 이벤트 종류

| `type` | 언제 | 추가 필드 | 반영 뒤 단계 |
|---|---|---|---|
| `LOT_STARTED` | 회차 시작 때 레인마다 1번 Lot (seq 1). 간격이 끝나 다음 Lot이 열릴 때 | `lotInfo` | 입찰 중 |
| `BID_ACCEPTED` | 입찰 수락 | 없음 | 입찰 중 |
| `LOT_CLOSED` | 마감 판정, 강제 종료 | `result`, `nextLotInfo` | 간격, 간격 중 정지(정지 중 강제 종료) |
| `LANE_PAUSED` | 레인 정지, 회차 일괄 정지 | 없음 | 입찰 중 정지, 간격 중 정지 |
| `LANE_RESUMED` | 레인 재개, 회차 일괄 재개 | 없음 | 입찰 중, 간격 |
| `LANE_FINISHED` | 마지막 Lot 뒤 간격이 끝남, 정지 중 마지막 Lot 강제 종료 | 없음 | 종료 |
| `HEARTBEAT` | `RUNNING`·`PAUSED` 레인에서 비컨 주기마다 | 상태 이벤트가 아니다 (5.4) | 바뀌지 않음 |

- 거절된 입찰은 이벤트를 만들지 않고 `seq`도 바꾸지 않는다 (AC-09-3).
- 서버 재시작 복구로 생긴 정지는 이벤트로 나가지 않는다. epoch가 바뀌므로 클라이언트가 스냅샷을 받아 정지를 본다 (ARCH 5.5.9).
- 유찰 사유(입찰 없음 / 최저가 미달 / 강제 종료)는 싣지 않는다. 입찰자에게 무엇을 보여 줄지는 [OQ-25]다.

#### `LOT_STARTED`

```json
{
  "type": "LOT_STARTED",
  "laneId": 12,
  "epoch": "9c41e07a5b3d2f18",
  "seq": 41,
  "occurredAt": "2026-10-23T11:00:00.004Z",
  "serverTime": "2026-10-23T11:00:00.006Z",
  "state": {
    "laneStatus": "RUNNING",
    "position": 3,
    "remainingLots": 9,
    "gapEndsAt": null,
    "pauseCause": null,
    "lot": {
      "lotId": 345, "status": "BIDDING", "currentPrice": 50000, "nextPrice": 60000,
      "bidCount": 0, "lastBidId": null, "deadlineAt": "2026-10-23T11:00:15.004Z",
      "pausedRemainingMs": null, "highestBidder": null, "reserveNotMet": false
    }
  },
  "lotInfo": { "lotId": 345, "name": "필름 카메라", "imageUrl": "/api/images/9f3c0d…", "startPrice": 50000 }
}
```

| 추가 필드 | 뜻 |
|---|---|
| `lotInfo` | 새로 열린 Lot의 정적 정보. `LotInfo`: `lotId`, `name`, `imageUrl`, `startPrice` (API 8장) |

- `deadlineAt` = Lot이 열린 시각 + 첫 입찰 대기 시간(기본 15초) (T1).

#### `BID_ACCEPTED`

추가 필드 없음. `state.lot`의 `currentPrice`, `nextPrice`, `bidCount`, `lastBidId`, `deadlineAt`, `highestBidder`, `reserveNotMet`이 바뀐다.

- `deadlineAt` = max(이전 마감, 수락 시각 + 10초) (T2). 늦춰지기만 하고 당겨지지 않는다.

#### `LOT_CLOSED`

```json
{
  "type": "LOT_CLOSED",
  "laneId": 12,
  "epoch": "9c41e07a5b3d2f18",
  "seq": 47,
  "occurredAt": "2026-10-23T11:00:31.208Z",
  "serverTime": "2026-10-23T11:00:31.210Z",
  "state": {
    "laneStatus": "RUNNING",
    "position": 3,
    "remainingLots": 9,
    "gapEndsAt": "2026-10-23T11:00:36.208Z",
    "pauseCause": null,
    "lot": {
      "lotId": 345, "status": "SOLD", "currentPrice": 90000, "nextPrice": null,
      "bidCount": 5, "lastBidId": 90833, "deadlineAt": null,
      "pausedRemainingMs": null, "highestBidder": "송**", "reserveNotMet": false
    }
  },
  "result": { "lotId": 345, "outcome": "SOLD", "finalPrice": 90000, "winner": "송**" },
  "nextLotInfo": { "lotId": 346, "name": "기계식 키보드", "imageUrl": "/api/images/0b7e51…", "startPrice": 30000 }
}
```

| 추가 필드 | 뜻 |
|---|---|
| `result.lotId` | 끝난 Lot |
| `result.outcome` | `SOLD` 또는 `UNSOLD` |
| `result.finalPrice` | `SOLD`면 낙찰가. 입찰이 있었던 `UNSOLD`면 마지막 최고가. 입찰이 없었으면 `null` |
| `result.winner` | `SOLD`면 낙찰자의 마스킹 닉네임, `UNSOLD`면 `null` |
| `nextLotInfo` | 다음 Lot의 정적 정보 (간격 중 미리보기, PRD T4). 마지막 Lot이었으면 `null` |

- `result`는 `state.lot`과 같은 사실을 담는다 (`outcome` = `lot.status`, `finalPrice` = 입찰이 있으면 `lot.currentPrice`, `winner` = `SOLD`면 `lot.highestBidder`). **결과 표시는 `state.lot`으로 그린다.** 재동기화로 스냅샷을 받으면 `result`가 없기 때문이다. `result`는 "방금 끝났다"는 연출(효과음, 애니메이션)의 계기로만 쓴다.
- `RUNNING` 레인이면 `state.gapEndsAt` = 끝난 시각 + 간격(기본 5초) (T4). 정지 중 강제 종료면 `null`이다. 재개하면 다시 잡힌다.
- 정지 중인 레인에서 마지막 Lot을 강제 종료하면 `LOT_CLOSED` 바로 뒤에 `LANE_FINISHED`가 다음 `seq`로 나간다 (AC-14-4).

#### `LANE_PAUSED`

추가 필드 없음. `state.laneStatus`가 `PAUSED`, `state.pauseCause`가 `OPERATOR` 또는 `SESSION_BULK`가 되고, `lot.deadlineAt`과 `state.gapEndsAt`이 `null`이 된다. 입찰 단계에서 정지했으면 `lot.pausedRemainingMs`에 남은 시간이 들어간다 (T5).

- `pauseCause`가 `RECOVERY`인 정지는 이 이벤트로 오지 않는다. 재시작 뒤 스냅샷에서 처음 보인다 (3.4 머리말).
- 마감 시각이 이미 지났는데 아직 마감 판정 전인 Lot에 정지가 처리되면, 마감이 먼저 처리된다 (ARCH 5.5.4 마감 선적용, B-10). 그러면 `LOT_CLOSED` 다음에 `LANE_PAUSED`(간격 중 정지)가 온다.

#### `LANE_RESUMED`

추가 필드 없음. `state.laneStatus`가 `RUNNING`, `state.pauseCause`가 `null`이 된다. 입찰 단계면 `lot.deadlineAt`이 새 마감 시각이고, 간격 단계면 `state.gapEndsAt`이 새 간격 종료 시각이다.

| 정지 상황 (`pauseCause`) | 새 시각 | 근거 |
|---|---|---|
| 입찰 단계, `OPERATOR`·`SESSION_BULK` | `deadlineAt` = 재개 시각 + max(남은 시간, 10초) | T5, AC-13-1·2 |
| 입찰 단계, `RECOVERY` | `deadlineAt` = 재개 시각 + 10초. 입찰 0건 Lot은 [OQ-11] | T6, AC-17-2 |
| 간격 단계 (원인 무관) | `gapEndsAt` = 재개 시각 + 5초 | OQ-10, AC-13-4 |

- 정지 화면의 "재개하면 N초" 안내는 이 표와 회차의 `timing.resumeMinMs`(API 4.2)로 계산한다 (부록 B.5).

#### `LANE_FINISHED`

추가 필드 없음. `state.laneStatus`가 `FINISHED`, `remainingLots`가 0이다. 이 뒤로 이 레인에는 메시지(비컨 포함)가 오지 않는다.

#### `HEARTBEAT` (레인 비컨)

```json
{ "type": "HEARTBEAT", "laneId": 12, "epoch": "9c41e07a5b3d2f18", "seq": 47, "serverTime": "2026-10-23T11:00:35.000Z" }
```

- `state`와 `occurredAt`이 없다. `seq`는 마지막 상태 이벤트의 값이고 올리지 않는다 (OQ-37). 처리 규칙은 5.4.

### 3.5 예시: 한 Lot의 흐름

시작가 50,000원, 레인 `RUNNING`, 기본 시간 파라미터.

| seq | type | 서버 시각 | `state`의 변화 |
|---:|---|---|---|
| 41 | `LOT_STARTED` | 11:00:00.004 | Lot 345 `BIDDING`, 현재가 50,000, 다음 호가 60,000, 마감 11:00:15.004 |
| 41 | `HEARTBEAT` | 11:00:05 | 없음 |
| 42 | `BID_ACCEPTED` | 11:00:03.120 | 현재가 60,000, 다음 호가 70,000, 마감 11:00:15.004 그대로 (13초 > 10초) |
| 43 | `BID_ACCEPTED` | 11:00:08.500 | 현재가 70,000, 마감 11:00:18.500 (7초 → 10초) |
| 44 | `LANE_PAUSED` | 11:00:12.000 | `PAUSED`, `pauseCause` `OPERATOR`, 마감 `null`, `pausedRemainingMs` 6500 |
| 45 | `LANE_RESUMED` | 11:01:00.000 | `RUNNING`, `pauseCause` `null`, 마감 11:01:10.000 (6.5초 → 10초) |
| 46 | `BID_ACCEPTED` | 11:01:05.300 | 현재가 80,000, 마감 11:01:15.300 |
| 47 | `LOT_CLOSED` | 11:01:15.310 | `SOLD`, 낙찰가 80,000, `gapEndsAt` 11:01:20.310, 다음 Lot 미리보기 |
| 48 | `LOT_STARTED` | 11:01:20.320 | Lot 346 `BIDDING` (간격 5초 뒤) |

(서버 시각 열은 이해를 돕는 값이다. 실제 `HEARTBEAT` 위치는 비컨 주기에 따라 다르다.)

---

## 4. 개인 알림 (`/user/queue/notifications`)

### 4.1 모양

알림함 API(API 6.7)의 원소와 같은 모양이다 (ARCH 7.9).

```json
{
  "id": 901,
  "type": "OUTBID",
  "payload": { "sessionId": 7, "laneId": 12, "lotId": 345, "itemName": "필름 카메라", "myAmount": 60000, "currentPrice": 70000 },
  "createdAt": "2026-10-23T11:00:08.520Z",
  "readAt": null
}
```

### 4.2 종류별 `payload`

payload 필드는 (제안)이다. ARCH 4.4는 "이동에 필요한 ID"만 정했다.

| `type` | 받는 사람 | `payload` | 누르면 이동 (PRD 8.2) |
|---|---|---|---|
| `OUTBID` | 밀려난 이전 최고 입찰자 | `sessionId`, `laneId`, `lotId`, `itemName`, `myAmount`(내 입찰가), `currentPrice`(나를 밀어낸 가격) | P-03 (`sessionId`) |
| `WON` | 낙찰자 | `sessionId`, `lotId`, `itemName`, `tradeId`, `price`, `paymentDueAt` | U-04 (`tradeId`) |
| `LOT_UNSOLD` | 판매자 | `sessionId`, `lotId`, `itemId`, `itemName` | U-02 (`itemId`) |
| `PAYMENT_DUE_SOON` | 낙찰자 | `tradeId`, `itemName`, `paymentDueAt`. 보내는 시점은 [B-9] | U-04 |
| `TRADE_CANCELED` | 낙찰자 | `tradeId`, `itemName` | U-04 |
| `REVIEW_RESULT` | 판매자 | `itemId`, `itemName`, `result`(`APPROVED` \| `REJECTED`), `rejectReason` | U-02 |

- `TRADE_CANCELED`를 받으면 경고가 쌓여 입찰 정지가 되었을 수 있다 (S-02). 내 정보(API 3.5)를 다시 읽는다.
- `WON`, `REVIEW_RESULT`, `LOT_UNSOLD`를 받았을 때 해당 목록 화면(U-04, U-02)이 열려 있으면 그 목록을 다시 읽는다.

### 4.3 전달 보장

- 서버는 알림을 DB 알림함에 먼저 저장하고 개인 큐로 보낸다. 접속해 있지 않으면 전송은 버려지고 알림함에만 남는다 (PRD 9.4-4).
- 개인 알림에는 `seq`가 없다. 유실을 감지하지 않는다. 연결하거나 재연결할 때마다 알림함(API 6.7)을 다시 읽는다.
- `OUTBID`는 입찰과 다른 트랜잭션이라, 입찰 커밋 직후 서버가 죽으면 사라질 수 있다 (ARCH 6.3). `WON`과 `LOT_UNSOLD`는 마감과 같은 트랜잭션이라 알림함에서는 사라지지 않는다.
- **알림으로 레인 화면 상태를 바꾸지 않는다.** `OUTBID`를 받아도 레인의 현재가는 `BID_ACCEPTED`가 바꾼다. 두 메시지의 도착 순서는 정해져 있지 않다 (5.3).

---

## 5. seq, epoch, 순서 보장

### 5.1 epoch

- 서버가 부팅할 때 한 번 만드는 무작위 문자열이다 (예: 16자리 16진수). 모든 레인이 같은 값을 쓴다 (ARCH 7.4).
- 클라이언트는 **같은지만 비교한다.** 대소 비교나 해석을 하지 않는다 (OQ-34).
- epoch가 바뀌면 그 레인의 `seq`는 의미가 없어진다. 스냅샷부터 다시 받는다 (AC-11-5).

### 5.2 seq

- 레인마다 따로 센다. 레인 런타임이 만들어질 때 0이고, 상태 이벤트를 하나 발행할 때마다 1 증가한다. 첫 이벤트가 1이다.
- `HEARTBEAT`, 거절된 입찰은 `seq`를 바꾸지 않는다 (ARCH 5.6 E5).
- 명령 하나가 이벤트 두 개를 만들 수 있다 (정지 중 마지막 Lot 강제 종료: `LOT_CLOSED`, `LANE_FINISHED`). 각각 `seq`를 하나씩 쓴다.
- 서버가 매기는 번호에는 갭이 없다. 클라이언트가 보는 갭은 전달 경로(브로커, 네트워크, 재연결)에서 생긴 것이다 (ARCH 7.4).
- 실행 중이 아닌 레인(`READY`, 서버 재시작 뒤의 `FINISHED`)의 스냅샷은 `seq`가 0이다 (API 4.3). `READY` 레인이 시작하면 첫 `LOT_STARTED`가 `seq` 1이다.

### 5.3 순서 보장 범위

| 범위 | 보장 |
|---|---|
| 한 레인의 상태 이벤트 | `seq`는 서버가 커밋한 순서대로 붙는다. 전달도 그 순서로 하도록 세션별 발행 순서 보장을 켠다 (잠정안, ARCH 7.4). 그래도 클라이언트는 갭과 역순을 처리해야 한다 (6.2) |
| 한 레인의 비컨과 상태 이벤트 | 같은 레인 스레드와 같은 경로로 나가므로 순서가 뒤바뀌지 않는다 (ARCH 7.4) |
| 레인 사이 | 보장하지 않는다. `seq`를 레인 사이에 비교하지 않는다 |
| REST 응답과 레인 이벤트 | 보장하지 않는다. 입찰 응답이 브로드캐스트보다 먼저 올 수도 늦게 올 수도 있다 (ARCH 2.5). 응답의 `epoch`·`seq`로 맞춘다 |
| 스냅샷과 레인 이벤트 | 스냅샷의 `state`와 `seq`는 언제나 같은 시점이다 (ARCH 7.5). 스냅샷의 `seq`보다 큰 이벤트만 반영한다 |
| 개인 알림과 레인 이벤트 | 보장하지 않는다 |
| 서버 재시작 전후 | epoch가 바뀐다. 이전 epoch의 `seq`와 비교하지 않는다 |

- 서버는 이벤트를 병합하지 않는다. 상태 변화 하나가 이벤트 하나다. 병합(conflation)은 [OQ-40]이고, 도입하면 이 문서의 버전을 올린다.

### 5.4 레인 비컨 (`HEARTBEAT`)

갭은 다음 메시지가 와야 보인다. 마감·정지·레인 종료처럼 뒤에 메시지가 한동안 없는 이벤트를 잃으면 갭 검사만으로는 화면이 무기한 틀린다. 그래서 비컨을 보낸다 (OQ-37, ARCH 7.4).

- 서버는 `RUNNING`·`PAUSED` 레인마다 `heartbeatIntervalMs`(제안: 5,000) 주기로 비컨을 보낸다. 주기는 스냅샷에 실려 온다 (API 4.3).
- `READY`, `FINISHED` 레인에는 비컨이 없다. `READY` 레인은 회차가 시작되면 비컨이 시작되므로, 첫 `LOT_STARTED`를 잃어도 비컨으로 알게 된다.

**클라이언트 규칙** (AC-11-7)

| 조건 | 처리 |
|---|---|
| 비컨의 `epoch`가 반영한 epoch와 다르다 | 재동기화 |
| 비컨의 `seq` > 반영한 `seq` | 재동기화 (마지막 메시지 유실) |
| 비컨의 `seq` ≤ 반영한 `seq` | 정상. 마지막 비컨 수신 시각만 기록한다 |
| `RUNNING`·`PAUSED` 레인에서 비컨이 `2 × heartbeatIntervalMs` 동안 오지 않음 | 재동기화 |

- 클라이언트 카운트다운이 0이 되었는데 `LOT_CLOSED`가 오지 않으면, 시간으로 판단하지 않고 비컨으로 판단한다. 비컨의 `seq`가 같으면 서버가 아직 마감을 판정하는 중이므로 계속 기다린다 (PRD C4, OQ-37).

---

## 6. 동기화 절차: 연결, 재연결, 스냅샷 복구

PRD 9.4의 절차를 클라이언트 코드 수준으로 옮긴 것이다 (ARCH 7.6, CH-08 후보 A2).

### 6.1 연결과 관전 화면 진입

```
enterSession(sessionId):                        // 관전 화면(P-03)·콘솔(A-04)에 들어올 때
  first = GET /api/sessions/{sessionId}         // ① laneId를 알아내고 화면을 먼저 그린다
  render(first)                                 //    아직 캐시에 넣지 않는다
  ensureConnected()
  subscribeAndSync(first.lanes의 laneId 목록)

ensureConnected():
  연결이 없으면 STOMP CONNECT (1.2)
  USER면 SUBSCRIBE /user/queue/notifications, 이어서 GET /api/me/notifications (4.3)

subscribeAndSync(laneIds):                      // 첫 진입과 재연결이 함께 쓴다
  레인마다 SUBSCRIBE /topic/lanes/{laneId}      // ② 이때부터 받은 메시지는 레인별 버퍼에 쌓는다
  base = GET /api/sessions/{sessionId}          // ③ 구독한 뒤의 스냅샷 묶음 (API 4.2)
  레인마다:
    applySnapshot(lane, base의 그 레인 스냅샷)
    버퍼의 메시지를 도착 순서대로 onMessage로 처리한다
  실시간 모드로 바꾼다
```

- **구독을 먼저 하고 스냅샷을 나중에 받는다.** 반대로 하면 스냅샷과 구독 사이의 메시지를 놓친다 (CH-08 시나리오 1). 그런데 처음 들어올 때는 구독할 `laneId`를 모르므로, 회차 상세를 한 번 더 부른다(①과 ③). ①은 `laneId`를 알아내고 화면을 빨리 그리는 용도이고, 상태의 기준은 ③이다 (0.2에서 고침. ARCH 0.2의 7.6은 `laneId`를 이미 안다고 보았고, ARCH 0.3에서 같이 고쳤다).
- 재연결할 때는 `laneId`를 이미 알므로 ①을 건너뛰고 `subscribeAndSync`부터 한다 (6.4).
- ③의 응답이 ①보다 낡을 수는 없다. 같은 레인에서 `seq`는 줄지 않고, `applySnapshot`이 낡은 스냅샷을 버린다.
- 관전 화면을 떠나면 레인 구독을 해지한다 (`UNSUBSCRIBE`). 개인 큐 구독과 연결은 1.1의 연결 시점 규칙을 따른다.

### 6.2 메시지 처리

```
onMessage(msg):
  if msg.epoch != lane.epoch          → resync(lane)
  else if msg.type == HEARTBEAT:
    lastHeartbeatAt[lane] = 지금
    if msg.seq > lane.seq             → resync(lane)     // 마지막 메시지 유실
  else if msg.seq <= lane.seq         → 버린다 (이미 반영함)
  else if msg.seq == lane.seq + 1     → lane = reduce(lane, msg)
  else                                → resync(lane)     // 갭

reduce(lane, msg):
  lane.seq = msg.seq
  lane.state = msg.state                     // 동적 상태는 통째로 바꾼다
  if msg.type == LOT_STARTED: lane.lotInfo = msg.lotInfo; lane.nextLotInfo = null
  if msg.type == LOT_CLOSED:  lane.nextLotInfo = msg.nextLotInfo; 결과 연출을 띄운다 (3.4)
  "내가 최고 입찰자"와 버튼 상태를 다시 계산한다 (부록 B)

heartbeatWatch():                            // 1초마다
  laneStatus가 RUNNING·PAUSED인 레인에서
  마지막 비컨 뒤 2 × heartbeatIntervalMs가 지났으면 → resync(lane)
```

- 모르는 `type`은 버린다. `seq`는 갱신하지 않는다. 새 상태 이벤트 종류가 생겼는데 클라이언트가 모르면 다음 메시지에서 갭으로 보여 재동기화된다.

### 6.3 재동기화

```
resync(lane):
  레인마다 동시에 1건만 보낸다. 그동안 온 메시지는 버퍼에 쌓는다
  GET /api/lanes/{laneId}/snapshot
  applySnapshot(lane, snapshot)
  버퍼를 onMessage로 처리한다

applySnapshot(lane, snapshot):
  if snapshot.epoch != lane.epoch or snapshot.seq >= lane.seq:
    lane.epoch, lane.seq, lane.state, lane.lotInfo, lane.nextLotInfo, lane.me = snapshot의 값
    myBid를 갱신한다 (부록 B.2)
  // 아니면 버린다 (이미 더 새 상태를 반영했다)
```

- 여러 레인이 한꺼번에 재동기화해야 하면(epoch 변경 등) 레인 스냅샷 대신 회차 상세(API 4.2) 한 번으로 받는다. 재연결 폭주 때 요청 수가 1/4로 준다 (ARCH 7.5).

### 6.4 연결 끊김과 재연결

| 단계 | 처리 |
|---|---|
| 끊김 감지 | `@stomp/stompjs`의 `onWebSocketClose`, 또는 STOMP 하트비트 누락 |
| 끊긴 동안 화면 | 모든 레인 패널에 "연결 중…"을 표시하고 **입찰 버튼을 비활성화한다** (제안). 화면의 현재가가 낡아 보낸 입찰은 대부분 409가 되고, 결과도 화면에 반영되지 않기 때문이다. 카운트다운은 계속 그려도 된다. 진행 중이던 입찰의 재전송(API 5.1)은 REST라 그대로 계속한다 |
| 재연결 지연 | 지수 백오프 + 무작위 지연 (ARCH 7.6). 1초에서 시작해 2배씩, 최대 30초. 매번 0~100% 사이의 무작위 값을 곱한다 (제안). 5,000명이 같은 순간에 재연결하지 않게 한다 (CH-08 시나리오 4) |
| 바로 재연결 | 브라우저의 `online` 이벤트, 탭이 다시 보일 때(`visibilitychange`)는 지연을 기다리지 않고 바로 시도한다 |
| 재연결 직전 | 토큰 만료를 확인하고 필요하면 재발급한다 (1.2) |
| 재연결 뒤 | `ensureConnected()` → `subscribeAndSync()` (6.1). `laneId`는 이미 안다 |
| 끊긴 동안 놓친 것 | 레인 상태는 스냅샷이 덮어쓴다. 개인 알림은 알림함, 놓친 Lot 결과는 내 입찰 내역(API 6.1)에서 본다 (PRD 9.4-4) |

### 6.5 서버 재시작 때 클라이언트가 보는 것

1. 연결이 끊긴다. 진행 중이던 입찰 요청은 응답을 받지 못한다.
2. 재연결은 서버가 뜰 때까지 실패하고 백오프한다. 그동안 입찰 재전송 창(10초, API 5.1)이 닫힐 수 있다.
3. 재연결하면 스냅샷의 epoch가 바뀌어 있다. 재시작 순간 `RUNNING`이던 레인은 `PAUSED`이고 `seq`는 0부터 다시 센다 (ARCH 5.5.9).
4. 응답을 받지 못한 입찰은 스냅샷의 `me.isHighestBidder`로 결과를 확인한다. 레인이 `PAUSED`라 그 사이 밀릴 수 없으므로 이 값이 내 입찰의 결과와 같다 (PRD 9.4-5).
5. 운영자가 재개하면 `LANE_RESUMED`가 새 epoch의 `seq` 1로 온다.

### 6.6 캐시 규칙 (TanStack Query)

ARCH 7.6에서 정한 것이다.

- 레인 상태는 레인마다 캐시 키 하나(`['lane', laneId]`)에 둔다. STOMP 핸들러와 `applySnapshot`이 `setQueryData`로만 바꾼다. 화면 컴포넌트는 이 캐시만 구독한다.
- 레인 쿼리의 자동 재조회를 끈다: `staleTime: Infinity`, `refetchOnWindowFocus: false`, `refetchOnReconnect: false`, `refetchOnMount: false`. 켜 두면 라이브러리가 6.1의 절차를 거치지 않고 스냅샷을 다시 받아, 그 사이 STOMP로 반영한 더 새 상태를 덮어쓴다 (CH-08 시나리오 8).
- 스냅샷을 캐시에 넣는 곳은 `applySnapshot` 하나다. 회차 상세(API 4.2)의 레인 스냅샷도 이 함수를 지난다.
- 회차 정보(`session`, `lineup`, `me.myLotIds`)는 레인 캐시와 다른 키(`['session', sessionId]`)에 둔다. 이 키는 6.7의 규칙으로만 다시 읽는다.

### 6.7 예정 회차 화면

M1-08 승인 (ADR-013): 아래 60초 재조회와 0초/10초 대기 문구를 채택한다. READY/PENDING은 reserveNotMet=false로 최저가 미달 배지를 표시하지 않는다. 진행 중 조건 OQ-22는 미결이다. M1-08은 예정 조회·사전 구독·구독 후 재조회·연결 수명까지이며 진행 스냅샷은 M2-04, 이벤트 적용·갭/비컨 복구·LOT_STARTED 실시간 전환은 M2-05에서 완성한다.

`SCHEDULED` 회차의 관전 화면(P-03 "시작까지 남은 시간과 레인별 편성 미리보기")에는 따로 규칙이 있다.

- **편성과 예정 시각이 바뀌어도 이벤트가 없다.** 시작 전에는 운영자가 둘 다 바꿀 수 있고(PRD A2, AC-04-4), 회차 토픽이 없으며, `READY` 레인은 비컨도 보내지 않는다 (5.4). 그래서 회차가 `SCHEDULED`인 동안 회차 상세(API 4.2)를 60초마다 다시 읽는다 (M1-08 승인). 받은 레인 스냅샷은 `applySnapshot`을 지난다.
- 레인 토픽은 미리 구독해 둔다 (6.1). 회차가 시작되면(예정 시각이든 운영자의 "지금 시작"이든) 레인마다 `LOT_STARTED`(`seq` 1)가 와서 패널이 바로 실시간 모드가 된다. 그 뒤로는 주기적으로 다시 읽지 않는다. 시작한 뒤에는 편성이 잠기기 때문이다 (AC-05-3).
- 시작 순간에 받은 `LOT_STARTED`의 `lotInfo`가 편성 미리보기와 다를 수 있다 (마지막 재조회 뒤에 편성이 바뀐 경우). `lotInfo`를 따른다. 내 출품 Lot인지(`me.myLotIds`)도 낡았을 수 있으므로, 첫 `LOT_STARTED`를 받으면 회차 상세를 한 번 다시 읽는다. 모든 관전자가 같은 순간에 부르지 않게 0~3초 무작위 지연을 둔다 (제안). 그 사이 입찰은 서버가 `SELF_BID_FORBIDDEN`으로 막는다.
- **예정 시각이 지나도 시작하지 않을 수 있다.** 시작 조건을 채우지 못했거나(AC-06-3) 서버가 꺼져 있었다면(AC-06-4) 회차는 `SCHEDULED`로 남는다. 카운트다운이 0이 되면 "곧 시작합니다"로 표시하고, 그 뒤 10초(M1-08 승인) 안에 `LOT_STARTED`가 없으면 "운영자 시작 대기 중"으로 바꾼다. 그 상태에서도 60초 재조회와 레인 구독은 그대로 둔다.

---

## 7. 클라이언트 시계 보정

### 7.1 원칙

- 모든 판정은 서버 시계로 한다. 클라이언트 시계는 표시용이다 (PRD C1, C4).
- 서버는 마감을 절대 시각(`deadlineAt`)으로 보내고, 메시지마다 서버 시각(`serverTime`)을 싣는다. 클라이언트는 둘로 남은 시간을 그린다.
- 오차 추정 방식은 CHALLENGES CH-01 (나)에서 측정으로 정한다. 이 계약은 후보 B1(오차 추정)과 B3(수신 시점부터 세기)을 그대로 지원한다 (ARCH 7.7). 아래는 B1의 기본 구현이다 (제안).

### 7.2 표본

| 표본이 되는 것 | 표본이 되지 않는 것 |
|---|---|
| 레인 이벤트와 비컨의 `serverTime`. 레인 스냅샷(API 4.3)·회차 상세(4.2)·회차 목록(4.1)·내 거래(6.2) 응답의 `serverTime` | 입찰 응답. `serverTime`이 없고, 재생 응답은 몇 초 전 값을 그대로 돌려주기 때문이다 (ARCH 7.7) |

- 수신 시각은 메시지가 도착한 순간에 `performance.now()`로 기록한다. 버퍼에 쌓았다 나중에 처리하는 메시지도 도착 시각을 쓴다.
- 표본 하나: `e = Date.parse(serverTime) − performance.now()`.

### 7.3 오차 추정

```
offset = 최근 60초 동안의 표본 e 중 최댓값
serverNow() = performance.now() + offset
```

- **최댓값을 쓰는 이유.** `e = (실제 시계 차이) − (전달 지연)`이고 전달 지연은 0 이상이다. 그래서 모든 표본은 실제 차이보다 작거나 같고, 지연이 가장 작았던 표본이 최댓값이다. 평균을 쓰면 지연의 평균만큼 늘 늦게 보인다.
- **최근 60초로 자르는 이유.** 사용자 PC와 서버 시계의 속도 차이(드리프트)와 서버 시계 보정을 따라가기 위해서다. 비컨이 5초마다 오므로 60초면 레인당 표본이 10개 이상이다.
- **`Date.now()`가 아니라 `performance.now()`를 쓰는 이유.** 사용자가 PC 시계를 바꾸거나 OS가 시계를 맞춰도 `performance.now()`는 뛰지 않는다. 시계 차이 자체는 첫 표본에 흡수된다.
- 표본이 아직 없으면 남은 시간을 그리지 않는다.
- 목표: 사용자 PC 시계가 ±5분 틀려도 표시 오차 200ms 이내 (CH-01 성공 기준, 제안).

### 7.4 남은 시간 그리기

| 표시 | 계산 |
|---|---|
| Lot 마감까지 | `Date.parse(state.lot.deadlineAt) − serverNow()` |
| 다음 Lot까지 (간격) | `Date.parse(state.gapEndsAt) − serverNow()` |
| 정지 중 | `state.lot.pausedRemainingMs`를 멈춘 값으로 보여 준다 (부록 B.5) |
| 회차 시작까지 (예정 회차) | `Date.parse(session.scheduledStartAt) − serverNow()` |
| 결제 기한까지 (U-04) | `Date.parse(paymentDueAt) − serverNow()`. 분 단위 표시라면 표본이 없을 때 `Date.now()`로 대신해도 된다 |

- 값을 1씩 빼며 세지 않는다. 그릴 때마다 위 식으로 다시 계산한다 (`requestAnimationFrame` 또는 100~250ms 타이머). 탭이 백그라운드에서 타이머가 밀려도 돌아오면 맞는 값이 나온다.
- 마감 시각은 `BID_ACCEPTED`가 바꾼다. 입찰 응답의 `deadlineAt`으로 바꾸지 않는다 (ARCH 2.5).

### 7.5 0이 된 뒤

- Lot 카운트다운이 0이 되면 "마감 판정 중"으로 표시한다. `LOT_CLOSED`를 받기 전에는 낙찰·유찰 결과를 보여 주지 않는다 (PRD C4, AC-12-6).
- 0이 된 뒤에도 서버 기준으로 시간이 남아 있을 수 있고, 반대로 화면에 시간이 남아 있어도 서버 기준으로 마감이면 입찰은 `LOT_NOT_BIDDING`이다 (PRD C4).
- 0이 되면 입찰 버튼을 비활성화한다 (제안, 부록 B.3). 누른 입찰은 거의 모두 `LOT_NOT_BIDDING`이 되기 때문이다.
- `LOT_CLOSED`를 잃었는지는 비컨으로 안다 (5.4).
- 간격 카운트다운이 0이 되면 "다음 Lot 준비 중"으로 표시하고 `LOT_STARTED` 또는 `LANE_FINISHED`를 기다린다. 다음 Lot을 클라이언트가 먼저 열지 않는다.

---

## 8. 미결 사항이 이벤트 계약에 주는 영향

| 미결 | 지금 계약 | 결정되면 |
|---|---|---|
| [OQ-06] RUNNING 강제 종료 뒤 | `LOT_CLOSED` 뒤 무엇이 오는지 정하지 않음 | `LOT_STARTED` 또는 `LANE_PAUSED`. 모양은 그대로 |
| [OQ-11] 복구 재개, 입찰 0건 Lot | `LANE_RESUMED`의 `deadlineAt`이 알려 준다 | 값만. 정지 화면의 "재개하면 N초" 안내(부록 B.5)만 고친다 |
| [OQ-13] 일괄 재개 범위 | `pauseCause`로 개별 정지와 일괄 정지를 구분할 수 있다 | 콘솔 안내 문구만 |
| [OQ-22] 최저가 미달 배지 | `reserveNotMet` | `true`가 되는 조건만 |
| [OQ-25] 패찰·유찰 표시 | 공개 메시지에 유찰 사유 없음 | 알림 종류가 늘 수 있다 (4.2) |
| [OQ-32] DB 쓰기 실패 시 자동 정지 | 없음 | 자동 정지를 하면 `LANE_PAUSED`가 운영자 명령 없이 오고, `pauseCause`에 새 값(예: `FAULT`)이 생긴다. 클라이언트는 모르는 `pauseCause`도 "일시정지"로 표시한다 |
| [OQ-35] 서버 시계 점프 | 없음 | 감지 후 정지하기로 하면 위와 같다 |
| [OQ-40] 병합 | 하지 않음 | 갭 규칙이 바뀐다. 계약 버전을 올린다 |
| [OQ-41] PENDING Lot 강제 종료 | 없음 | 허용하면 `remainingLots`가 줄어드는 새 이벤트가 필요하다 |

---

## 부록 A. 타입 요약 (TypeScript)

규범은 본문의 표다. 프론트가 옮겨 쓰기 쉽게 모은 것이다.

```ts
type Instant = string; // ISO-8601, UTC, 밀리초 3자리

type LaneStatus = 'READY' | 'RUNNING' | 'PAUSED' | 'FINISHED';
type LotStatus = 'PENDING' | 'BIDDING' | 'SOLD' | 'UNSOLD';
type PauseCause = 'OPERATOR' | 'SESSION_BULK' | 'RECOVERY';

interface LotState {
  lotId: number;
  status: LotStatus;
  currentPrice: number;
  nextPrice: number | null;
  bidCount: number;
  lastBidId: number | null;
  deadlineAt: Instant | null;
  pausedRemainingMs: number | null;
  highestBidder: string | null;
  reserveNotMet: boolean;
}

interface LaneState {
  laneStatus: LaneStatus;
  position: number;
  remainingLots: number;
  gapEndsAt: Instant | null;
  pauseCause: PauseCause | null;
  lot: LotState | null;
}

interface LotInfo { lotId: number; name: string; imageUrl: string; startPrice: number; }

interface LotResult {
  lotId: number;
  outcome: 'SOLD' | 'UNSOLD';
  finalPrice: number | null;
  winner: string | null;
}

interface Envelope<T extends string> {
  type: T;
  laneId: number;
  epoch: string;
  seq: number;
  occurredAt: Instant;
  serverTime: Instant;
  state: LaneState;
}

type LaneEvent =
  | (Envelope<'LOT_STARTED'> & { lotInfo: LotInfo })
  | Envelope<'BID_ACCEPTED'>
  | (Envelope<'LOT_CLOSED'> & { result: LotResult; nextLotInfo: LotInfo | null })
  | Envelope<'LANE_PAUSED'>
  | Envelope<'LANE_RESUMED'>
  | Envelope<'LANE_FINISHED'>;

interface Heartbeat { type: 'HEARTBEAT'; laneId: number; epoch: string; seq: number; serverTime: Instant; }

type LaneMessage = LaneEvent | Heartbeat;

interface LaneSnapshot {           // API 4.3
  laneId: number;
  epoch: string;
  seq: number;
  serverTime: Instant;
  heartbeatIntervalMs: number;
  state: LaneState;
  lotInfo: LotInfo | null;
  nextLotInfo: LotInfo | null;
  me: { isHighestBidder: boolean } | null;
}

type NotificationType =
  | 'OUTBID' | 'WON' | 'LOT_UNSOLD' | 'PAYMENT_DUE_SOON' | 'TRADE_CANCELED' | 'REVIEW_RESULT';

interface Notification {           // API 6.7과 같은 모양
  id: number;
  type: NotificationType;
  payload: Record<string, unknown>; // type별 필드는 4.2
  createdAt: Instant;
  readAt: Instant | null;
}
```

---

## 부록 B. 화면 상태 계산

레인 상태(3.2), 입찰 응답(API 5.1), 스냅샷의 `me`(API 4.3), 내 정보(API 3.5)를 합쳐 화면을 정하는 규칙이다. 결과는 상태로 저장하지 않고, 레인 상태가 바뀔 때마다 다시 계산한다 (ARCH 2.5). 0.2에서 더했다 (API 부록 B F-09, F-10).

### B.1 레인 단계

```ts
type Phase = 'NO_LINEUP' | 'BEFORE_START' | 'BIDDING' | 'GAP' | 'PAUSED_BIDDING' | 'PAUSED_GAP' | 'FINISHED';

function phase(s: LaneState): Phase {
  switch (s.laneStatus) {
    case 'READY':    return s.lot ? 'BEFORE_START' : 'NO_LINEUP';
    case 'RUNNING':  return s.lot?.status === 'BIDDING' ? 'BIDDING' : 'GAP';
    case 'PAUSED':   return s.lot?.status === 'BIDDING' ? 'PAUSED_BIDDING' : 'PAUSED_GAP';
    case 'FINISHED': return 'FINISHED';
  }
}
```

- 회차의 모든 레인이 `FINISHED`면 화면 위쪽에 "회차 종료"를 표시한다 (2장).

### B.2 "내가 최고 입찰자"

레인마다 `myBid` 하나를 탭 메모리에 기억한다.

```ts
interface MyBid { lotId: number; bidId: number; epoch: string; seq: number; }
```

| 언제 | `myBid` |
|---|---|
| 입찰 성공 응답을 받음 (재생 응답 포함) | 응답의 `lotId`, `bidId`, `epoch`, `seq` |
| `applySnapshot`이 캐시를 바꿈 (6.3) | `me.isHighestBidder`가 `true`면 `{ lotId: state.lot.lotId, bidId: state.lot.lastBidId, epoch: snapshot.epoch, seq: snapshot.seq }`. `false`면 그대로 둔다 |
| 로그아웃 | 지운다 |

```ts
function isMeHighest(lane: { epoch: string; seq: number; state: LaneState }, my: MyBid | null): boolean {
  const lot = lane.state.lot;
  if (!my || !lot || lot.lotId !== my.lotId) return false;
  if (lot.lastBidId === my.bidId) return true;          // 내 입찰이 마지막 수락 입찰이다
  return my.epoch === lane.epoch && lane.seq < my.seq;  // 응답이 브로드캐스트보다 먼저 왔다
}
```

- 응답이 브로드캐스트보다 먼저 오면, 그 `seq`를 반영할 때까지 "내가 최고 입찰자"로 먼저 표시한다. 반영하면 `lastBidId`가 내 `bidId`가 되어 표시가 그대로 이어진다. 그 사이 응답보다 앞선 스냅샷이 늦게 와도 `seq`가 작으므로 표시가 꺼지지 않는다 (ARCH 2.5).
- 다른 사람의 입찰이 수락되면 `lastBidId`가 바뀌므로 표시가 저절로 꺼지고 버튼이 다시 켜진다 (AC-12-3).
- **스냅샷의 `me.isHighestBidder`를 그대로 저장해 쓰지 않는다.** 뒤따르는 이벤트가 이 값을 갱신하지 않기 때문이다. 위 표처럼 `lastBidId`로 바꿔 둔다.
- `lastBidId`와 `bidId`는 DB에 남는 값이라 재시작으로 epoch가 바뀌어도 비교가 맞다 (ARCH 2.5).
- 비로그인과 ADMIN은 언제나 `false`다.

### B.3 입찰 버튼

위에서부터 처음 맞는 줄이 버튼 상태다.

| # | 조건 | 버튼 | 문구 (예) |
|---|---|---|---|
| 1 | ADMIN | 숨김 | — |
| 2 | 비로그인 | 로그인 유도 | "로그인하고 입찰" → P-02, 로그인 뒤 이 화면으로 (AC-12-4) |
| 3 | 연결 끊김 (6.4) | 비활성 | "연결 중…" |
| 4 | 단계가 `BIDDING`이 아님 (B.1) | 비활성 | 단계별 안내 (B.5, AC-12-5) |
| 5 | `bidSuspended` (API 3.5) | 비활성 | "입찰 정지 상태입니다" |
| 6 | `state.lot.lotId`가 `me.myLotIds`에 있음 (API 4.2) | 비활성 | "내 출품" (AC-12-4) |
| 7 | `isMeHighest` (B.2) | 비활성 | "내가 최고 입찰자" (AC-12-3) |
| 8 | 이 레인에 응답을 기다리는 입찰이 있음 (재전송 중 포함) | 비활성 | "입찰 중…" |
| 9 | `error.lane`을 받았고 그 `seq`까지 아직 반영하지 못함 (API 5.1) | 비활성 | "갱신 중…" |
| 10 | 남은 시간 ≤ 0 (7.5) | 비활성 | "마감 판정 중" |
| 11 | 그 밖 | 활성 | "{nextPrice}원에 입찰" |

- 8번이 연속 클릭을 막는다. 누를 때마다 새 키를 만드므로(API 1.6), 응답 전에 또 누르면 두 번째 입찰은 `ALREADY_HIGHEST_BIDDER`나 `PRICE_CHANGED`를 받는다.
- 9번의 기다림은 같은 epoch에서 `lane.seq ≥ error.lane.seq`가 되거나, epoch가 달라 재동기화하면 끝난다.
- `503 LANE_OVERLOADED`를 받으면 "혼잡" 안내만 띄우고 버튼은 바로 11번 상태로 돌아간다. 자동으로 다시 보내지 않는다.
- 누르는 순간의 `state.lot`으로 요청을 만든다: `lotId`, `expectedPrice = currentPrice`, `amount = nextPrice` (API 5.1).

### B.4 낙찰·패찰 표시 (M-15)

`LOT_CLOSED`를 반영했거나 스냅샷에서 끝난 Lot을 받았을 때(단계 `GAP`, `PAUSED_GAP`, `FINISHED`) 정한다. 값은 `state.lot`에서 읽는다 (3.4).

| 조건 | 내 화면 | 모두에게 |
|---|---|---|
| `SOLD`이고 `isMeHighest` | "낙찰" + 결제 안내 → U-04 (AC-15-1) | "낙찰 · {currentPrice}원 · {highestBidder}" |
| `SOLD`이고 이 Lot에 내 성공 입찰이 있었음 | "패찰" (AC-15-2. 정의는 [OQ-25]) | 위와 같다 |
| `UNSOLD`이고 이 Lot에 내 성공 입찰이 있었음 | "패찰" 또는 "유찰" ([OQ-25]) | "유찰" |
| 그 밖 | 없음 | `SOLD`면 위와 같고, `UNSOLD`면 "유찰" |

- "이 Lot에 내 성공 입찰이 있었음"은 탭이 기억한다. 성공 응답을 받을 때마다 `lotId`를 집합에 넣는다. 새로고침하면 잊으므로, 그때는 낙찰만 표시하고 패찰은 내 입찰 내역(API 6.1)에서 본다 (AC-15-3, API 부록 B.3).
- 유찰 사유는 공개 메시지에 없다 (3.4). "최저가 미달로 유찰" 같은 문구를 만들지 않는다.

### B.5 단계별 표시

| 단계 | 패널 | 카운트다운 (7.4) |
|---|---|---|
| `NO_LINEUP` | "편성 준비 중" | 없음 |
| `BEFORE_START` | 1번 Lot 미리보기(`lotInfo`), 시작가 | 회차 시작까지 (6.7) |
| `BIDDING`, `bidCount = 0` | "첫 입찰 대기", 시작가, 첫 입찰 금액(`nextPrice`) | `deadlineAt` |
| `BIDDING`, `bidCount ≥ 1` | 현재가, 최고 입찰자, 다음 호가, 최저가 미달 배지 | `deadlineAt` |
| `GAP` | 결과(B.4), 다음 Lot 미리보기(`nextLotInfo`). 다음 Lot이 없으면 "레인 종료 예정" | `gapEndsAt` |
| `PAUSED_BIDDING` | "일시정지" + 재개 안내 (아래) | 멈춤 |
| `PAUSED_GAP` | "일시정지", 결과, 다음 Lot 미리보기, "재개하면 5초 뒤 다음 Lot" | 멈춤 |
| `FINISHED` | "레인 종료", 마지막 결과 | 없음 |
| (모든 단계) | "현재 {position}번째 / 남은 {remainingLots}개" (T3) | |

**재개 안내** (`PAUSED_BIDDING`)

| `pauseCause` | 안내 |
|---|---|
| `OPERATOR`, `SESSION_BULK` | "{pausedRemainingMs}초 남기고 정지 · 재개하면 {max(pausedRemainingMs, resumeMinMs)}초" (T5) |
| `RECOVERY` | "서버 재시작으로 정지 · 재개하면 {resumeMinMs}초" (T6. 입찰 0건 Lot은 [OQ-11]) |
| 그 밖 (모르는 값) | "일시정지" |

- `resumeMinMs`는 회차의 `timing.resumeMinMs`(API 4.2, 기본 10초)다. 재개 뒤의 실제 마감은 `LANE_RESUMED`의 `deadlineAt`을 따른다. 안내는 예고일 뿐이다.
- 남은 시간은 초 단위로 올림해 보여 준다 (제안). 0.4초 남았을 때 "1초"로 보여야 "0초"가 "마감 판정 중"과 겹치지 않는다.
