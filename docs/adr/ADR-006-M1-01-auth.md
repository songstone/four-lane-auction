# ADR-006: M1-01 인증과 Refresh Token 회전

- 상태: 사용자 전체 승인 (2026-10-27, Asia/Seoul). 구현·실제 검증·기록·로컬 커밋 승인. push·PR 생성은 별도 승인.
- 시작 SHA: `2ade8160d6b6c8160d905f01e5fd5dc6264497d8`, 최신 dev와 origin/dev 일치, clean. 기본 checkout의 `feature/M1-01-auth`.
- 선행 M0-03 완료 커밋 `51b591500624a696e68b80ea4d13e5480c4f5e74` 포함 확인.

## 대안과 승인한 선택

| 항목 | 선택과 이유 | 비교한 대안 |
|---|---|---|
| JWT | Boot 4.1.2 Resource Server + Nimbus, 공용 JwtDecoder. M1-02가 같은 빈을 재사용 | jjwt 0.13.0은 별도 파서·Decoder 어댑터·패키지 필요 |
| 버전 | starter 4.1.2, Security 7.1.2, 전이 Nimbus 10.9.1, security-test starter 4.1.2. BOM 우선 | 개별 최신 버전은 사용하지 않음 |
| 시드 | 명시적 admin-seed 프로필, 환경변수 이메일·BCrypt 해시. 기본 비활성. 기존 ADMIN 재실행 무해, USER 승격·비밀번호 덮어쓰기 금지 | Flyway placeholder/Java 마이그레이션은 환경별 비밀값과 스키마 이력 결합 |
| 비밀번호 | DelegatingPasswordEncoder의 BCrypt, 8~64 코드 포인트·UTF-8 최대 72바이트 | 사전 해시 인코더는 별도 형식·유지보수 필요 |
| 닉네임 | 2~20 코드 포인트, 중복 허용 | 중복 금지는 새 제약·오류 계약 필요 |
| 쿠키 | HttpOnly, SameSite=Strict, Path=/api/auth. prod Secure=true, local/test만 false | 개발 HTTPS는 인증서·TLS 설정 범위 증가 |
| Refresh 수명 | 최초 로그인부터 절대 7일. 회전은 최초 expires_at 유지, 쿠키 Max-Age는 남은 기간 | 회전마다 7일 연장은 무기한 연장 가능, 절대 30일은 사용 가능 기간이 김 |
| 시간 | 같은 주입 Clock, exp 정각부터 거절. 유예 0초. JWT NumericDate 정밀도에 맞춰 발급 시각을 초로 자름 | 기본 60초 유예는 명시한 15분을 넘김 |
| 회전 | application @Transactional, 해시로 Refresh 행 비관적 잠금 → Clock 읽기 → 유효성 판단 → 이전 폐기와 신규 저장. 컨트롤러는 커밋 후 쿠키·응답 작성 | 조건부 UPDATE는 락 대기 뒤 만료 판단과 JPA 상태 동기화가 추가됨 |
| 경합 결과 | 같은 토큰 두 요청 중 하나만 200, 나머지 401. 실패 응답은 쿠키를 지우거나 덮어쓰지 않음 | 유예 재사용·전체 토큰 폐기는 기존 B-15 결정과 달라 도입하지 않음 |
| warningCount | V5에 member_warning만 선반영하고 실제 COUNT 조회 | member 카운터는 원본 정의·갱신 방식 변경, 거래 전체 선반영은 범위 확대 |
| FE | 생성 HTTP 함수만 사용. 탭 내부 Promise 공유·늦은 401의 토큰 비교, Web Locks로 탭 간 직렬화. 쿠키를 바꾸는 로그인·로그아웃도 같은 락 | 새 라우터·HTTP 라이브러리 없음. hash 경로로 서버 fallback 변경 불필요 |

## 경계와 실패 상황

- V1~V4는 수정하지 않는다. V5 member_warning은 member FK·trade_id UNIQUE를 적용한다. trade가 아직 없으므로 **trade FK 추가는 M3-07 필수 후속**이다. 인증에는 경고 쓰기 API·패널티 정책이 없다. auth → member 의존을 명시적으로 허용한다.
- Refresh 락을 얻은 뒤 시각을 읽으므로 대기 중 만료된 토큰은 거절된다. 새 행 저장·커밋 실패는 이전 폐기도 롤백한다. 커밋 뒤 응답이 유실되면 구 토큰은 재사용할 수 없어 재로그인이 필요할 수 있다. 유예 정책으로 숨기지 않는다.
- JWT는 초 단위 NumericDate, DB는 밀리초다. 발급 응답의 만료와 JWT exp는 같은 시각이다. Clock 역행은 토큰 유효 시간을 늘릴 수 있고, 앞으로 점프하면 일찍 만료된다. 레인 OQ-35는 이번에 정하지 않는다.
- Access는 메모리만, Refresh는 쿠키와 DB 해시만. 로그에 요청 본문·Authorization·쿠키·예외 원문을 기록하지 않는다. 기본 운영 로그에서 실제 경로를 캡처해 검증하며 임의의 외부 DEBUG 설정까지 보장하지 않는다.
- M0-03 공개 목록·OpenAPI·익명 STOMP·관리 health를 보존한다. M1-01 당시에는 프론트 STOMP에 토큰을 붙이지 않았다. 후속 **M1-02 처리 완료**: CONNECT 임시 거절 제거, 공용 Decoder·Principal 변환기, 인증 개인 큐와 프론트 신원 전환을 구현·검증했다 ([ADR-007](ADR-007-M1-02-stomp-auth.md), PROGRESS M1-02).
- ADR-005의 운영 HTTP에서는 Secure 쿠키·Web Locks를 전제로 한 인증 검증을 완료할 수 없다. 운영 인증에는 HTTPS가 필요하다. TLS 설치·서버 배포는 이번 범위 밖이다.

## 출처와 증거

- [Boot BOM](https://repo.maven.apache.org/maven2/org/springframework/boot/spring-boot-dependencies/4.1.2/spring-boot-dependencies-4.1.2.pom), [Security JOSE POM](https://repo.maven.apache.org/maven2/org/springframework/security/spring-security-oauth2-jose/7.1.2/spring-security-oauth2-jose-7.1.2.pom): 2026-10-27 조회.
- [Spring JWT](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html), [BCrypt 7.1.2 소스](https://github.com/spring-projects/spring-security/blob/7.1.2/crypto/src/main/java/org/springframework/security/crypto/bcrypt/BCrypt.java), [Web Locks](https://developer.mozilla.org/en-US/docs/Web/API/Web_Locks_API).
- API 0.4를 먼저 수정하고 BE → GEN → FE → 통합으로 진행한다. 실제 결과는 PROGRESS M1-01에 기록한다. CHALLENGES의 미결 후보는 새로 선택하지 않았다.
