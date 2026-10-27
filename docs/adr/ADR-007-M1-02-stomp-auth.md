# ADR-007: M1-02 STOMP 연결 인증과 신원 전환

- 상태: 사용자 승인 (2026-10-27, Asia/Seoul). 권장안 A로 구현·검증·기록·로컬 커밋 승인. push·PR 생성은 별도 승인이다.
- 범위: TASKS M1-02, PRD AC-11-1·AC-11-6·OQ-39, EVENTS 1.2·1.3·1.6, ARCH 8장, CH-08의 이미 결정된 인증 규칙.
- 시작 SHA: `3e254b31e14ecc5c133cb2be4010755f0d0a88c8`. fetch 후 dev=origin/dev, clean 확인. M1-01 PR #4 머지 포함 확인 후 기본 checkout에서 `feature/M1-02-stomp-auth` 생성.

## 결정과 비교

| 항목 | 승인한 선택과 이유 | 비교한 대안 |
|---|---|---|
| Principal 변환 | A: 기존 REST의 `JwtAuthenticationConverter`를 공용 빈으로 추출해 REST·STOMP가 공유한다. 이름은 JWT sub(회원 ID), 역할은 ROLE_USER·ROLE_ADMIN이다 | B: STOMP에서 별도 Principal을 생성하면 변경 파일은 적지만 역할 변환이 중복된다 |
| JWT 검증 | ADR-006의 공용 JwtDecoder·AccessTokenValidator·Clock을 그대로 사용한다 | 별도 JWT 파서는 검증·만료 규칙이 어긋날 수 있고 기존 결정과 다르다 |
| 오류 | EVENTS 1.3과 ARCH 8.2가 TOKEN_EXPIRED·TOKEN_INVALID로 일치함을 구현 전에 확인했다. 검증된 만료 오류만 TOKEN_EXPIRED, 나머지는 TOKEN_INVALID다 | 계약 값 변경은 하지 않는다 |

새 의존성·Flyway·REST·이벤트 스키마 변경은 없다. TASKS 0.2의 입찰 v1, 기존 ADR, OQ-39를 다시 선택하지 않는다. CH-08의 재동기화·스냅샷·성능 후보는 선택하지 않는다.

## 서버 경계

- HTTP Upgrade의 신원과 관계없이 CONNECT에서 신원을 정한다. Authorization이 없으면 익명이고, 빈 값·잘못된 Bearer·중복 헤더·서명/claim 오류는 거절한다. 인증 헤더는 디코딩 전에 브로커 헤더에서 제거하고 오류 예외에는 토큰·Decoder 예외 원문을 넣지 않는다.
- `/ws`만 HTTP Bearer 추출을 생략해 컨테이너의 HTTP Principal이 STOMP로 상속되지 않게 한다. 다른 REST 경로의 Bearer 검증은 그대로다. 핸드셰이크 핸들러에서 null만 반환하면 StandardWebSocketSession이 native session의 Principal로 다시 채우므로 충분하지 않음을 실제 WebSocket 회귀 테스트로 확인했다.
- CONNECT에서 설정한 Principal이 후속 프레임과 사용자 목적지에 이어진다. 숫자 레인 토픽은 공개, 개인 큐는 인증된 Principal만 허용한다. ADMIN도 서버 구독 권한은 있지만 정상 프론트는 USER만 개인 큐를 구독한다.
- 토큰 검증은 CONNECT에서만 한다. 기존 연결의 토큰 만료는 연결·개인 큐를 끊지 않으며 이후 REST 요청은 독립적으로 검증한다. 신원 변경은 이전 연결을 닫고 새로 연결한다.
- Spring Security 7.1의 JwtAuthenticationConverter가 추가하는 FACTOR_BEARER 권한도 REST·STOMP에 동일하게 적용된다. 새 제품 역할을 추가한 것이 아니다.
- 실제 알림 생성·DB 알림함·화면 표시는 M3-06이다. 테스트의 개인 메시지는 src/test에서만 발행한다.

## 프론트 경계

- Access Token과 응답의 accessTokenExpiresAt은 메모리에만 보관한다. JWT 본문을 해석하지 않는다. beforeConnect에서 최신 토큰을 읽고 만료했거나 60초 안에 만료하면 기존 재발급 경로를 사용한다.
- 인증 오류에는 재발급을 시도하고, 복구가 실패하면 비로그인 상태로 바꿔 Authorization 없는 CONNECT를 보낸다. 동일한 거절 토큰의 무한 재연결을 막는다.
- 로그인·로그아웃·회원 변경은 연결 신원을 바꾼다. 같은 회원의 정상 REST 재발급만으로 연결을 교체하지 않는다. 이전 연결 종료를 기다리며 늦은 비동기 완료는 이전 세대의 연결을 다시 열지 못한다.
- USER 연결은 앱에서 유지한다. 홈의 비로그인·ADMIN 진단 연결은 ADR-003의 임시 예외를 유지한다. 관전/진행 제어 화면 기준으로 그 수명을 정리하는 것은 M1-08이다.

## 실패할 수 있는 상황과 검증 경계

- 연결 중 만료를 유지하므로 이미 연결된 사용자의 Access Token을 폐기하는 장치는 아니다. 로그아웃은 현재 클라이언트 연결을 바꾸고 Refresh를 폐기한다. 다른 탭·탈취된 Access를 즉시 무효화하지 않는 ADR-006의 범위는 유지한다.
- 서버 Clock이 앞으로 점프하면 새 CONNECT가 일찍 만료될 수 있고 역행하면 토큰이 더 오래 유효할 수 있다. 기존 연결은 영향받지 않는다. 레인 시계 점프 OQ-35는 정하지 않는다.
- 브라우저 시계에 따른 사전 재발급은 연결 준비를 위한 추정이다. 실제 유효성은 서버 Decoder가 판단하고 ERROR 복구 경로로 보완한다.
- WebSocket 종료·토큰 재발급·로그아웃이 겹칠 수 있다. 세대 확인과 기존 소켓 종료 대기가 누락되면 과거 신원이 다시 연결되거나 탭당 소켓이 중복될 수 있다.
- 이 작업은 새로운 업무 트랜잭션·락·경매 타이머를 만들지 않는다. Refresh 회전의 트랜잭션과 Web Locks는 ADR-006을 재사용한다.

실제 테스트·curl·브라우저 결과와 NOT_RUN은 PROGRESS M1-02에 기록한다.

## 공식 근거

- [Spring CONNECT 토큰 인증과 후속 프레임의 Principal](https://docs.spring.io/spring-framework/reference/web/websocket/stomp/authentication-token-based.html)
- [Spring 사용자 목적지와 여러 세션 전달](https://docs.spring.io/spring-framework/reference/web/websocket/stomp/user-destination.html)
- [JwtAuthenticationConverter 7.1.2](https://docs.spring.io/spring-security/reference/api/java/org/springframework/security/oauth2/server/resource/authentication/JwtAuthenticationConverter.html)
- [stompjs 7.5.0 Client / beforeConnect](https://stomp-js.github.io/api-docs/latest/classes/Client.html)
