# Four-Lane Auction — 진행·검증 기록

## M1-01 회원가입·로그인·재발급·로그아웃

- 작업일: 2026-10-27 (Asia/Seoul).
- 상태: **완료 (M1-01 범위, 아래 수용 기준과 실제 실행 증거 기준)**. push·PR 생성·원격 CI는 아직 실행하지 않았다.
- 원본: TASKS 0장·M1-01·9장 B-5, PRD M-01·AC-01-1/2/4/5·P-02, API 1.3·3장, ARCH R10·11장, CH-01. AC-01-3 입찰 거절은 M2-06이다.
- 시작 SHA: `2ade8160d6b6c8160d905f01e5fd5dc6264497d8`. fetch 뒤 dev=origin/dev, clean 상태에서 `feature/M1-01-auth` 생성. 직접 선행 M0-03의 `51b591500624a696e68b80ea4d13e5480c4f5e74` 포함을 Git ancestry와 완료 기록으로 확인했다.
- 작업 위치: 기본 checkout `C:\Users\jitin81\Documents\dev\lab\four-lane-auction`, 별도 worktree 없음.
- 승인: 사용자가 권장안 전체와 비밀번호 UTF-8 72바이트 제한, Refresh 절대 7일, member_warning V5 선반영을 명시 승인했다. 구현·실제 검증·기록·로컬 커밋까지 승인, **push·PR 생성은 별도 승인**이다.
- 결정 기록: [ADR-006](adr/ADR-006-M1-01-auth.md), 제품 OQ-49~51. API 0.4를 BE보다 먼저 반영했다. CHALLENGES의 새 미결 후보는 선택하지 않았다.
- 로컬 커밋: `d255571` 결정·계약, `4f13ec2` BE, `fcbc0c3` GEN, `f8284f3` FE. 최종 증거 문서는 후속 docs 커밋이다. 원격 push·PR·머지·태그는 실행하지 않았다.

### 승인한 구현과 경계

- Nimbus Resource Server와 공용 JwtDecoder. HS256 키는 Base64 환경변수이며 256비트 이상이어야 한다. 서명뿐 아니라 iss·aud·sub·role·token_use·iat·exp를 검증한다. M1-02는 이 Decoder를 그대로 사용한다.
- Access 15분, Refresh 로그인부터 절대 7일. 회전해도 최초 expires_at을 유지한다. Access는 JWT NumericDate에 맞춰 발급 시각을 초로 자르고, 응답과 JWT의 만료는 같은 Instant다. 둘 다 주입 Clock으로 판단하고 만료 정각부터 무효, 유예 0초다.
- Refresh 해시 행의 비관적 잠금을 얻은 뒤 Clock을 읽고, 이전 폐기와 새 행 저장을 application 트랜잭션 하나에서 처리한다. 컨트롤러의 쿠키·응답 작성은 커밋 성공 뒤다. 같은 토큰의 경합은 200 한 건·401 한 건이며, 실패 응답은 쿠키를 덮어쓰지 않는다.
- BCrypt와 API 0.4의 입력 검증: 비밀번호 8~64 코드 포인트·UTF-8 최대 72바이트, 닉네임 2~20 코드 포인트·중복 허용. USER 가입 고정, ADMIN은 명시적 admin-seed만 허용한다.
- V5는 member_warning의 실제 조회 원본을 만든다. member FK와 trade_id 유니크를 적용했다. trade가 아직 없으므로 **M3-07에서 fk_warning_trade 추가가 필수**다. 경고 쓰기·패널티 정책·거래 구현은 넣지 않았다. V1~V4 수정은 없다.
- FE는 생성 함수만 호출한다. 탭 내부의 진행 중 Promise와 요청 당시 토큰 비교로 늦은 401도 합류시킨다. 탭 사이는 Web Locks `auth-refresh`로 직렬화하고 로그인·로그아웃도 같은 락을 쓴다. Access는 메모리, 회원 데이터는 TanStack Query의 `['me']` 캐시다. 토큰 내용은 해석하지 않는다.
- 익명 STOMP 진단과 토큰 CONNECT 임시 거절은 그대로다. 인증 CONNECT·개인 큐·신원 전환은 M1-02다. 관전 진입에 맞춘 연결 수명 정리는 M1-08이다.

### 단계별 실제 실행 증거

| 단계 | 결과와 실제 실행 | 증거 |
|---|---|---|
| BE | `./gradlew.bat clean build` 성공. 14 actionable tasks 모두 실행. 시간 출처 55파일, 위반 0 | `output/m1-01/be-clean-build.log` |
| JUnit | UTC test 103회 + 별도 JVM 서울 testSeoul 103회 = 총 206회 실행, 각 failures/errors/skipped 0. 같은 103개를 두 JVM에서 실행한 횟수다 | `output/m1-01/test-summary.md`, app/build/test-results의 XML |
| 실제 MySQL | JVM마다 기존 Schema/Session 35회 + 신규 Auth 13회·Concurrency 2회 = 50회. CI 검증기의 MySQL 35 표시는 기존 필수 두 클래스의 분류이며 신규 두 클래스도 실제 Testcontainers를 사용한다 | 위 XML과 MySqlIntegrationTest 상속 관계 |
| 실제 TCP 경합 | JVM마다 HTTP 경합 500회. 각 회 같은 쿠키 두 요청 중 200 한 건·401 한 건, 활성 행 한 개, 실패 응답 Set-Cookie 없음. 합계 1,000회 경합. 마지막 승자의 쿠키로 추가 재발급 200 | RefreshRotationConcurrencyTest |
| ADMIN 시드 | 실제 local,admin-seed 비웹 bootRun이 성공·종료하고 이후 정상 bootRun에서 ADMIN 로그인 200. 기존 USER 승격 거절·기존 ADMIN 해시 유지·재실행은 MySQL 통합 테스트에서 확인 | `output/m1-01/seed-admin.log`, AuthHttpIntegrationTest |
| 실제 curl | 가입→로그인→me→회전→구 쿠키 거절→로그아웃→재발급 거절, 공개 경로·오류·키·로그 등 22항목 PASS | `output/m1-01/curl-checks.json` |
| GEN | 실제 8080/v3/api-docs에서 Orval 8.45.0으로 생성, typecheck 성공, 생성 코드 별도 커밋. 수동 수정 없음 | `output/m1-01/gen-api.log`, `gen-typecheck.log`, `fcbc0c3` |
| FE | 최종 lint·typecheck·build 성공. 새 npm 의존성 없음. 수기 fetch와 토큰의 localStorage/sessionStorage 사용 0 | `output/m1-01/final-{lint,typecheck,frontend-build}.log` |
| 화면 | 실제 Chrome+bootRun+Vite의 18항목 PASS. 응답 합성 없이 가입 201·필드 오류 400·중복 409·로그아웃 204·내 정보 재조회 200을 확인 | `output/playwright/m1-01/ui-evidence.json` |
| 자연 만료·401 합류 | 실제 16분(960,018ms)을 기다린 뒤 보류한 인증 요청 3개를 전달. 실제 401 세 건 → Refresh 한 건 200 → 원래 요청 세 건 200, 로그인 유지·JS 예외 0 | `output/playwright/m1-01/natural-expiry.json`, `natural-expiry-run.log` |
| 재발급 중 로그아웃 | 실제 Refresh 200 응답을 보류한 동안 UI 로그아웃. held 1·pending 1, 늦은 복구 결과 수락 false, 서버 로그아웃 204·쿠키 없음·새로고침 비로그인 | `output/playwright/m1-01/logout-refresh-evidence.json` |
| 키 설정 | 키 없는 별도 JAR 기동 실패. prod Compose는 키 없으면 거부, 설정하면 config 통과. 컨테이너 새 이미지 실행 증거는 아니다 | `missing-key-startup.json`, `compose-key-checks.json` |
| 민감값 | 실제 자격·키·토큰/해시 13개 값을 seed/bootRun 로그와 Git 추적 파일에서 검색해 일치 0. 기본 애플리케이션 로그 범위다 | `output/m1-01/credential-scan.json` |

engine은 아직 도메인 코드가 없어 test NO-SOURCE이며 엔진 기능 검증을 뜻하지 않는다. 새 JAR에는 인증 클래스와 의존성이 포함되지만, 새 Docker 이미지 빌드·서버 배포·운영 TLS 검증은 NOT_RUN이다.

### 수용 기준별 판정

| 기준 | 결과 | 근거 |
|---|---|---|
| AC-01-1 로그인 시 두 토큰 | PASS | BCrypt 저장·로그인·쿠키 속성·DB SHA-256 검사, 실제 curl과 UI의 로그인 200 |
| AC-01-2 Access 만료 후 재발급 | PASS | 주입 Clock을 16분 이동한 Spring Security 필터·MySQL 테스트. 만료 Access로 me/공개 목록 401, 유효 Refresh로 200, 새 Access로 me 200. 별도 JwtClockTest는 2020년 Clock을 써 실제 벽시계 사용을 검출 |
| AC-01-4 ADMIN 가입 불가 | PASS | role=ADMIN을 보낸 실제 가입 응답·DB가 USER. ADMIN 시드는 별도 프로필·환경변수만 사용 |
| AC-01-5 로그아웃 유지 | PASS | 실제 204·쿠키 삭제·구 쿠키 401, 두 탭 새로고침 뒤 비로그인. 기존 Access는 여전히 유효한 것도 검증 |
| 폐기 Refresh 거절 | PASS | 회전 구 쿠키·로그아웃 쿠키·없는/잘못된 쿠키 모두 401. 구 쿠키 실패에 Set-Cookie 없음 |
| 공개 API의 만료 토큰 | PASS | Clock 조작 필터 통합에서 익명 200·만료 401. 실제 curl의 위조 토큰도 401. 원래 공개 경로·M0-03 테스트 모두 유지 |
| 비밀번호·토큰 로그 비노출 | PASS | AuthHttpIntegrationTest의 로그인·회전·거절·로그아웃 실제 토큰 캡처, AuthControllerTest의 잘못된 JSON·필드 오류·인증 실패·민감 예외 캡처. 실제 기동 로그 검색도 별도 PASS |
| Access/Refresh Clock·정각 | PASS | Access 만료 1ms 전/정각, Refresh 7일 1ms 전/정각/직후. 대기 중 만료된 Refresh는 잠금 해제 뒤 거절 |
| 동일 Refresh 동시 회전 | PASS | 두 JVM의 실제 MySQL HTTP 경합 각 500회. 임시 DB CHECK로 신규 저장 실패를 주입하면 기존 폐기도 롤백되어 같은 구 토큰이 다시 성공 |
| 같은 탭 여러 401의 재발급 1회 | PASS | 독립된 headless Chrome의 실제 자연 만료. 요청 3개 401·Refresh 1개 200·재시도 3개 200, 로그인 유지 |
| 탭 간 Web Locks | PASS | 두 탭 동시 새로고침 5회, Refresh 10건 모두 200·동시 최대 1. 실제 첫 응답 보류 중 held 1·pending 1·서버 요청 1 확인 |

### 화면 검증의 범위와 재현 중 수정

- 자연 만료 검증은 2026-10-27 16:14:08.532 KST에 시작했다. 서버 응답의 Access 만료는 16:29:08.000, 요청 전달은 16:30:08.549였다. 서버 Clock·브라우저 Date·응답 본문/상태를 바꾸지 않고 실제 요청 전송만 보류했다. Cookie는 유효한 실제 로그인에서 받았고, 애플리케이션은 Access를 브라우저 메모리에만 보관했다. Chrome은 실제 실행 버전 155.0.8059.86이다.

- 1440×960과 390×844에서 실제 화면을 캡처하고 직접 열어 배치·줄바꿈을 확인했다. 모바일 내 정보와 로그인 문서 폭은 viewport 폭과 같았다. USER의 내 정보·ADMIN의 운영자 계정 메뉴, 원래 화면 복귀, 로그인 유지, 로그아웃 뒤 두 탭의 다음 복구 실패, JS 영속 저장소 비사용을 확인했다.
- 계정 조회의 전송 실패는 브라우저에서 요청을 거절해 주입했다. 오류 화면과 다시 조회를 확인한 뒤 실제 backend 200으로 복구했다. 로딩은 실제 Refresh 200 응답 전달만 보류해 관찰했다. 서버 응답 본문·상태를 만들어서 통과시키지 않았다.
- 초기 트리거 기반 INSERT 실패 주입은 MySQL binary logging의 SUPER 권한 요구로 실패했다. 권한을 올리지 않고 테스트 DB의 임시 CHECK 제약으로 바꿔 롤백을 재현했다. 운영 마이그레이션에 테스트 제약을 넣지 않았다.
- 로그 캡처에 MockMvc의 실패 요청 덤프가 섞이는 문제를 발견해 인증 테스트는 HTTP 비밀 덤프를 끄고 실제 애플리케이션 로그를 검사한다. 테스트 삭제·skip·필터 비활성화는 없다.
- 실제 화면에서 로그아웃의 홈 이동과 Account의 비로그인 redirect가 겹치는 경합을 수정했다. redirect 전에 현재 hash가 여전히 /me인지 확인하며, 수정 후 홈 유지·새로고침을 검증했다.
- 초기 CLI 브라우저 검증은 잔류 라우팅 후크와 탭이 about:blank로 바뀌는 문제로 증거를 확정할 수 없었다. 독립된 headless Chrome 프로세스로 격리해 화면 검증을 다시 실행했다. 최초 실패를 PASS로 세지 않았다.
- 브라우저의 예상한 400/401/409와 의도한 전송 실패는 네트워크 콘솔에 남는다. 최종 화면 검증의 uncaught JavaScript exception은 0이었다. 모든 콘솔 오류가 0이었다는 뜻은 아니다.

### 직접 확인하는 방법

기존 bootRun을 종료한 뒤 clean 검사를 한다. 아래 명령은 PowerShell 7 기준이다. Docker의 로컬 mysql 볼륨과 다른 프로젝트 컨테이너는 삭제하지 않는다. 기존 검증 서버를 그대로 볼 때는 [127.0.0.1:5173](http://127.0.0.1:5173/)을 연다. localhost와 127.0.0.1을 섞으면 Origin·쿠키 저장소가 달라지므로 여기서는 127.0.0.1로 통일한다.

```powershell
# 저장소 루트
docker compose up -d mysql
$env:JAVA_HOME = 'C:\Users\jitin81\.jdks\corretto-21.0.10' # 자신의 JDK 21
$env:JWT_SECRET_BASE64 = [Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes(32))
cd backend
./gradlew.bat clean build
./gradlew.bat :app:bootRun
```

```powershell
# 다른 터미널, 저장소 루트
cd frontend
npm ci
npm run lint
npm run typecheck
npm run build
npm run dev
```

1. /#/me에 비로그인으로 들어가 로그인으로 이동하는지 확인한다. 회원가입에서 짧은 닉네임·비밀번호와 한글 25자 비밀번호의 오류를 본다. 유효한 가입 후 원래 내 정보 화면으로 돌아오는지 확인한다. 기존 이메일 가입은 409 안내를 표시한다.
2. 새로고침 뒤 로그인 유지, 새 탭에서도 로그인 유지, 로그아웃 뒤 새로고침과 다른 탭의 다음 새로고침에서 비로그인을 확인한다.
3. 두 탭에서 Network의 refresh를 관찰하며 동시에 새로고침한다. 탭당 1건씩 총 2건은 정상이며, **동시에 진행 중인 요청은 1건**이어야 한다. 응답 하나를 보류하는 브라우저 자동화에서 `await navigator.locks.query()`로 held/pending의 auth-refresh를 확인할 수 있다.
4. 같은 탭의 401 합류는 로그인 후 세 회차 조회를 실제 만료까지 보류한 뒤 함께 전달해 재현한다. 만료된 세 요청 401 → refresh 한 건 200 → 원래 세 요청 200이 기준이다. DevTools에서 토큰·쿠키 원문을 공유하지 않는다.
5. Application에서 Refresh 쿠키의 HttpOnly·Strict·/api/auth와 local의 Secure=false를 확인한다. localStorage/sessionStorage에는 토큰이 없어야 한다. 운영 Secure=true 강제는 백엔드 테스트에서 확인했으며 실제 HTTPS 브라우저는 NOT_RUN이다.

curl로 확인할 때는 `output/manual-auth/`에 요청·응답·쿠키를 보관하고 출력에는 상태만 남긴다. 실제 이메일과 비밀번호는 자신의 로컬 계정 값을 사용한다.

```powershell
New-Item -ItemType Directory -Path output/manual-auth -Force | Out-Null
$taskEmail = Read-Host '로컬 계정 이메일'
$taskSecure = Read-Host '비밀번호' -AsSecureString
$taskPassword = [Net.NetworkCredential]::new('', $taskSecure).Password
@{email=$taskEmail; password=$taskPassword; nickname='직접검증'; role='ADMIN'} |
  ConvertTo-Json | Set-Content output/manual-auth/signup.json -Encoding utf8NoBOM
curl.exe -s -o output/manual-auth/member.json -w '%{http_code}' -X POST http://127.0.0.1:8080/api/auth/signup -H 'Content-Type: application/json' --data-binary '@output/manual-auth/signup.json'
# 새 계정 201, member.json의 role은 USER. 기존 계정은 409.
curl.exe -s -o output/manual-auth/login.json -w '%{http_code}' -c output/manual-auth/cookies.txt -X POST http://127.0.0.1:8080/api/auth/login -H 'Content-Type: application/json' --data-binary '@output/manual-auth/signup.json'
$taskAccess = (Get-Content output/manual-auth/login.json -Raw | ConvertFrom-Json).accessToken
curl.exe -s http://127.0.0.1:8080/api/me -H "Authorization: Bearer $taskAccess"
Copy-Item output/manual-auth/cookies.txt output/manual-auth/old-cookies.txt
curl.exe -s -o output/manual-auth/refresh.json -w '%{http_code}' -b output/manual-auth/cookies.txt -c output/manual-auth/cookies.txt -X POST http://127.0.0.1:8080/api/auth/refresh
# 200. 구 쿠키는 401이며 새 쿠키를 지우지 않는다.
curl.exe -s -o output/manual-auth/rejected.json -w '%{http_code}' -b output/manual-auth/old-cookies.txt -X POST http://127.0.0.1:8080/api/auth/refresh
curl.exe -s -o output/manual-auth/logout.txt -w '%{http_code}' -b output/manual-auth/cookies.txt -X POST http://127.0.0.1:8080/api/auth/logout
curl.exe -s -o output/manual-auth/after-logout.json -w '%{http_code}' -b output/manual-auth/cookies.txt -X POST http://127.0.0.1:8080/api/auth/refresh
# 순서대로 401 / 204 / 401. 이미 발급된 Access는 만료까지 여전히 유효.
$taskPassword=$null; $taskAccess=$null; $taskSecure.Dispose()
```

ADMIN은 `ADMIN_SEED_EMAIL`과 유효한 `{bcrypt}` 해시인 `ADMIN_SEED_PASSWORD_HASH`를 자신의 프로세스 환경변수로 설정한 뒤 `./gradlew.bat :app:bootRun '--args=--spring.profiles.active=local,admin-seed --spring.main.web-application-type=none'`를 한 번 실행한다. 비웹 실행이 종료되면 정상 local bootRun으로 돌아간다. 해시·키는 커밋하지 않는다. 운영 명령은 [DEPLOY](DEPLOY.md)의 M1-01 추가 절을 따른다.

### 핵심 이유·깨질 수 있는 상황·후속

- 행 잠금 뒤 시각을 읽는 이유는 기다리는 중 만료된 토큰의 수락을 막기 위해서다. DB 잠금 대기 시간 초과·커넥션 장애는 실패 응답이 될 수 있으며 성능 상한을 측정한 것은 아니다. HMAC 검증은 DB 조회 없이 공유 Decoder 하나에서 한다.
- 이전 폐기와 신규 저장을 한 트랜잭션으로 묶어 반쪽 회전을 막는다. **커밋 후 응답/Set-Cookie가 유실되면** 구 토큰은 이미 폐기되어 재로그인이 필요할 수 있다. 재사용 유예가 없다는 승인한 정책의 한계다. 새 쿠키를 잘못 지우지 않도록 실패 응답은 Set-Cookie를 보내지 않는다.
- Web Locks는 같은 출처·브라우저 컨텍스트의 협력하는 탭을 직렬화한다. 서버는 이를 신뢰하지 않고 DB 잠금으로 중복 성공을 막는다. 다른 클라이언트·응답 유실까지 해결하는 장치는 아니다.
- Clock 역행은 유효 기간을 실제 시간보다 늘릴 수 있고, 앞으로 점프하면 일찍 만료될 수 있다. JWT는 초 단위라 실제 수명은 발급 Clock 값 기준 15분보다 최대 1초 미만 짧다. 응답의 expiresAt과 실제 검증 경계는 일치한다. 레인 시계 점프 OQ-35는 이번에 정하지 않았다.
- M1-02: 공용 Decoder로 CONNECT 인증, 임시 TOKEN_INVALID 거절 제거, Principal·개인 큐·연결 신원 전환. M1-08: 익명 진단 연결 수명 정리. M3-07: trade FK와 경고 쓰기·거래 구현. 패널티 OQ-26은 미결 그대로다.
- 원격 CI·운영 HTTPS·실제 운영 Secure 쿠키·외부 관리 포트·배포/이미지 실행·성능·복구·입찰·STOMP JWT는 NOT_RUN이다. 단위/필터/MySQL/TCP/실제 UI의 증거를 서로 대신하지 않는다. 새 npm 의존성·외부 알림·원격 쓰기는 없다.

## M0-05 첫 배포 준비

- 작업일: 2026-10-27 (Asia/Seoul).
- 상태: **이미지·서버 compose·배포 문서 준비와 로컬 검증 PASS / 실제 서버 배포 검증 NOT_RUN**. 사용자 지시로 이번에는 서버 배포를 하지 않는다. M0-05 전체 완료가 아니다.
- 원본: [TASKS M0-05](TASKS.md#m0-05-첫-배포), 사용자 보충 지시와 범위 조정. TASKS 수용 기준 자체는 바꾸지 않는다.
- 승인: 권장안 승인 후 M0-04 완료까지 대기. 사용자의 M0-04 검증 완료 통보와 착수 지시 후 시작했다.
- 선행 확인: 최신 dev `9f1381f8c2c74658421a4b6c3f718e0bf638f98d`, 작업 트리 clean. [M0-04 PR #1](https://github.com/songstone/four-lane-auction/pull/1)의 최종 검증 본문, [dev 성공](https://github.com/songstone/four-lane-auction/actions/runs/40453469067), [실패 실행](https://github.com/songstone/four-lane-auction/actions/runs/40455257375)의 backend 실패·frontend 성공, [PR #2](https://github.com/songstone/four-lane-auction/pull/2)의 미머지 종료를 조회했다. 브랜치 보호도 재조회했다. 아래 M0-04 절의 미완료 표기는 당시 커밋의 스냅샷이며 최종 원격 증거는 PR #1 본문에 있다.
- 작업 위치: 기본 checkout `C:\Users\jitin81\Documents\dev\lab\four-lane-auction`, 별도 worktree 없음. 최신 origin/dev에서 `feature/M0-05-first-deploy` 생성.
- 결정: [ADR-005](adr/ADR-005-M0-05-first-deploy.md). 서울 t3.medium 제안, 공개 GHCR·수동 배포, 앱의 SPA 서빙, 독립 prod compose, app stop→start. app·mysql만 포함한다.
- **TLS는 M6-01로 연기**했다. 이번 절차의 검증 주소는 `http://<host>`, WebSocket은 `ws://<host>/ws`다. TASKS의 HTTPS curl 예시는 이번에 HTTP로 검증하도록 사용자 승인받았다. 외부 HTTP 자체도 아직 NOT_RUN이다.
- app 메모리 상한 1,536MiB·JVM `-Xms256m -Xmx768m`, mysql 1,536MiB·buffer pool 512MiB는 초기 배치 예산이다. 서버 수용량·지연·처리량 측정 결과가 아니다. 측정 인스턴스는 M4-03에서 정한다.

### 구현과 검증 계획

- Dockerfile: Node 빌드 → Corretto 21 빌드 → 비root Java 실행. Docker 빌드에서만 프론트 dist를 JAR static에 포함한다. 소스 SHA·이미지 태그는 OCI label에 기록한다.
- `.dockerignore`: backend·frontend 빌드 입력만 허용하고 .env·로컬 설정·빌드 결과를 제외한다.
- `docker-compose.prod.yml`: 독립 파일, app·mysql, app 포트 하나, 관리 포트 내부 loopback, MySQL publish 없음. prod DB·Origin은 서버 환경변수 필수값이다.
- `deploy/.env.example`: 실제 비밀번호는 빈 값. `.env` 제외 규칙을 그대로 사용한다.
- [DEPLOY](DEPLOY.md): SHA 태그와 정식 v0.1.0의 연결, 명령별 빌드·승인 후 push·서버 설정·pull·stop→start·검증·진단·이전 이미지 재배포를 적었다.
- 기존 로컬 compose·bootRun·Vite·API·EVENTS·스키마·CI 워크플로는 수정하지 않는다. 단일 인스턴스 가드는 M3-09, Prometheus·Grafana는 M4-01, Actions 자동 배포는 M6-01이다.
- 로컬 빌드·이미지 실행 증거는 아래에 기록한다. 이미지 push·Git push·PR 생성·AWS 명령은 실행하지 않은 상태다.

### 준비 단계 로컬 실행

| 검사 | 결과 | 증거 |
|---|---|---|
| backend clean build | PASS. 14 actionable tasks, 13 executed·1 up-to-date. 시간 출처 검사 29파일·위반 0 | `output/m0-05-backend-build.log` |
| 실제 JUnit XML 검증 | PASS. UTC 70회·서울 70회, 각각 MySQL 35회, failures/errors/skipped 0. 같은 테스트를 두 JVM에서 실행한 횟수 | `output/m0-05-test-summary.md`, `backend/app/build/test-results/{test,testSeoul}/TEST-*.xml` |
| npm ci·lint·typecheck·build | PASS, 기존 lockfile 유지. npm audit 0 vulnerabilities | `output/m0-05-{npm-ci,lint,typecheck,frontend-build}.log` |
| Compose 구성 확인 | PASS. app·mysql, 관리 loopback, mysql publish 없음, 별도 로컬 bind/port 적용, 각 1,536MiB 상한 | `output/m0-05-compose-checks.json` |
| 필수값 거부 | PASS. IMAGE_TAG·DB명·사용자·앱/루트 비밀번호·DB_URL·Origin을 하나씩 빼면 config 실패. 미설정 샘플도 실패 | 위 Compose 결과. 정적 구성 검증이며 앱 실행 증거는 아님 |
| 베이스 실행 확인 | Corretto 21.0.13, curl 8.21.0 포함 확인. 추가 OS 패키지 설치 없음 | 고정 digest의 임시 컨테이너 실제 실행 |

검증 환경: Windows, 로컬 Corretto 21.0.10, Gradle 9.8.2, Node 24.11.1·npm 11.6.2, Docker Engine 28.5.1·Compose 2.40.0. 실행 중이던 이 checkout의 bootRun·Vite만 종료해 파일 잠금을 피했고 검사 후 복원해 관리 health와 Vite HTTP 200을 확인했다(`output/m0-05-restored-services.json`). 기존 로컬 MySQL과 다른 프로젝트 컨테이너는 유지했다.

### 생성한 이미지

- 원본 커밋: `95eae2cae58c9833885ae1ad4f981de89da83d5a`.
- 로컬 이미지: `ghcr.io/songstone/four-lane-auction:sha-95eae2cae58c9833885ae1ad4f981de89da83d5a`.
- 로컬 image ID: `sha256:091eef9a39b0890d1ee977d8b3bf18454b6d5a471b4414b10b92429192dd96f6`. **GHCR에는 push하지 않았다.** 이 ID를 공개 레지스트리에서 pull 검증한 digest로 취급하지 않는다.
- 플랫폼 linux/amd64, 실행 사용자 `10001:10001`. OCI revision/version이 위 원본 SHA·태그와 일치한다. 이후 문서 증거 커밋은 실행 이미지의 소스를 바꾸지 않는다.
- 빌드: 프론트 lint·typecheck·Vite build, Corretto JDK 21.0.13에서 Wrapper 9.8.2의 bootJar 성공. 실행 단계는 headless Corretto 21.0.13. 로그는 `output/m0-05-image-build.log`.
- 최초 빌드는 최소 이미지의 xargs 부재로 실패했고, 같은 Wrapper JAR의 JVM 진입점으로 수정했다. 다음 시도는 실행용 headless 이미지에 javac가 없어 실패해 빌드 단계만 JDK 이미지로 분리했다. 실패 로그는 `output/m0-05-image-build-initial.log`, `output/m0-05-image-build-jre.log`에 보존했다. 프로젝트 의존성·Wrapper 검증을 우회하거나 테스트를 skip하지 않았다.

### 로컬 이미지 실제 실행 증거

별도 프로젝트 `fourlane-m0-05-smoke`, 별도 MySQL named volume, `127.0.0.1:18080`을 사용했다. AWS 서버·도메인·보안 그룹 확인이 아니다. 로컬 DB의 일회성 검증 비밀번호는 gitignore 대상 `output/m0-05-local.env`에만 넣었다.

| 확인 | 실제 결과 | 증거 |
|---|---|---|
| prod 이미지 + 실제 MySQL | app·mysql healthy, 내부 health UP. Flyway V1~V4 | `output/m0-05-compose-up.log`, `output/m0-05-runtime-checks.json` |
| SPA | 홈 HTML·빌드 JS 200. 실제 Chrome에서 홈과 빈 상태 3개, REST·WebSocket 녹색 배지 확인 | `output/m0-05-browser-snapshot.log`, `output/playwright/m0-05-connected.png` |
| REST·404 계약 | RUNNING/SCHEDULED/FINISHED 각각 200·빈 items·UTC ms serverTime. 없는 API와 앱 포트의 actuator는 404 NOT_FOUND JSON | `output/m0-05-runtime-checks.json` |
| WebSocket | 실제 /ws 연결 후 STOMP 1.2 CONNECTED 수신. 다른 Origin은 HTTP 403 거절 | 위 runtime 결과. WebSocket 101만으로 판정하지 않음 |
| 같은 출처 | Chrome 요청 기록의 세 상태 API는 18080 출처·200. 연결 배지도 connected. 콘솔 error/warning 0 | `output/m0-05-browser-requests.log`, `output/m0-05-browser-console.log` |
| 관리·DB 포트 | app publish는 localhost 18080→8080 하나, mysql publish 없음. 관리 주소는 내부 127.0.0.1. 같은 Docker bridge의 임시 peer는 app API 200, app:8081은 TCP 연결 거절(curl exit 7) | runtime 결과, `output/m0-05-peer-checks.json`. 실제 외부/SG 증거는 아님 |
| 환경변수 누락 | 설정 없이 prod 이미지를 실행하면 필수 placeholder 오류로 exit 1 | `output/m0-05-prod-missing-env.json`, `.log` |
| 이미지에 서버 설정 미포함 | image Config.Env에 DB·MYSQL·Origin 변수 없음. 실제 값은 compose 실행 환경에서만 전달 | `output/m0-05-image-env-check.log` |
| stop→start | app stop 뒤 running app 0개 확인 후 같은 이미지를 start해 healthy·UP. 브라우저 두 배지도 다시 연결됨 | `output/m0-05-stop-start.log`, `output/m0-05-browser-after-restart.log`. 다른 버전 교체·M3 복구 검증은 아님 |
| 배포 문서 | 로컬 링크 대상과 Bash·PowerShell 코드 블록 문법 검사 통과 | `output/m0-05-deploy-syntax.log`. 서버 명령 실제 실행으로 세지 않음 |

검증 후 임시 이미지 프로젝트의 컨테이너·네트워크만 종료했고 named volume과 로컬 이미지는 유지했다. 기존 개발 서버·로컬 MySQL은 유지했다(`output/m0-05-cleanup.log`, `output/m0-05-final-local-state.json`). 직접 재현하려면 [DEPLOY 3절의 로컬 이미지 확인](DEPLOY.md#로컬에서-생성한-이미지를-직접-확인할-때)을 따른다. 서버 준비 후에는 DEPLOY 5절부터 사용자 실행 결과를 확인한다.

### TASKS 수용 기준과 남은 서버 검증

| M0-05 수용 기준 | 이번 범위의 상태 | 나중에 필요한 증거 |
|---|---|---|
| 배포 주소 홈·배지·WebSocket | 서버 **NOT_RUN**. 로컬 이미지에서는 PASS | 실제 배포 주소의 REST 200·홈·STOMP CONNECTED |
| 관리 포트 외부 비공개 | 서버 **NOT_RUN**. publish·바인딩·peer 거절 로컬 PASS | 실제 서버 publish·SG 조회와 외부 TCP 접속 실패 |
| 비밀번호 등 서버 환경변수 | 서버 **NOT_RUN**. 필수 변수·Git 제외·prod 누락 거부 로컬 PASS | 실제 서버 환경 공급·파일 권한 확인. 비밀값 원문 공유 불필요 |
| 명령 단위 배포 문서 | DEPLOY 작성·문법 확인 PASS. 로컬 이미지 실행 PASS | 서버 명령 실제 실행은 NOT_RUN |

M0 완료 기준 6·7의 정식 v0.1.0 배포, TLS·WSS, EC2 자원 확인은 **NOT_RUN**이다. 실제 서버 단계는 사용자가 준비한 뒤 DEPLOY 5절부터 결과를 확인하며 진행한다.

## M0-04 CI

- 작업일: 2026-10-27 (Asia/Seoul)
- 상태: **로컬 구현·검증 PASS / 원격 검증 NOT_RUN, 원격 쓰기 승인 대기**. M0-04 전체 완료가 아니다.
- 원본: [TASKS M0-04](TASKS.md#m0-04-ci), TASKS 0.2 및 사용자 보충 지시.
- 시작 SHA: `ede60ae2a6f6b28d3c00162a96dd9f4c3c125abf`. 시작 시 `dev`와 작업 트리 clean. `git fetch origin` 뒤 최신 `origin/dev`와 일치 확인.
- 작업 위치: 기본 checkout `C:\Users\jitin81\Documents\dev\lab\four-lane-auction`, 별도 worktree 없음. 브랜치 `feature/M0-04-ci`.
- 시작 시 원격: 공개 `songstone/four-lane-auction`, main/dev 모두 같은 시작 SHA로 이미 게시됨. 원격 추가·초기 push는 필요하지 않았다.
- 승인: 권장 계획과 gh 설치 승인. 호환성 확인 뒤 TypeScript 7.0.2를 호환 최신 안정 6.0.x로 조정하는 추가 선택을 받았으며 6.0.3으로 고정했다.
- 결정 기록: [ADR-004](adr/ADR-004-M0-04-ci.md). 실제 push·PR 생성·보호 및 머지 방식 설정은 각각 명령과 입력 JSON을 보여준 뒤 별도 승인을 받아야 한다.

### 구현

- `.github/workflows/ci.yml`: dev/main push와 두 브랜치 대상 PR에서 `backend`, `frontend`를 독립 실행한다. feature push 트리거·경로 필터·조건부 잡 생략·잡 간 의존 관계는 없다. 문서만 바뀌거나 워크플로가 바뀌어도 두 검사가 생성된다.
- backend: ubuntu-24.04, Corretto 21, 기존 Wrapper. `docker info` 뒤 `./gradlew --no-daemon clean build --no-build-cache`. 기존 `verifyTimeSources`, engine/app 빌드, UTC `test`와 서울 `testSeoul`을 포함한다.
- frontend: Node 24, `npm ci → npm run lint → npm run typecheck → npm run build`. ESLint flat config의 JS·TS 권장 규칙과 Hooks 호출·의존성 규칙, 경고 0개 기준이다. 생성 코드·빌드 산출물은 제외하며 기존 앱 소스와 생성 코드는 수정하지 않았다.
- Gradle/npm 다운로드 캐시를 사용하되 테스트 결과를 실제 실행으로 대체하지 않는다. Gradle 캐시는 main/dev push에서만 쓰고 PR에서는 읽는다. Actions는 공식 안정 release의 commit SHA로 고정했다.
- 리포트 검증기는 UTC·서울 각각의 필수 MySQL 클래스 2개를 요구하고 실행 0건·skip·실패·오류·리포트 누락·XML 손상·집계 불일치를 실패 처리한다. XML·HTML은 Actions artifact, 실행 수는 step summary에 남긴다.
- gh 2.103.3 설치 성공. 최초 `gh auth status`는 로그인 없음이었다. 이후 인증 상태는 원격 작업 직전 다시 확인한다.
- 백엔드 의존성·API·EVENTS 계약·TASKS 원문 변경 없음. 운영 secrets 입력은 불필요하며 필요한 비밀값은 GitHub secrets로만 공급한다. 토큰 권한은 `contents: read`다.

### 로컬 실행 증거

검증 환경: Windows, Corretto 21.0.10, Gradle Wrapper 9.8.2, Docker Engine 28.5.1, Node 24.11.1, npm 11.6.2, Python 3.14. Linux/GitHub-hosted 실제 실행은 아직 NOT_RUN이다.

| 검사 | 실제 실행 결과 | 증거 |
|---|---|---|
| `./gradlew.bat --no-daemon clean build --no-build-cache --console=plain` | PASS, 14 actionable tasks 모두 executed. app `test`·`testSeoul` 실제 실행 | `output/m0-04-backend-build.log` |
| M0-02 시간 출처 검사 | PASS, 운영 파일 29개, 위반 0건 | 위 빌드의 `verifyTimeSources` |
| `npm ci` | PASS, 239 packages 설치, audit 0 vulnerabilities | `output/m0-04-npm-ci.log` |
| `npm run lint` | PASS, 오류·경고 0 | `output/m0-04-lint.log` |
| `npm run typecheck` | PASS, src와 Node 설정 두 tsconfig 검사 | `output/m0-04-typecheck.log` |
| `npm run build` | PASS, Vite production build | `output/m0-04-frontend-build.log` |
| 실제 JUnit XML 검증·summary 파일 기록 | PASS | `output/m0-04-report-verification.log`, `output/m0-04-test-summary.md` |
| 리포트 검증기 음성·양성 검사 | PASS, 합성 입력 9개: 정상·클래스 누락·태스크 누락·0건·skip·failure·error·집계 불일치·XML 손상 | `output/m0-04-reporter-checks.log`. **검증기 자체 검사이며 MySQL 실행 증거로 세지 않는다** |
| 리포트 검증기 CLI 실패 전달 | PASS, 리포트가 없는 경로를 입력하면 exit 1 | `output/m0-04-reporter-cli-negative.log`. 검증기 오류 전달 검사이며 실제 테스트 실행이 아니다 |
| 워크플로 YAML·설정 검사 | PASS, 기존 설치된 yaml 파서로 YAML 문법, 트리거·필터 없음·독립 잡·고정 SHA·명령 순서 확인 | `output/m0-04-workflow-checks.log`. **정적 검사이며 Actions 실행으로 세지 않는다** |

실제 테스트 리포트의 실행 수는 다음과 같다. UTC·서울은 같은 테스트를 서로 다른 JVM 시간대에서 실행한 횟수이며 서로 다른 테스트 140개라는 뜻은 아니다.

| 실행 | SchemaAndTimeIntegrationTest | SessionHttpAndStompIntegrationTest | Testcontainers 소계 | 전체 테스트 | failures / errors / skipped |
|---|---:|---:|---:|---:|---|
| `test`, UTC | 11 | 24 | **35** | **70** | 0 / 0 / 0 |
| `testSeoul`, Asia/Seoul | 11 | 24 | **35** | **70** | 0 / 0 / 0 |
| 총 실행 횟수 | 22 | 48 | **70** | **140** | 0 / 0 / 0 |

원본은 `backend/app/build/test-results/{test,testSeoul}/TEST-*.xml`과 `backend/app/build/reports/tests/{test,testSeoul}/index.html`이다. engine은 아직 테스트가 없어 `engine:test NO-SOURCE`이며 엔진 테스트를 실행했다고 주장하지 않는다. 로그·빌드·합성 입력 검증 스크립트는 gitignore 대상 `output/`에만 보존한다.

### 실행 중 확인한 문제

- 최초 `npm ci`는 이전 Vite가 lightningcss 네이티브 모듈을 열고 있어 EPERM으로 실패했다. 이 checkout의 Vite PID를 확인해 종료한 뒤 같은 명령으로 성공했다. 최초 로그는 `output/m0-04-npm-ci-initial.log`에 보존했다. 관리자 실행·skip·lockfile 무시로 해결하지 않았다.
- clean 전에 이 checkout에서 실행 중이던 백엔드 앱도 소유 경로·main class를 확인해 종료했다. 관련 없는 Java/Node 프로세스는 종료하지 않았다.
- 검증 뒤 기존 백엔드와 Vite를 다시 기동했고 관리 health·Vite HTTP 200을 확인했다 (`output/m0-04-restored-services.json`). 이는 로컬 서비스 복원 확인이며 브라우저 시연·CI 실행 증거는 아니다.
- npm registry의 안정판 metadata와 peer 범위를 확인했을 때 typescript-eslint 8.72.1은 TypeScript `<6.1.0`을 요구했다. 사용자 추가 선택으로 TypeScript 6.0.3을 사용했다. 설치 전후 기존 패키지의 버전 비교에서 TypeScript 외 기존 패키지 버전은 유지됐다.

### TASKS M0-04 수용 기준별 상태

| 수용 기준 | 현재 판정 | 남은 확인 |
|---|---|---|
| dev/main push와 두 브랜치 대상 PR에서 실행, backend/frontend 각각 실패 보고 | 설정·로컬 검사 PASS / **원격 NOT_RUN** | 구현 PR 첫 Actions, 머지 후 dev push, 실제 잡 결과 |
| Testcontainers가 CI에서 실제 실행되고 건너뛰지 않음 | 로컬 실제 실행 PASS / **CI NOT_RUN** | Actions summary의 실행 수, XML·HTML artifact |
| Instant.now() 실패 PR의 CI 빨간색·머지 차단·미머지 종료 | **NOT_RUN** | 사용자의 구현 PR 머지 후 실패용 feature/PR로 실행 |
| main/dev 관리자 포함 직접 push 거절, 필수 검사, 승인 리뷰 불필요 | **NOT_RUN** | 첫 검사 실행 후 보호 설정 별도 승인·적용·조회, 실제 직접 push 거절 |
| 비밀값은 GitHub secrets로만 공급 | 코드·설정에 운영 비밀값 입력 없음 | 원격 설정 확인 시 함께 재확인. 실제 운영 secrets를 요구하는 단계는 없음 |

성공·실패 Actions 링크 2개, 머지 차단 화면, 양쪽 보호 설정 조회와 직접 push 거절 증거는 아직 없다. 이 증거를 얻기 전에는 완료로 표시하지 않는다. 사용자가 구현 PR을 머지한 뒤의 최종 증거는 기존 PR 본문과 최종 보고에 추가하고 dev에 직접 커밋하지 않는다.

### 직접 재현하는 방법

기존 bootRun·Vite가 실행 중이면 이 checkout의 해당 프로세스를 먼저 종료해야 Windows 파일 잠금 없이 clean·npm ci를 실행할 수 있다. Testcontainers는 자체 MySQL 컨테이너를 사용하므로 테스트를 위해 Compose app을 띄우지 않는다.

```powershell
$env:JAVA_HOME = 'C:\Users\jitin81\.jdks\corretto-21.0.10'
cd backend
./gradlew.bat --no-daemon clean build --no-build-cache --console=plain
cd ..
python .github/scripts/verify-test-results.py
cd frontend
npm ci
npm run lint
npm run typecheck
npm run build
```

## M0-01 저장소 뼈대와 로컬 인프라

- 작업일: 2026-10-27 (Asia/Seoul)
- 상태: 완료 (M0-01 범위, 아래 수용 기준·실행 증거 기준)
- 원본: [TASKS M0-01](TASKS.md#m0-01-저장소-뼈대와-로컬-인프라), 사용자 보충 범위(README 초안·simulator 빈 자리)
- 시작 SHA: `8482eebbe55f9bd4ea54d9a79f96674047fdf213`, `main`, 시작 시 작업 트리 clean
- 작업 위치: 기본 checkout `C:\Users\jitin81\Documents\dev\lab\four-lane-auction` (별도 worktree 없음)
- 승인: 사용자 2026-10-27. 권장안 4개와 변경·검증 계획 승인. JUnit 6으로 ARCH 1.3 수정, 백엔드는 Boot 4.1.2 BOM 조합으로 고정
- 결정 기록: [ADR-001](adr/ADR-001-M0-01-project-foundation.md)

### 버전 확인 및 고정

확인일은 2026-10-27이다. 프리릴리스·snapshot은 선택하지 않았다. 프론트 직접 의존성은 정확한 버전과 package-lock.json으로 고정한다. 백엔드는 **사용자가 승인한 BOM 조합**이며 개별 라이브러리 최신 버전과 구별한다.

| 대상 | 선택 버전 | 확인한 출처 |
|---|---|---|
| Java | 언어·bytecode 21 / 검증 JDK Corretto 21.0.10.7.1 | ARCH 1.3, 로컬 `java -version` (JDK 최신 패치 조사 범위는 아님) |
| Spring Boot | 4.1.2 | [공식 프로젝트](https://spring.io/projects/spring-boot/), [Maven metadata](https://repo.maven.apache.org/maven2/org/springframework/boot/spring-boot-dependencies/maven-metadata.xml) |
| Spring Framework | 7.0.10 (Boot BOM) | [Boot 4.1.2 BOM](https://repo.maven.apache.org/maven2/org/springframework/boot/spring-boot-dependencies/4.1.2/spring-boot-dependencies-4.1.2.pom) |
| Spring Data | 2026.0.2 BOM (Boot BOM) | 위 Boot BOM |
| Hibernate ORM | 7.4.13.Final (Boot BOM) | 위 Boot BOM |
| Jackson | 3.1.7 (Boot BOM) | 위 Boot BOM |
| MySQL Connector/J | 9.7.0 (Boot BOM) | 위 Boot BOM |
| Lombok | 1.18.48 (Boot BOM) | 위 Boot BOM |
| JUnit Jupiter | 6.0.3 (Boot BOM) | 위 Boot BOM, [Spring 7 baseline](https://github.com/spring-projects/spring-framework/wiki/Spring-Framework-7.0-Release-Notes) |
| Gradle Wrapper | 9.8.2 | [공식 current metadata](https://services.gradle.org/versions/current), [배포 SHA-256](https://services.gradle.org/distributions/gradle-9.8.2-bin.zip.sha256) |
| MySQL Server | 8.4.13 LTS | [공식 릴리스 노트](https://dev.mysql.com/doc/relnotes/mysql/8.4/en/news-8-4-13.html), [공식 이미지 tag·digest](https://hub.docker.com/v2/repositories/library/mysql/tags/8.4.13) |
| React / React DOM | 19.3.1 | [React npm](https://registry.npmjs.org/react/latest), [React DOM npm](https://registry.npmjs.org/react-dom/latest) |
| Vite | 8.3.5 | [npm](https://registry.npmjs.org/vite/latest) |
| TypeScript | 7.0.2 | [npm](https://registry.npmjs.org/typescript/latest) |
| Tailwind / @tailwindcss/vite | 4.3.3 | [Tailwind npm](https://registry.npmjs.org/tailwindcss/latest), [Vite 플러그인 npm](https://registry.npmjs.org/@tailwindcss%2Fvite/latest) |
| TanStack Query | 5.105.0 | [npm](https://registry.npmjs.org/@tanstack%2Freact-query/latest) |
| @vitejs/plugin-react | 6.1.2 | [npm](https://registry.npmjs.org/@vitejs%2Fplugin-react/latest) |
| @types/react / @types/react-dom | 19.3.0 | [React 타입 npm](https://registry.npmjs.org/@types%2Freact/latest), [React DOM 타입 npm](https://registry.npmjs.org/@types%2Freact-dom/latest) |
| @types/node | 24.19.2 (실행 Node 24 계열) | [npm 전체 버전](https://registry.npmjs.org/@types%2Fnode) |

Gradle 배포 SHA-256: `21bd5da2de49b642904077f38c0efdc7778e92b3ed0889392dca61b97e389607`. Wrapper JAR SHA-256: `3b8a25775a69158b5ad2b1d17a80a88dc7b40a352a73eb27d06c612a7ce68e98` ([공식 값](https://services.gradle.org/distributions/gradle-9.8.2-wrapper.jar.sha256)).

MySQL 이미지 digest: `sha256:76f0358884cb92d9a086d24e5671c38048d3fef40f2588703d73eb55392d6b69`.

### 수용 기준과 실행 증거

로컬 Windows에서 실제 실행했다. MySQL은 이 checkout이 새로 만든 named volume으로 시작했고, `.env`와 `application-local.yml`은 존재하지 않는 상태였다. 단위/MVC 테스트와 실제 기동·HTTP·브라우저 증거를 구분한다.

| TASKS M0-01 수용 기준 | 결과 | 증거 |
|---|---|---|
| Compose 뒤 bootRun이 MySQL에 붙어 기동 | PASS — 실제 기동 | `docker compose up -d` 성공, `four-lane-auction-mysql-1 healthy`. `:app:bootRun`은 기본 local 프로필로 Hikari 연결·JPA 초기화·앱 8080/관리 8081 기동. DB 버전 8.4.13. 엔티티가 없는 빈 스키마에서 validate 설정으로 기동한 증거이며 실제 테이블 매핑 검증은 아님 |
| 프론트 dev / typecheck / build 통과 | PASS — 실제 실행·브라우저 | `npm ci`, `npm run typecheck`, `npm run build` 성공. `npm run dev` 5173. Chrome/Playwright에서 1440×960과 390×844 레이아웃 확인. 모바일 문서 폭 390 = 뷰포트 390, console error/warning 0 |
| 없는 경로 → 404 NOT_FOUND, API 1.4 형식, 내부 정보 없음 | PASS — MVC + 실제 HTTP | `GET :8080/api/does-not-exist` → 404 + `application/json` + `{"error":{"code":"NOT_FOUND","message":"요청한 경로를 찾을 수 없습니다."}}`. `Accept: text/html`도 같은 JSON 계약. Vite `:5173/api/does-not-exist`도 같은 응답. 예외·trace 비노출은 MVC 테스트로 검사 |
| 소수 초 0인 Instant → `.000Z` | PASS — MVC 직렬화 테스트 | `ApiContractTest`의 파라미터 테스트 3건: 0초 `.000Z`, `.120Z`, `+09:00` → UTC `.000Z`. 운영 Jackson 설정을 실제 MockMvc 메시지 변환기에 등록해 검증. 제품용 시각 API를 추가한 것은 아님 |
| `.env`, `application-local.yml`이 git status에 없음 | PASS — Git 검사 | `git check-ignore .env backend/app/src/main/resources/application-local.yml` 두 경로 매칭. 두 경로의 `git status --short --untracked-files=all` 출력 없음. 실제 비밀값 파일을 만들지 않음 |

### 추가 검증과 보관 위치

- 백엔드: Gradle 9.8.2 / Corretto 21로 `test build wrapper` 성공. 총 **11 tests, failures 0, errors 0, skipped 0** (`ClockConfigTest` 1 + `ApiContractTest` 10). HTML Accept 회귀, 필드 검증 `fields`, JSON 파싱 실패의 빈 `fields`, `lane` 참조, 예상하지 못한 500과 servlet error dispatch를 포함한다.
- `engine:compileClasspath`: **No dependencies**. 엔진은 package 자리만 있고 도메인 구현·테스트가 없어 `engine:test NO-SOURCE`다. 이를 엔진 기능 테스트 통과로 해석하지 않는다.
- 실제 관리 포트: `GET :8081/actuator/health` → 200, `status: UP`. 앱 포트 `:8080/actuator/health`는 404. 운영 클래스패스에 Flyway·Testcontainers·Security·WebSocket을 넣지 않았다.
- prod에 필수 환경변수를 전달하지 않고 패키징 JAR을 실행하면 exit 1. `MANAGEMENT_SERVER_PORT` placeholder 미해결로 기동이 거절됨을 확인했다. 실제 prod 환경 접속·배포 검증은 **NOT_RUN**.
- Gradle Wrapper JAR SHA-256을 위 공식 값과 대조했다. Unix 실행 파일 `backend/gradlew`의 Git mode는 `100755`로 지정했다. Linux/macOS에서의 실행 자체는 **NOT_RUN**.
- `.gitattributes`로 Unix Wrapper는 LF, Windows Wrapper는 CRLF를 고정한다. `ErrorCode` 카탈로그 39개가 API 9장의 코드 목록과 같은 것도 대조했다. 기존 PRD·API·EVENTS·TASKS의 계약과 수용 기준은 수정하지 않았다.
- 테스트 결과: `backend/app/build/test-results/test/TEST-*.xml`, `backend/app/build/reports/tests/test/index.html`.
- 로컬 로그/HTTP 기록: `output/m0-01-bootrun.log`, `output/m0-01-http-checks.json`, `output/m0-01-dependencies.txt`, `output/m0-01-prod-missing-env.log`.
- 화면 캡처: `output/playwright/m0-01-desktop.png`, `output/playwright/m0-01-mobile.png`. 위 빌드·실행 산출물과 Playwright 세션 기록은 gitignore 대상이고 커밋 대상이 아니다.

검증 중 수정: HTML Accept 요청이 에러 JSON 대신 406이 되던 문제는 에러 응답의 Content-Type을 JSON으로 명시하고 회귀 테스트로 막았다. Windows에서 Vite 실행 중 `npm ci`가 네이티브 모듈 파일 잠금으로 한 번 실패했으며, 개발 서버를 종료한 뒤 `npm ci`·typecheck·build를 모두 다시 통과했다. 브라우저는 설치된 Chrome을 사용했다.

### 직접 확인하는 방법

루트에서 실행한다. JDK 21 경로는 자신의 설치 위치에 맞춘다. 기존 서버가 같은 포트를 쓰고 있으면 먼저 종료한다. Compose의 MySQL이 healthy가 된 뒤 백엔드를 실행한다.

```powershell
docker compose up -d
docker compose ps
$env:JAVA_HOME = 'C:\Users\jitin81\.jdks\corretto-21.0.10'
cd backend
./gradlew.bat test build
./gradlew.bat :app:bootRun
```

별도 터미널에서 실행한다. `npm ci` 전에 Vite 개발 서버를 종료해야 Windows 파일 잠금을 피할 수 있다.

```powershell
cd frontend
npm ci
npm run typecheck
npm run build
npm run dev
```

다른 터미널에서 확인한다.

```powershell
curl.exe -i http://localhost:8080/api/does-not-exist
# 404, application/json, error.code = NOT_FOUND, trace/exception 없음
curl.exe -i -H 'Accept: text/html' http://localhost:8080/api/does-not-exist
# 위와 같은 404 JSON
curl.exe http://localhost:8081/actuator/health
# status = UP
curl.exe -i http://localhost:5173/api/does-not-exist
# Vite 프록시를 거친 같은 404 JSON
git check-ignore .env backend/app/src/main/resources/application-local.yml
```

브라우저 `http://localhost:5173`에 소개와 빈 경매 공간이 표시된다. 경매 목록·연결 배지는 M0-03 범위다. 종료할 때는 Vite와 bootRun 터미널에서 Ctrl+C, MySQL은 루트에서 `docker compose stop mysql`을 사용한다. 볼륨의 데이터는 보존된다.

prod에서 필요한 값은 `SPRING_PROFILES_ACTIVE=prod`, `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `SERVER_PORT`, `MANAGEMENT_SERVER_PORT`, `MANAGEMENT_SERVER_ADDRESS`다. 실제 운영 비밀값을 파일에 커밋하지 않는다.

### 범위와 증거의 한계

Flyway·Testcontainers·스키마·DB 시간 정밀도 통합 테스트는 M0-02, WebSocket·springdoc·OpenAPI 생성은 M0-03, security는 M1-01이다. simulator는 빈 디렉터리 자리뿐이다. 이번 HTTP/MVC 검증은 경매 기능·동시성·복구·성능 검증을 뜻하지 않는다. 성능 수치는 측정하지 않는다.

## M0-02 Flyway 스키마와 시간 출처 검사

- 작업일: 2026-10-27 (Asia/Seoul)
- 상태: 완료 (M0-02 범위, 아래 수용 기준·실행 증거 기준)
- 원본: [TASKS M0-02](TASKS.md#m0-02-flyway-스키마와-시간-출처-검사)
- 시작 SHA: `95720b944daf83b73fb17100687ef59096c7252b`, `dev`, 작업 트리 clean
- 작업 위치: 기본 checkout `C:\Users\jitin81\Documents\dev\lab\four-lane-auction` (별도 worktree 없음)
- 승인: V1~V4 함께 적용, Gradle 빌드 단계 검색, Flyway 13.11.1 BOM 예외, ARCH 4.4의 CHECK 두 곳을 먼저 보강한 뒤 SQL 복사. 변경·검증 계획 전체 승인
- 결정 기록: [ADR-002](adr/ADR-002-M0-02-schema-and-time-sources.md)

### 버전 확인과 실제 해석 결과

확인일은 2026-10-27이다. Maven Central의 release metadata와 공식 릴리스·사용 문서를 확인했고, 프리릴리스·snapshot은 선택하지 않았다.

| 대상 | 선택·해석 버전 | 확인한 출처와 이유 |
|---|---|---|
| Flyway core / MySQL 모듈 | **13.11.1**, 두 모듈 모두 `strictly` 고정 | [core metadata](https://repo.maven.apache.org/maven2/org/flywaydb/flyway-core/maven-metadata.xml), [MySQL metadata](https://repo.maven.apache.org/maven2/org/flywaydb/flyway-mysql/maven-metadata.xml), [공식 릴리스 노트](https://documentation.red-gate.com/fd/release-notes-for-flyway-engine-179732572.html). 현재 최신 안정판. Boot BOM 12.4.0 대신 쓰도록 승인받은 예외 |
| Boot Flyway starter / 연동 모듈 | 4.1.2 | [공식 기동 연동 설명](https://docs.spring.io/spring-boot/how-to/data-initialization.html), [starter POM](https://repo.maven.apache.org/maven2/org/springframework/boot/spring-boot-starter-flyway/4.1.2/spring-boot-starter-flyway-4.1.2.pom). MySQL 지원은 별도 모듈이 필요하다 |
| Testcontainers BOM / MySQL / JUnit Jupiter 모듈 | **2.0.5** | [metadata](https://repo.maven.apache.org/maven2/org/testcontainers/testcontainers-bom/maven-metadata.xml), [공식 릴리스](https://github.com/testcontainers/testcontainers-java/releases/tag/2.0.5), [MySQL 문서](https://java.testcontainers.org/modules/databases/mysql/). 최신 안정판이며 Boot 4.1.2 BOM과 동일 |
| MySQL Server | 8.4.13 LTS, Compose와 같은 digest | [공식 릴리스 노트](https://dev.mysql.com/doc/relnotes/mysql/8.4/en/news-8-4-13.html), [공식 이미지 metadata](https://hub.docker.com/v2/repositories/library/mysql/tags/8.4.13). 컨테이너에서 `SELECT VERSION()`으로 실제 버전 확인 |
| JUnit Jupiter | 6.0.3 (유지) | [Boot 4.1.2 BOM](https://repo.maven.apache.org/maven2/org/springframework/boot/spring-boot-dependencies/4.1.2/spring-boot-dependencies-4.1.2.pom) |
| 공유 Jackson annotations | 2.22 (Flyway의 전이 의존성으로 해석) | [Flyway core 13.11.1 POM](https://repo.maven.apache.org/maven2/org/flywaydb/flyway-core/13.11.1/flyway-core-13.11.1.pom), 실제 `runtimeClasspath` 보고서. Boot BOM의 2.21 요청보다 높게 해석된다. Jackson 3 databind/core는 3.1.7을 유지한다. 최종 MVC 직렬화·에러 계약 테스트와 실제 HTTP를 함께 검증했다 |

실제 패키징 JAR에 `flyway-core-13.11.1.jar`, `flyway-mysql-13.11.1.jar`, `spring-boot-flyway-4.1.2.jar`가 포함됨을 확인했다. 별도 Flyway Gradle 플러그인·ArchUnit·H2·Redis는 추가하지 않았다.

### SQL 원본과 적용 규칙

- ARCH 4.4를 먼저 보강했다: `ck_lane_pause`의 PAUSED 분기에 `pause_cause IS NOT NULL`, `ck_lot_unsold_reason`의 UNSOLD 분기에 `unsold_reason IS NOT NULL` 추가. MySQL CHECK는 UNKNOWN(NULL)도 허용하기 때문이다 ([공식 설명](https://dev.mysql.com/doc/refman/8.4/en/create-table-check-constraints.html)).
- 그 뒤 ARCH의 SQL 코드 블록에서 `V1__member_auth.sql`, `V2__item.sql`, `V3__bid_policy.sql`, `V4__auction.sql`을 추출했다. 줄바꿈을 LF로 맞춘 텍스트 대조에서 **네 파일 모두 원본과 동일**했다. `member` 등 테이블 이름·시드 값은 그대로다.
- 기본 정책은 원문대로 v1 한 행과 구간 `(0, 1000)`, `(10000, 10000)`, `(100000, 25000)`이다. `created_by=NULL`, 시드 생성 시각도 원문 값이다. ADMIN 계정 행은 넣지 않았다. 생성 방식은 M1-01에서 정한다.
- **다음 마이그레이션부터 실제 적용 순서대로 다음 번호를 매긴다** (TASKS 11장 3번). ARCH 4.3의 V5~V8은 계획 당시 번호로 예약하지 않는다. 이미 적용한 파일은 수정하지 않고 변경은 새 버전 파일로 낸다. ARCH 4.3 표 정리는 원래 지정된 M2-01에서 한다.
- Flyway가 DDL을 관리한다. `ddl-auto=validate`, UTC 설정을 유지하며 SQL init·baseline·out-of-order·clean은 사용하지 않는다.

### 수용 기준과 실행 증거

검증 환경은 Windows, Corretto 21.0.10, Gradle Wrapper 9.8.2, Docker의 실제 MySQL 8.4.13이다. `.env`와 `application-local.yml` 없이 기본 local 설정으로 기동했다.

| TASKS M0-02 수용 기준 | 결과 | 실행 증거 |
|---|---|---|
| 빈 DB bootRun → V1~V4, validate, 정책 v1 구간 3개 | PASS — 실제 기동 + MySQL 통합 | 기동 전 Compose DB의 테이블 수 0. bootRun이 이력 테이블과 V1~V4를 생성하고 JPA 초기화·8080/8081 기동. SQL로 성공 이력 4건과 구간 3개 확인. 재기동 로그는 `Schema ... is up to date. No migration necessary.` |
| DATETIME(3) 저장·조회 정밀도, Clock의 선행 절삭 | PASS — 실제 Hibernate + MySQL | 테스트 전용 `member` 매핑으로 `.000`, `.123`, `.999`를 persist → flush → clear → find한 값이 동일. DB의 DATE_FORMAT 원시 UTC 문자열도 비교. 고정 시각 `.999600`은 tick Clock에서 `.999`로 잘린 뒤 저장되어 다음 초 마감 경계를 넘지 않음. 실제 주입 Clock의 값도 같은 경로로 확인 |
| JVM 기본 시간대 Asia/Seoul에서도 동일 | PASS — 별도 JVM + 실제 MySQL | `test`의 `-Duser.timezone=UTC`와 `testSeoul`의 `-Duser.timezone=Asia/Seoul`에서 같은 테스트 실행. 테스트가 실제 JVM 기본 시간대를 확인하며 JDBC 세션은 UTC. 각각 22건, 실패·오류·건너뛰기 0건 |
| 금지 출처 0건, 의도한 위반 시 빌드 실패 | PASS — Gradle + 실제 실패 재현 | 정상 운영 소스 14개 파일에서 0건. 임시 Java 파일의 호출·쿼리 위반 11건, 임시 새 SQL의 위반 2건을 각각 `clean build`가 exit 1로 거절. 두 임시 파일 제거와 V1~V4 해시 불변 확인 후 같은 `clean build` 최종 성공 |
| 시작가 12,345원 Item INSERT 거절 | PASS — 실제 CHECK | 정상 USER와 시작가 12,000 Item INSERT가 성공한 같은 테스트에서 12,345 INSERT가 MySQL 오류 3819, `ck_item_start_price`로 거절됨. 외래 키나 다른 제약에 의한 실패와 구분 |

실제 업무 엔티티는 아직 없다. 따라서 bootRun의 `validate` 성공은 전체 제품 엔티티 매핑 검증을 뜻하지 않는다. 통합 테스트는 테스트 전용 매핑의 Hibernate validation·저장 경로와 업무 테이블 10개의 존재·InnoDB·정렬·시간 컬럼 DATETIME(3)·DB 시간 기본값 부재를 각각 검증한다. 테스트 전용 매핑과 임시 위반 파일은 패키징 JAR에 없다.

추가 CHECK 검증도 실제 DB에서 통과했다. OPERATOR 원인이 있는 PAUSED 레인과 NO_BID 사유가 있는 UNSOLD Lot은 INSERT가 성공하고, 같은 상태에서 사유를 NULL로 넣으면 각각 `ck_lane_pause`, `ck_lot_unsold_reason`의 오류 3819로 거절된다. USER 테스트 데이터는 롤백되며 로컬 Compose의 `member` 행은 0개다.

### 단일 검증 명령과 결과

- CI 진입점은 **`cd backend && ./gradlew clean build`**다. 루트 집계 작업에 시간 출처 검사·engine/app 빌드·일반 테스트·Seoul 테스트를 연결했다. Windows에서는 `./gradlew.bat clean build`를 쓴다. 실제 GitHub Actions 작성·실행은 M0-04이며 이번에는 **NOT_RUN**이다.
- 최종 `clean build`: 성공. `test` 22건 + `testSeoul` 22건 = **총 44회 테스트 실행, 실패 0·오류 0·건너뛰기 0**. 서로 다른 테스트 44개라는 뜻은 아니다. 각 실행은 기존 Clock/MVC 11건 + 신규 MySQL 통합 11건이다.
- TASKS의 `./gradlew test`도 성공했다. 최종 빌드와 입력이 같아 app 테스트는 UP-TO-DATE였고, 시간 출처 검사는 다시 실행되어 0건을 확인했다. 실제 테스트 실행 증거는 위 최종 빌드다.
- engine은 아직 도메인 구현·테스트가 없어 `engine:test NO-SOURCE`다. 엔진 기능 검증으로 해석하지 않는다.
- 프론트 `npm run typecheck`, `npm run build`: 성공. 프론트 소스는 변경하지 않았다.
- 실제 HTTP: 관리 포트 health 200/UP, 앱·Vite 프록시의 없는 API 경로는 404/NOT_FOUND JSON. Flyway 도입 뒤 기존 공통 계약이 유지된다.
- 정적 검사는 engine/app의 운영 Java·SQL·XML·YAML·properties를 대상으로 한다. Java 호출은 일반 표기·완전 수식명·정적 import·공백 변형을 검사하고, Java 문자열·text block의 SQL/JPQL과 SQL 파일의 NOW(정밀도 포함)·CURRENT_TIMESTAMP도 검사한다. 주석과 테스트·빌드 산출물·외부 라이브러리 내부는 제외한다. 소스 검색의 판별 한계는 ADR-002에 기록했다.

### 재현 중 수정한 문제와 증거 위치

- 처음 `clean`은 기존 M0-01 bootRun이 engine JAR을 열고 있어 Windows 파일 잠금으로 실패했다. 해당 앱 프로세스를 확인해 종료한 뒤 재실행했다. 실행 중인 bootRun을 먼저 종료하고 clean을 사용해야 한다.
- Testcontainers는 tag와 digest를 함께 쓴 문자열을 호환 이미지로 해석하지 못해, **같은 manifest**를 digest-only 이름으로 전달했다. 테스트에서 실제 버전 8.4.13을 확인한다.
- 컨테이너 JDBC URL에 `&`로 매개변수를 직접 붙이던 초기 코드는 URL에 `?`가 없을 때 DB 이름으로 해석되어 실패했다. `withUrlParam`으로 바꿨다. 테스트를 건너뛰거나 SQL을 바꿔 해결하지 않았다.
- Java 정적 import 검사에서 공백 뒤의 수식 호출을 중복 집계하던 부분을 실패 재현 중 발견해 제거했다. 최종 실패 로그는 서로 다른 위반 11건을 가리킨다.
- 최종 테스트 보고서: `backend/app/build/reports/tests/test/index.html`, `backend/app/build/reports/tests/testSeoul/index.html` 및 각 `test-results` XML.
- 정상/의도한 실패: `output/m0-02-final-build.log`, `output/m0-02-java-violation.log`, `output/m0-02-sql-violation.log`, `output/m0-02-violation-checks.json`.
- 실제 기동·DB·HTTP: `output/m0-02-before-boot-db.txt`, `output/m0-02-bootrun.log`, `output/m0-02-bootrun-restart.log`, `output/m0-02-schema-evidence.txt`, `output/m0-02-http-checks.json`.
- 의존성 해석: `output/m0-02-dependencies.log` (runtime), `output/m0-02-initial-build.log` (Testcontainers 해석 포함). 위 산출물은 gitignore 대상이다.

### 직접 확인하는 방법

JDK 21과 Docker가 필요하다. 아래 clean 검증 전에 실행 중인 bootRun 터미널에서 Ctrl+C로 종료한다. MySQL 볼륨은 지우지 않는다.

```powershell
# 저장소 루트
docker compose up -d mysql
$env:JAVA_HOME = 'C:\Users\jitin81\.jdks\corretto-21.0.10' # 자신의 JDK 21 경로
cd backend
./gradlew.bat clean build
# verifyTimeSources 0 violations, test/testSeoul 성공
./gradlew.bat test
./gradlew.bat :app:bootRun
```

별도 터미널의 루트에서 확인한다. `-p`에는 `.env.example`의 로컬 MYSQL_PASSWORD를 입력한다.

```powershell
docker compose exec mysql mysql -ufourlane -p fourlane_auction -e "SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank;"
# 1 member auth / 2 item / 3 bid policy / 4 auction, success=1
docker compose exec mysql mysql -ufourlane -p fourlane_auction -e "SELECT policy_id, min_price, increment FROM bid_increment_tier ORDER BY min_price; SELECT COUNT(*) AS admin_count FROM member WHERE role='ADMIN';"
# 구간 3개, admin_count=0
curl.exe http://localhost:8081/actuator/health
# status=UP
cd frontend
npm run typecheck
npm run build
```

정적 검사 실패를 직접 재현하려면 운영 Java에 임시로 `Instant.now()`를 넣거나, **새 임시 SQL 파일**에 `SELECT NOW();`를 넣고 `./gradlew.bat clean build`를 실행한다. `verifyTimeSources`가 경로·행 번호와 함께 실패해야 한다. 임시 변경을 제거하고 같은 명령이 성공하는지 확인한다. 적용된 V1~V4는 수정하지 않는다. 이번 실제 실패 재현의 임시 파일은 모두 제거되어 있다.

제품 엔티티·API·WebSocket·보안·ADMIN 생성 방식·CI 워크플로는 후속 범위다. 배포·Linux/macOS 실행·경매 동작·동시성·복구·성능 검증은 **NOT_RUN**이다.

## M0-03 연결 확인 화면 (REST + WebSocket)

- 작업일: 2026-10-27 (Asia/Seoul)
- 상태: 완료 (M0-03 범위, 수용 기준과 아래 실제 실행 증거 기준)
- 원본: TASKS M0-03, API 1.2·1.4·1.5·4.1, EVENTS 1장과 사용자 보충
- 시작 SHA: `354b2ae36bf53c96b6a1dad4720a79fd8ee6aa3b`, `dev`, 작업 트리 clean
- 작업 위치: 기본 checkout `C:\Users\jitin81\Documents\dev\lab\four-lane-auction` (별도 worktree 없음)
- 승인: 사용자 2026-10-27. Orval + Fetch, springdoc 3.2.0, 키셋 커서와 [BE] → [GEN] → [FE] → [통합] 계획 승인. 토큰 포함 CONNECT 임시 거절도 명시 승인
- 결정 기록: [ADR-003](adr/ADR-003-M0-03-api-generation-and-connections.md)

### 버전 확인 및 고정

확인일은 2026-10-27이다. 프론트 직접 의존성은 정확한 버전과 lockfile로 고정하고, WebSocket은 기존 Boot BOM 조합을 따른다.

| 대상 | 선택 버전 | 확인한 출처 |
|---|---|---|
| springdoc-openapi-starter-webmvc-api | 3.2.0 | [공식 릴리스](https://github.com/springdoc/springdoc-openapi/releases/tag/v3.2.0): Boot 4.1.2 기반. [호환 표](https://springdoc.org/#what-is-the-compatibility-matrix-of-springdoc-openapi-with-spring-boot), [Maven metadata](https://repo.maven.apache.org/maven2/org/springdoc/springdoc-openapi-starter-webmvc-api/maven-metadata.xml) |
| Spring WebSocket·Messaging / WebSocket starter | Boot 4.1.2 BOM (Framework 7.0.10) | 기존 승인 BOM. 개별 버전을 덮어쓰지 않는다 |
| Orval | 8.45.0 (개발 의존성) | [npm metadata](https://registry.npmjs.org/orval/8.45.0), [Fetch 생성 문서](https://orval.dev/docs/guides/fetch/) |
| 런타임 HTTP | 브라우저 Fetch | Orval 생성 코드 내부에서 사용. 추가 HTTP 런타임 의존성 없음 |
| @stomp/stompjs | 7.5.0 | [npm metadata](https://registry.npmjs.org/@stomp%2Fstompjs/7.5.0), EVENTS 1.1 |

비교 대안은 openapi-typescript 7.13.0 + openapi-fetch 0.17.0, springdoc 3.1.1, offset 기반 커서였다. 선택 이유는 ADR-003에 기록했다.

### 승인한 임시 제한과 후속 작업

- **M1-02**: Authorization 헤더가 있는 CONNECT는 빈 값도 `ERROR message:TOKEN_INVALID` 뒤 종료한다. JWT 검증의 완료를 뜻하지 않는다. 같은 JwtDecoder로 유효/만료/위조를 구분하고 Principal·인증된 개인 큐를 구현할 때 이 임시 거절을 제거한다.
- **M1-08**: 홈의 익명 STOMP 연결은 M0 진단용 예외다. EVENTS 1.1의 비로그인 연결 시점(관전 화면)에 맞춰 연결 수명을 정리한다.
- 회차·레인·Lot 엔티티는 현재 조회용 부분 매핑이며 `@Immutable`이다. 후속 생성·상태 전이 구현 시 해당 매핑을 확장한다. 이번 작업에서 상태 전이나 쓰기 기능의 완료를 주장하지 않는다.

### 단계별 실행 증거

- **[BE] PASS**: Corretto 21에서 `./gradlew.bat test build` 성공 (`output/m0-03-be-final-build.log`). UTC 70건 + 별도 JVM Asia/Seoul 70건, 각각 failures/errors/skipped 0. 기존 22건과 새 MVC 11건·인터셉터 13건·실제 MySQL/HTTP/STOMP 통합 24건이다. engine은 도메인 코드가 없어 `test NO-SOURCE`이며 엔진 기능 검증으로 해석하지 않는다.
- 실제 MySQL 테스트: 세 상태의 정렬, 시각 동률의 ID 정렬과 2개씩 페이지 이동, 마지막 기준 행 삭제·앞쪽 삽입 후 계속 조회, 기본 20/최대 100, 두 레인의 Lot 합계, 잘못된/다른 상태 커서 400, 필수 nullable 필드를 검증했다. 테스트 fixture는 Testcontainers에만 만들었다.
- 실제 `WebSocketStompClient`: 익명 CONNECTED(STOMP 1.2, 10초 하트비트), 숫자 레인 구독, SEND 세 목적지 차단, 개인 큐·직접 큐·허용 목록 밖 구독 차단, ERROR message와 연결 종료, 토큰 헤더 임시 거절, 미허용 Origin의 403을 확인했다. 테스트 토픽이나 이벤트 발행 코드는 추가하지 않았다.
- 실제 bootRun + Compose MySQL: 상태별 목록 200·빈 배열·null 커서·밀리초 3자리 UTC serverTime, status 누락 400 INVALID_REQUEST 확인 (`output/m0-03-http-checks.json`). `/v3/api-docs`는 `/api/sessions`만 노출하며 필수·nullable·enum·오류 스키마를 계약 테스트로 검사했다 (`output/m0-03-openapi.json`).
- 시간 출처 검사: 운영 파일 29개, 0 violations. V1~V4 수정 없음. 최초 테스트 클라이언트의 하트비트 스케줄러 누락과 STOMP 헤더 접근 오류를 수정한 뒤 위 최종 명령을 성공시켰다. 동일 checkout의 이전 앱·Vite 프로세스가 기본 포트를 사용하고 있어 소유 경로를 확인해 종료한 뒤 최신 백엔드를 기동했다.
- [GEN] 최초 타입 검사에서 응답 content type이 OpenAPI에 `*/*`로 나가 Orval이 문자열을 생성하는 문제를 발견했다. [BE]의 매핑·응답 애노테이션에 계약의 `application/json`을 명시하고 200/400/500의 OpenAPI media type 회귀 검증을 추가했다. `test build` 재실행 성공 (`output/m0-03-be-json-build.log`, UTC·서울 각각 70건, 실패·오류·skip 0). API.md·EVENTS.md의 계약 변경이나 생성 파일 수동 수정으로 해결하지 않았다.
- **[GEN] PASS**: 실행 중인 `http://127.0.0.1:8080/v3/api-docs`에서 Orval 8.45.0으로 요청 함수·모델을 `frontend/src/api/generated/`에 생성했다 (`output/m0-03-gen-api.log`). 생성된 호출 경로는 `/api/sessions`이며 JSON을 파싱한다. 필수 status와 응답의 필수 nullable 필드가 타입에 반영됐고 `npm run typecheck`가 통과했다. 같은 입력으로 재생성한 파일 해시도 동일하다 (`output/m0-03-gen-api-repeat.log`). Orval 설치 audit은 0 vulnerabilities였다.
- **[FE] PASS — 타입·빌드**: `@stomp/stompjs` 7.5.0 설치, `npm run typecheck`·`npm run build` 성공 (`output/m0-03-fe-checks.log`). 세 상태별 Infinite Query와 다음 커서의 더 보기, 로딩·빈 상태·오류·다시 조회, 30초/화면 복귀 재조회, 서버 시각의 마지막 수신값, 독립된 REST·STOMP 배지를 구현했다. 브라우저 REST는 생성된 요청 함수만 호출한다. STOMP는 현재 location의 같은 출처 `/ws`, 10초 하트비트, 최대 30초의 지수 백오프와 시도별 jitter를 사용한다. 마지막 구독자가 떠난 뒤 소켓 종료를 기다려 StrictMode 재마운트의 중복 연결을 막는다. 브라우저 결과는 아래 [통합]에 별도로 기록한다.

### [통합] 실제 브라우저 검증

Windows Chrome + Playwright CLI에서 실제 Compose MySQL·bootRun·Vite를 연결해 실행했다. 데스크톱 1440×960과 모바일 390×844를 확인했고, 각각 문서 폭이 뷰포트 폭과 같았다. 정상 연결 상태에서 console error/warning은 0이었다.

- 세 목록은 실제 API 응답의 `items: []`로 빈 상태를 표시했다. REST는 세 상태의 조회 결과로, WebSocket은 실제 STOMP CONNECTED 프레임을 수신한 뒤 초록색이 됐다. 화면 serverTime이 실제 목록 응답의 값과 같은 것도 검사했다 (`output/m0-03-browser-initial.log`).
- 개발 모드 StrictMode를 포함한 최초 연결과 서버 종료·재시작 구간에서, 계측한 `/ws`의 동시 연결 최대값은 **탭당 1개**였다. Vite 개발용 소켓은 집계에서 제외했다. 이는 이번 기능 검증 구간의 관측이며 부하·성능 목표 측정이 아니다.
- 실제 자동 재조회에서 표시 시각이 `04:12:20.889Z` → `04:12:50.913Z`로 바뀌고 HTTP 응답이 도착하는 것을 확인했다. 모바일 문서 폭은 390이었다 (`output/m0-03-browser-poll-mobile.log`).
- 이 작업의 백엔드 프로세스를 확인해 종료하자 REST·WebSocket 배지가 모두 빨간색으로 바뀌고 목록별 오류 3개가 표시됐다. 마지막 수신 시각은 유지됐고 CONNECTED 성공 횟수는 늘지 않았다 (`output/m0-03-browser-offline.log`).
- 같은 백엔드를 재시작한 뒤 **페이지 새로고침 없이** 두 배지가 초록색으로 돌아왔다. CONNECTED 수신 횟수 1 → 2, 활성 소켓 1, 동시 최대 1, 오류 패널 0, 새로운 응답 시각 `04:14:51.157Z`를 확인했다 (`output/m0-03-browser-recovery.log`). 이 구간의 uncaught page error는 0이었다. 서버 중단 중의 예상된 HTTP/WebSocket 네트워크 실패는 브라우저 콘솔에 기록됐다.
- 로딩은 별도 브라우저 세션에서 실제 회차 요청을 잠시 보류한 뒤 그대로 백엔드에 전달해 확인했다. 로딩 표시 3개가 나온 뒤 실제 응답으로 연결 상태가 정상화됐다. 응답을 합성하지 않았다 (`output/m0-03-browser-loading.log`). 최초 검증 세션의 잔류 route handler가 간섭해 이 검사가 실패했으며, 새 브라우저 세션에서 최종 명령은 성공했다.
- 캡처: `output/playwright/m0-03-desktop.png`, `m0-03-mobile.png`, `m0-03-offline.png`, `m0-03-recovered.png`, `m0-03-loading.png`. 테스트·빌드·로그·캡처는 gitignore 대상이다.

### TASKS M0-03 수용 기준별 결과

| 수용 기준 | 결과 | 확인한 증거 |
|---|---|---|
| SCHEDULED 조회 200, items [], nextCursor null, 밀리초 3자리 UTC serverTime | PASS | 실제 HTTP·실제 MySQL·고정 Clock 계약 테스트, 브라우저 표시와 응답값 대조 |
| status 누락 400 INVALID_REQUEST | PASS | MVC 및 실제 curl/HTTP. error.fields에 status/REQUIRED |
| 익명 CONNECT → CONNECTED, SEND·직접 /queue 구독 거절 | PASS | WebSocketStompClient의 실제 연결·ERROR·연결 종료. 브라우저 STOMP CONNECTED 수신도 확인 |
| 프론트는 생성 클라이언트로만 목록 호출 | PASS | sessionQueries가 생성 listSessions만 호출. Git 소스 검사상 수동 fetch 없음. 실제 브라우저의 같은 출처 /api/sessions 호출 확인 |

API.md·EVENTS.md·TASKS.md 원문은 수정하지 않았다. 진단 API와 테스트 토픽은 추가하지 않았다. 승인한 임시 제한 두 가지는 위 후속 작업 및 ADR-003에 남아 있다.

### 직접 확인하는 방법

현재 검증용 백엔드(앱 8080·관리 8081), Vite(5173), MySQL이 실행 중이다. [http://127.0.0.1:5173/](http://127.0.0.1:5173/)에서 바로 볼 수 있다. 처음부터 재현할 때는 기존 bootRun·Vite를 종료한 뒤 루트에서 실행한다.

```powershell
docker compose up -d mysql
$env:JAVA_HOME = 'C:\Users\jitin81\.jdks\corretto-21.0.10' # 자신의 JDK 21 경로
cd backend
./gradlew.bat test build
./gradlew.bat :app:bootRun
```

별도 터미널에서 루트 기준으로 실행한다. gen-api는 위 백엔드가 실행 중이어야 한다. 다른 주소는 `OPENAPI_URL` 환경변수에 `/v3/api-docs` URL을 지정한다.

```powershell
cd frontend
npm ci
npm run gen-api
npm run typecheck
npm run build
npm run dev
```

```powershell
curl.exe 'http://127.0.0.1:8080/api/sessions?status=SCHEDULED'
curl.exe -i 'http://127.0.0.1:8080/api/sessions'
```

홈에서 세 빈 목록·초록 배지·서버 시각을 확인한다. 개발자 도구 Network의 `/ws`에서 CONNECT/CONNECTED와 `heart-beat:10000,10000`을 확인할 수 있다. bootRun을 Ctrl+C로 종료하면 WebSocket 배지가 끊김/재연결 중으로, REST는 다음 30초 재조회에서 끊김으로 바뀐다. bootRun을 다시 실행하면 페이지를 새로고침하지 않아도 재연결과 다음 REST 재조회로 초록색으로 돌아온다.

### 증거의 범위

- [BE] → [GEN] → [FE] → [통합] 순서로 수행했다. [GEN]의 media type 오류는 [BE]로 돌아가 수정·검증한 뒤 재생성했다. [FE]에서 backend를 수정하지 않았다. 생성 결과는 별도 커밋에 포함했다.
- JWT 실제 검증·로그인/개인 큐(M1-02), 관전 화면의 연결 수명 정리(M1-08), 레인 이벤트·비컨·스냅샷 재동기화·경매 엔진·입찰·영속 상태 복구·부하·배포는 **NOT_RUN**이며 이번 완료 범위 밖이다.
- 데이터가 있는 회차 카드·더 보기의 브라우저 검증은 **NOT_RUN**이다. 상태별 페이지·정렬·Lot 집계는 Testcontainers의 실제 MySQL에서 검증했고, 실제 로컬 DB의 빈 목록을 가짜 회차로 채우지 않았다.
