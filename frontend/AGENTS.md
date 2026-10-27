# Frontend — React + Vite + TypeScript + Tailwind

## 목표
백엔드 기능을 정확하게 시연하는 깔끔한 UI. 화려함보다 실시간 반영의 정확성과 완성도.

## 구조
src/
├── api/generated/    OpenAPI에서 자동 생성 (직접 수정 금지)
├── api/errorMessages.ts   백엔드 에러코드 → 한국어 메시지 매핑 (이 파일 하나로 관리)
├── realtime/         STOMP 클라이언트, 이벤트 타입(events.ts), 레인 구독 훅
├── components/ui/    공통 컴포넌트 (Button, Card, Modal, Toast, Badge, Spinner, EmptyState)
├── features/lane/    레인 카드, 카운트다운, 입찰 버튼
├── features/admin/   운영자 화면
└── pages/

## 규칙
- REST 호출은 생성된 클라이언트만 사용한다. 아직 생성되지 않은 엔드포인트는 API.md 기준 목 서버로 개발하고, gen-api 뒤 생성된 클라이언트로 바꾼다
- 서버 상태는 TanStack Query로 관리한다. 레인 상태는 ['lane', laneId] 캐시 하나에 두고 자동 재조회를 끈다(staleTime: Infinity, refetchOnWindowFocus·refetchOnReconnect·refetchOnMount: false). 이 캐시는 STOMP 핸들러와 applySnapshot만 바꾼다
- 색상, 간격, 폰트는 tailwind 설정의 디자인 토큰만 사용한다. 임의의 색상값은 금지
- 모든 화면에 로딩 / 빈 상태 / 에러 상태를 처리한다

## 실시간 규칙
- 동기화 절차는 EVENTS 6장을 그대로 따른다
  - 진입: 회차 상세로 laneId를 알아낸 뒤, 레인 토픽을 먼저 구독하고(이때부터 메시지는 버퍼에 쌓음) 회차 상세를 다시 받아 기준 상태로 삼고 버퍼를 처리한다
  - 메시지: epoch가 다르면 재동기화 → HEARTBEAT의 seq가 반영한 seq보다 크면 재동기화 → seq ≤ 반영한 seq면 버림 → seq = 반영 + 1이면 반영 → 그 밖(갭)은 재동기화
  - RUNNING·PAUSED 레인에서 비컨이 2 × heartbeatIntervalMs 동안 오지 않으면 재동기화한다
- 레인 화면 상태는 레인 이벤트와 스냅샷으로만 바꾼다. 입찰 응답으로 바꾸지 않는다
- error.lane을 받은 거절은 스냅샷을 다시 부르지 않고, 같은 epoch에서 그 seq까지 반영되기를 기다린다
- 카운트다운은 deadlineAt·gapEndsAt과 serverTime 표본으로 추정한 서버 시각으로 그린다. 0이 되어도 LOT_CLOSED를 받기 전에는 결과를 표시하지 않는다
- 입찰은 클릭마다 새 Idempotency-Key(crypto.randomUUID())를 만든다. 응답이 없거나 500 BID_RESULT_UNKNOWN이면 처음 요청 뒤 10초 안에서 같은 키·같은 본문으로 재전송한다. 그 밖의 4xx와 503은 재전송하지 않는다
- 입찰 버튼 상태는 EVENTS 부록 B.3 표로 계산한다 (요청·재전송 중 비활성화 포함)
- 레인(1~4개)은 서로 독립적으로 갱신된다. 한 레인의 이벤트가 다른 레인을 다시 렌더링하지 않게 한다
- 연결이 끊기면 상단에 재연결 배너를 띄우고 입찰 버튼을 비활성화한다. 지수 백오프 + 무작위 지연으로 재연결하고, 재연결 뒤 레인을 다시 구독하고 회차 상세 한 번으로 모든 레인을 다시 맞춘다

## 금지
- [FE] 단계에서 backend/ 폴더 수정. API나 이벤트 필드가 부족하면 멈추고 무엇이 필요한지 제안한다
