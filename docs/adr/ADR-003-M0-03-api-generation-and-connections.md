# ADR-003: M0-03 회차 조회·API 생성·연결 진단

- 상태: 승인됨 (사용자, 2026-10-27)
- 범위: [TASKS M0-03](../TASKS.md#m0-03-연결-확인-화면-rest--websocket)
- 시작 SHA: `354b2ae36bf53c96b6a1dad4720a79fd8ee6aa3b` (`dev`, clean)
- 원본 계약: API 1.2·1.4·1.5·4.1, EVENTS 1장. 사용자 보충과 아래 임시 제한을 함께 승인받았다.

## 선택과 비교

| 항목 | 승인한 선택 | 비교한 대안과 이유 |
|---|---|---|
| 생성·HTTP | Orval 8.45.0 + 브라우저 Fetch | openapi-typescript 7.13.0 + openapi-fetch 0.17.0은 생성 타입과 범용 런타임 클라이언트 조합이다. 엔드포인트별 요청 함수까지 생성하는 Orval을 선택했다. Orval만 개발 의존성으로 넣고 런타임 HTTP 패키지는 추가하지 않는다. 화면·훅이 직접 fetch하지 않는다 |
| springdoc | springdoc-openapi-starter-webmvc-api 3.2.0 | 3.1.1도 Boot 4.1 라인이다. 3.2.0은 공식 릴리스가 Boot 4.1.2를 기준으로 빌드되었다고 명시해 현재 조합과 맞는다. Swagger UI는 이번 범위에 필요하지 않다 |
| STOMP | @stomp/stompjs 7.5.0, Boot 4.1.2 BOM의 WebSocket starter | 사용자 지정. 순수 WebSocket, STOMP 1.2, Simple Broker, 하트비트 10초, 탭당 연결 하나를 따른다 |
| 커서 | 상태·정렬 시각·ID를 넣은 버전 있는 Base64URL 키셋 커서 | offset을 감추는 커서는 앞 페이지 삽입·삭제에 취약하다. 예정은 시각·ID 오름차순, 진행·종료는 내림차순. 상태가 다른 커서와 잘못된 커서는 400 INVALID_REQUEST다. 커서는 암호나 권한 증명이 아니며 클라이언트는 해석하지 않는다 |

## 구현 경계

- 기존 테이블의 조회용 JPA 매핑과 `@Query` 프로젝션만 추가한다. 생성·상태 전이·마이그레이션 변경은 없다. application은 domain 조회 포트를 사용하고 infra가 구현한다. 조회 서비스는 `@Transactional(readOnly = true)`다.
- `size + 1`행으로 다음 페이지 존재를 판정한다. 커서가 마지막 행의 정렬 값을 담으므로 그 행이 삭제되어도 이어서 조회할 수 있다. 페이지 간 동일 스냅샷을 보장하는 것은 아니다. 회차 상태·정렬 시각 변경에 따른 목록 이동은 API 4.1의 재조회로 갱신한다.
- REST 진단은 상태별 `GET /api/sessions`의 실제 결과로 한다. WebSocket 진단은 STOMP CONNECTED 수신으로 한다. 진단 API·테스트 토픽은 추가하지 않는다.
- `serverTime`은 주입된 Clock으로 만들며 응답 시각은 기존 UTC 밀리초 3자리 직렬화를 사용한다. 화면은 회차 목록 응답의 마지막 수신값을 표시한다.
- Orval 입력은 실행 중인 백엔드 `/v3/api-docs`, 출력은 `frontend/src/api/generated/`다. 생성 파일을 커밋하고 직접 수정하지 않는다. 생성 HTTP 호출은 같은 출처의 상대 경로다.
- 브라우저 brokerURL은 현재 location에서 만든 같은 출처의 `/ws`다. Vite의 기존 `/api`·`/ws` 프록시를 사용하고 CORS는 추가하지 않는다.
- 허용 Origin은 `WS_ALLOWED_ORIGINS`(쉼표 구분)로 받는다. local 기본값은 `http://127.0.0.1:5173`, test는 같은 값이며 prod는 명시값이 필수다. 빈 목록과 wildcard는 받지 않는다. Compose의 .env는 bootRun이 자동으로 읽지 않으므로 변경 시 프로세스 환경변수로 전달한다.
- 숫자 레인 토픽만 구독을 허용한다. 모든 SEND와 허용 목록 밖 SUBSCRIBE는 ERROR 뒤 연결을 닫는다. 수신 상한은 ARCH 7.8의 8KB다.

## 승인한 임시 제한과 후속 작업

1. **M1-02 — 처리 완료 (2026-10-27, [ADR-007](ADR-007-M1-02-stomp-auth.md))**: M0-03 당시 Authorization 헤더가 있는 CONNECT를 값이 비어 있어도 `TOKEN_INVALID`로 거절하던 임시 제한을 제거했다. REST와 같은 Decoder·Principal 변환기로 유효 토큰 허용, 만료/위조 구분, 인증된 개인 큐를 구현했다. Clock 이동·실제 WebSocket·화면 검증 결과는 PROGRESS M1-02에 기록했다. 위 M0-03의 실행 증거는 당시 범위 그대로다.
2. **M1-08**: 홈의 익명 연결은 M0 진단용으로만 승인했다. EVENTS 1.1의 비로그인 연결 시점(관전 화면)과 다르므로 관전 화면 구현 때 연결 수명을 정리한다.

이 결정은 CHALLENGES의 엔진·입찰·타이머·재동기화·성능 후보를 선택한 것이 아니다. 버전 출처·실행 증거·수용 기준과 직접 확인 방법은 [PROGRESS](../PROGRESS.md)에 기록한다.
