# Frontend — React + Vite + TypeScript + Tailwind

## 목표
백엔드 기능을 정확하게 시연하는 깔끔한 UI. 화려함보다 실시간 반영의 정확성과 완성도.

## 구조
src/
├── api/generated/    OpenAPI에서 자동 생성 (직접 수정 금지)
├── api/errorMessages.ts   백엔드 에러코드 → 한국어 메시지 매핑 (이 파일 하나로 관리)
├── realtime/         STOMP 클라이언트, 이벤트 타입(events.ts), 레인 구독 훅
├── components/ui/    공통 컴포넌트 (Button, Card, Modal, Toast, Badge, Spinner, EmptyState)
├── features/lane/    레인 카드, 카운트다운, 입찰 버튼, 입찰 피드
├── features/admin/   운영자 화면
└── pages/

## 규칙
- REST 호출은 생성된 클라이언트만 사용한다. 서버 상태는 TanStack Query로 관리한다
- 색상, 간격, 폰트는 tailwind 설정의 디자인 토큰만 사용한다. 임의의 색상값은 금지
- 모든 화면에 로딩 / 빈 상태 / 에러 상태를 처리한다

## 실시간 규칙
- 레인 초기 상태는 스냅샷 API로 받고, 이후 변경은 WebSocket 이벤트로 반영한다
- 이벤트 seq가 건너뛰면 해당 레인의 스냅샷을 다시 요청한다. seq가 이미 반영한 값 이하이면 무시한다
- 카운트다운은 서버 시각 오프셋을 보정해서 endsAt 기준으로 계산한다. 클라이언트 시계를 그대로 쓰지 않는다
- 입찰 버튼은 요청 중에 비활성화하고, 클릭마다 새 Idempotency-Key(UUID)를 만든다
- 4개 레인은 서로 독립적으로 갱신된다. 한 레인의 이벤트가 다른 레인을 다시 렌더링하지 않게 한다
- 연결이 끊기면 상단에 재연결 배너를 띄우고, 재연결 후 4개 레인 스냅샷을 다시 받는다

## 금지
- backend/ 폴더 수정. API나 이벤트 필드가 부족하면 멈추고 무엇이 필요한지 제안한다