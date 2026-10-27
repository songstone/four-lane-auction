# ADR-001: M0-01 저장소 뼈대와 로컬 실행 기준

- 상태: 승인됨 (사용자, 2026-10-27)
- 범위 원본: [TASKS M0-01](../TASKS.md#m0-01-저장소-뼈대와-로컬-인프라)
- 시작 SHA: `8482eebbe55f9bd4ea54d9a79f96674047fdf213` (`main`, 기존 작업 트리 clean)

## 배경과 대안

M0-01 착수 전에 사용자에게 변경 파일, 의존성, 구현·검증 계획과 아래 대안을 제시했고 승인을 받았다.

| 항목 | 승인한 선택 | 비교한 대안과 이유 |
|---|---|---|
| 모듈 | Java 21, Gradle Kotlin DSL, 순수 Java `engine` + Spring `app` | TASKS 0.2·M0-01과 사용자 지시로 결정됨. Spring·JPA·Jackson은 engine의 운영 클래스패스에 두지 않는다 |
| 테스트 | JUnit 6, ARCH 1.3의 JUnit 5 표기 수정 | JUnit 5 유지: Spring Framework 7의 SpringExtension 기준(JUnit 6 이상)과 충돌. Boot 4를 유지하므로 JUnit 6을 선택 |
| 버전 | Boot 4.1.2 BOM 조합 고정 | 개별 최신 라이브러리로 덮어쓰기: Boot 권장 조합을 벗어나 호환 검증이 늘어난다. 사용자 승인으로 개별 최신 요구의 예외를 둔다. 버전·출처는 PROGRESS에 기록 |
| 포트 | 앱 8080, 관리 8081, 프론트 5173, MySQL 3306 | 다른 번호도 가능하나 기존 문서 예시와 맞춘다. 로컬 관리 포트·DB는 loopback에 바인딩 |
| 프론트 | React/Vite/TypeScript/Tailwind/TanStack Query와 필수 빌드·타입 의존성만 | 라우터·UI 라이브러리 선도입: 빈 레이아웃에는 사용처가 없다. UI 7종은 직접 작성 |

## 설정과 경계

- 프로필은 `local`(기본), `test`, `prod`. 커밋되는 `application.yml`의 프로필별 문서에 둔다. `application-local.yml`은 무시되는 선택적 개인 설정이다.
- 새 clone은 `.env`·`application-local.yml` 없이 Compose와 bootRun의 같은 로컬 기본값으로 실행한다. 이 값은 공개된 개발용 자격 증명이다. Compose `.env`는 bootRun이 읽지 않는다. 재정의 시 같은 프로세스 환경변수를 전달한다.
- prod DB URL·사용자·비밀번호와 앱·관리 포트·관리 바인딩 주소는 환경변수로만 받는다. JDBC 세션과 Hibernate의 시간대는 공통으로 UTC를 강제한다.
- `Clock.tick(Clock.systemUTC(), Duration.ofMillis(1))` 빈 하나를 둔다. Jackson 3에는 `Instant` 응답을 UTC·밀리초 3자리로 쓰는 serializer를 등록한다.
- 에러 코드와 HTTP 상태는 API 9장을 따른다. MVC 형식 오류는 `400 INVALID_REQUEST`, 없는 경로는 `404 NOT_FOUND`, 예측하지 못한 예외는 `500 INTERNAL_ERROR`로 정규화한다. 추가 `fields`는 400에서만, `lane`은 전달된 경우에만 포함한다. 내부 예외 정보는 응답에 쓰지 않는다.
- Compose에는 MySQL 8.4 LTS만 둔다. 패치와 이미지 digest를 고정하고 named volume, 실제 사용자 SELECT 기반 healthcheck를 둔다.
- Vite의 `/api`, `/ws` 프록시가 같은 출처를 만든다. `/ws`는 `ws: true`. 백엔드 주소는 Vite 설정의 프록시 대상에서만 환경변수로 읽는다. 클라이언트에는 백엔드 URL을 넣지 않는다.
- Tailwind 4의 Vite 플러그인과 `@config`로 중앙 디자인 토큰을 사용한다.

## 후속 작업

M0-01에는 엔티티·스키마·제품 API·실시간 기능을 구현하지 않는다. Flyway와 Testcontainers는 M0-02, WebSocket·springdoc·API 타입 생성은 M0-03, security는 M1-01이다. 테스트 전용 MVC probe는 `src/test`에만 있다. 동시성·타이머·이벤트 구조와 CHALLENGES의 미결 후보를 새로 선택하지 않는다.

검증 결과와 재현 방법은 [PROGRESS](../PROGRESS.md)에 기록한다.
