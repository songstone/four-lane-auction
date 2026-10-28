# Four-Lane Auction — 진행·검증 기록

## M1-06 회차 생성, 예정 시각 변경, 회차 목록

- 작업일: 2026-10-28 (Asia/Seoul).
- 상태: **완료 (M1-06 로컬 수용 기준·아래 실제 실행 증거 기준)**. BE → GEN → FE → 통합과 최종 빌드를 마쳤다. 원격 게시·CI 결과는 PR과 완료 보고에서 별도로 확인하며 머지는 별도 지시가 필요하다.
- 원본: TASKS 0장·M1-06·9장, PRD M-04·AC-04-1/2/3/5 저장/6·A-02·P-01·OQ-14/48, API 4.1·7.2, EVENTS 7, ARCH 3·4·5.5.1·5.7·7.7·9, CH-01·CH-02, ADR-001~010과 M0-03 부분 매핑 후속.
- 시작 SHA: `569c07d4ae56d899c0b4ec191855ea0acbd02b33`. fetch 후 dev=origin/dev·clean, 선행 M1-01 PR #4 ancestry 확인 후 `feature/M1-06-session-create` 생성.
- 승인: 사용자 ①~⑥ 권장안과 변경·검증·로컬 커밋·push·dev 대상 PR 생성 승인. 머지는 별도 지시가 필요하다.
- 결정 기록: [ADR-011](adr/ADR-011-M1-06-session-create.md), PRD OQ-14, API 0.8, ARCH 부록 A·CH-01 후보 B1의 홈 적용 표시. 새 의존성·EVENTS 변경 없음.

### 승인한 구현과 경계

- 실제 시작 전 SCHEDULED 동안 예정 시각·네 시간 값을 바꾼다. 예정 시각이 지났어도 timing만 변경할 수 있다. 시간 값은 각각 1,000~60,000ms 정수이며 배수·상호 대소 제약은 없다.
- 기본값은 application.yml 한 곳의 15,000/10,000/5,000/10,000ms다. 설정 포트는 domain에, 설정 바인딩은 infra에 두어 application이 infra를 참조하지 않는다. 잘못된 기본 설정은 기동 실패한다.
- 회차와 READY 레인은 application 트랜잭션 하나로 생성하며 Clock 1회의 값으로 미래 검증·생성/수정 시각을 채운다. 회차·레인·Lot의 @Immutable 부분 매핑을 확장했다. Lot 편성·경매 상태 전이 API는 후속이다.
- PATCH는 회차 PK 비관적 락 뒤 상태 확인·Clock 1회·부분 변경이다. 같은 시각·과거는 422, 오프셋 누락·밀리초 미만의 손실 있는 시각·명시적 null·정수 아닌 값은 400이다. 빈 PATCH는 값과 updated_at을 보존한다.
- ADMIN 상세는 실제 레인·Lot의 프로젝션으로 빈 레인·시작 가능 여부를 계산한다. 최저 낙찰가는 회차 응답에 포함하지 않는다. 기존 공개 GET 목록 계약·커서·정렬을 유지한다.
- V7은 시간 범위 CHECK만 추가한다. V1~V6은 수정하지 않으며 기존 범위 밖 데이터를 자동 보정하지 않는다. 착수 시 로컬 회차는 0건이었다. 이후 번호는 V8부터다.

### 단계별 실제 실행 증거

- 결정·계약 선행 커밋: `e28edaf`.
- 실제 bootRun의 V7 적용·Hibernate validate·기동 확인: `output/m1-06/bootRun.log`, `local-migrations.txt`.
- curl 12사례의 HTTP 상태·UTC 변환·권한 검증: `output/m1-06/curl-checks.json` 및 같은 폴더 응답 JSON. 비밀값이 있는 로그인·요청 파일은 ignored output에만 보관한다.
- BE 전체 첫 실행은 마이그레이션 이력 6건을 기대한 기존 테스트 2건이 실패했다. V7 기대값으로 수정 후 `gradlew test` 성공 (`output/m1-06/be-all-tests.log`). app 241건은 실제 실행, engine 10건은 해당 단계 UP-TO-DATE였으며 최종 clean build에서 모두 새로 실행한다. 테스트를 삭제·skip하지 않았다. 기존 GET 테스트 두 파일은 수정하지 않았다.
- [GEN] 실행 중인 백엔드에서 Orval 8.45.0 `npm run gen-api`와 생성 후 typecheck 성공. 생성 코드 커밋 `8bd6370`, 로그 `gen-api.log`, `gen-typecheck.log`.
- [FE] 최종 `npm run lint`, `npm run typecheck`, `npm run build` 성공. 로그 `fe-lint.log`, `fe-typecheck.log`, `fe-build.log`. FE 커밋 `f6563ac`, BE 커밋 `cd912b6`. BE 이후 backend diff 0건, 생성 클라이언트 밖 직접 fetch 0건.
- 최종 `cd backend; .\gradlew.bat clean build` 성공: 16개 태스크 모두 실행, verifyTimeSources 122개 운영 파일·0 violations. UTC `test` 251건(app 241 + engine 10), `testSeoul` 241건(app), 각 failures/errors/skipped 0. 순수 정책 엔진은 UTC에서만 실행되며 app의 같은 테스트는 UTC·Asia/Seoul에서 각각 실행됐다.
- JUnit XML의 testcase 수·실패/skip 요소·필수 실제 MySQL suite를 대조한 `.github/scripts/verify-test-results.py` PASS. 증거: `backend-clean-build.log`, `test-summary.json`, `ci-report-verifier.log`, `backend/*/build/test-results/{test,testSeoul}`.
- 최종 FE 커밋은 `f6563ac`과 `ed9d844`다. 완료 검토에서 startable=false를 미편성으로 오해하지 않도록 실제 emptyLaneNos로 안내를 구분하고 lint·typecheck·build를 다시 통과했다.
- 작업 diff의 로컬 비밀번호·JWT 키·ADMIN 해시 표식 0건 (`credential-scan.json`), `git diff --check` 통과. push·PR 생성은 이번 승인 범위이며 원격 CI·링크는 PR과 완료 보고에서 별도로 확인한다.

### 실제 화면 검증

새 Chrome 컨텍스트 `m106`에서 bootRun(8080) + Vite(5173)의 실제 응답을 사용했다. 아래 로그는 `output/playwright/m1-06/`, 스크립트는 ignored `output/m1-06/`에 있다.

- `ui-login.log`: 익명 A-02 접근 → 로그인 → 원래 A-02 복귀, ADMIN 메뉴 확인.
- `ui-create.log`: 빈 시각·범위 999ms는 POST 0건. 유효 생성은 더블클릭에도 POST 1건, 실제 201. 브라우저 현지 시각 `2026-11-04T20:00:00.123+09:00` 요청 → UTC `11:00:00.123Z` 응답, 서버 기본값 네 개 확인. 실제 회차 #3 생성.
- `ui-patch.log`: #3의 예정 시각과 softCloseMs만 PATCH, 나머지 시간 값 보존. 과거 시각 생성은 실제 422와 화면 메시지. 모바일 viewport/document 390/390px. 제품 모듈을 실제 브라우저에서 import해 표본 최댓값·60초 만료·잘못된 날짜 거절을 검증했다.
- `ui-countdown.log`: 화면에서 약 12초 뒤의 회차 #4(레인 2개)를 생성. 홈에서 남은 시간 표시 후 실제 시간 경과로 시작 대기 중 전환. Date.now를 +300,000/-300,000ms로 주입한 두 경우 모두 같은 12초 표시. OS 시계 변경이나 200ms 지연 목표 측정은 아니다.
- `ui-overdue.log`: #4의 예정 시각이 지난 뒤 UI에서 softCloseMs만 2345로 변경. 실제 요청 `{timing:{softCloseMs:2345}}`, 200, 기존 과거 예정 시각 보존. 실제 RUNNING 회차 0건에서 빈 상태 확인.
- `ui-states.log`: 원본 GET 전달을 지연해 로딩 확인. 연결 실패 주입 후 실제 GET 재조회 복구. POST 연결 실패는 1회만 시도하고 자동 재등록 없음. 성공 JSON을 합성하지 않았다. uncaught page error 0, local/session storage 키 0.
- `ui-user-guard.log`: USER에게 운영자 메뉴·생성 폼 없음, 직접 URL은 접근 권한 없음. 로그아웃 후 익명으로 종료.
- 시각 확인 이미지: `created-desktop.png`, `updated-mobile.png`, `home-countdown.png`, `home-waiting.png`, `overdue-timing-update.png`, `list-loading.png`, `list-empty.png`, `list-network-error.png`, `create-network-error.png`, `user-forbidden.png`.
- 브라우저 콘솔에는 의도한 401/422·연결 실패와 로그아웃 시 아직 연결 중이던 WebSocket 종료 경고가 있다. 이를 uncaught page error와 구분한다. 기존 익명/ADMIN 연결 수명 정리는 M1-08 후속이다.

### 수용 기준별 판정

| 기준 | 결과 | 실제 근거 |
|---|---|---|
| AC-04-1 | PASS | AdminSessionHttpIntegrationTest 1~4개 생성·SCHEDULED·READY·레인 번호·동일 시각·목록 확인. 실제 UI #3·#4 생성·홈 표시 |
| AC-04-2 | PASS | SessionValidationTest·AdminSessionControllerTest의 0·5·누락·타입 오류, 실제 화면 필수값·999ms 오류와 POST 0건 |
| AC-04-3 | PASS | 전체/부분 생략 기본값, 설정 재정의·잘못된 설정 기동 실패, 실제 POST·화면 응답의 15000/10000/5000/10000 |
| AC-04-5 저장 부분 | PASS | 실제 MySQL 원시 UTC 문자열과 응답 일치, 오프셋 누락 400. UTC·Seoul 최종 재실행 결과는 빌드 절 참고 |
| AC-04-6 | PASS | 생성·PATCH 각각 Clock -1ms/정확히 지금/+1ms 경계. 실제 curl·화면의 과거 요청 422 |
| OQ-14 | PASS | 각 값 999/1000/1001/59999/60000/60001, 정수·null 검증, 실제 DB CHECK 3819. 과거 SCHEDULED의 timing-only 수정 200 |
| 예정 시각·부분 수정 | PASS | 전달 필드만 변경·빈 PATCH timestamp 보존·404·RUNNING/FINISHED 422, 실제 UI 요청 본문·응답 확인 |
| 동시성·원자성 | PASS | 같은/다른 필드 각각 500쌍, 락 대기 중 Clock·상태 재검사. 레인 INSERT 실패와 beforeCommit 실패 롤백 |
| 기존 GET 회귀 | PASS | 기존 SessionControllerTest 11건·SessionHttpAndStompIntegrationTest 24건, 파일 변경 없이 통과 |
| 응답 계약·최저가 비노출 | PASS | 전체 handler 응답 카탈로그와 저장된 최저가 표식의 실제 비어 있지 않은 Lot 응답 검사 |
| ADMIN 접근·화면 상태 | PASS | 실제 HTTP 401/403 및 USER URL 가드, 로딩·실제 빈 목록·오류·복구·모바일 확인 |
| AC-04-4·AC-04-5 자동 시작 부분 | NOT_RUN — 후속 | TASKS가 M2-02에 배정한 자동 시작·재예약·시작 경합은 이번에 구현하지 않음 |

### 직접 확인하는 방법

Windows의 clean은 실행 중인 bootRun이 engine JAR를 잡고 있으면 실패한다. 먼저 bootRun을 종료한 뒤 검사하고 다시 실행한다. 인증 환경 준비는 기존 M1-01 절·ADR-006을 따른다.

```powershell
# 저장소 루트에서, 실행 중인 bootRun 종료 후
cd backend
.\gradlew.bat clean build
.\gradlew.bat :app:bootRun
# 별도 터미널, 저장소 루트에서
cd frontend
npm run lint
npm run typecheck
npm run build
npm run dev
```

```powershell
$taskEmail = Read-Host 'ADMIN 이메일'
$taskPassword = Read-Host 'ADMIN 비밀번호' -MaskInput
$taskLogin = @{email=$taskEmail;password=$taskPassword} | ConvertTo-Json -Compress
$taskAdmin = (Invoke-RestMethod -Method Post -Uri http://127.0.0.1:8080/api/auth/login -ContentType application/json -Body $taskLogin).accessToken
$taskHeaders = @{Authorization="Bearer $taskAdmin"}
$taskSchedule = [DateTimeOffset]::Now.AddHours(1).ToString('yyyy-MM-ddTHH:mm:ss.fffzzz')
$taskInput = @{laneCount=2;scheduledStartAt=$taskSchedule} | ConvertTo-Json -Compress
$taskCreated = Invoke-RestMethod -Method Post -Uri http://127.0.0.1:8080/api/admin/sessions -Headers $taskHeaders -ContentType application/json -Body $taskInput
$taskId = $taskCreated.session.sessionId
Invoke-RestMethod -Uri "http://127.0.0.1:8080/api/admin/sessions/$taskId" -Headers $taskHeaders
Invoke-RestMethod -Method Patch -Uri "http://127.0.0.1:8080/api/admin/sessions/$taskId" -Headers $taskHeaders -ContentType application/json -Body '{"timing":{"softCloseMs":8000}}'
Invoke-RestMethod -Uri 'http://127.0.0.1:8080/api/sessions?status=SCHEDULED'
```

1. `http://127.0.0.1:5173/#/admin/sessions`에서 ADMIN 로그인 → 레인 1~4·미래 현지 시각으로 생성 → 선택 회차의 기본값·레인 수 확인.
2. 예정 시각과 시간 값 하나를 변경 → 홈의 예정 목록과 남은 시간 확인. 999ms·과거 시각으로 오류 확인.
3. 가까운 미래의 회차를 만들고 시각 경과 뒤 홈의 시작 대기 중 확인 → 운영자 화면에서 예정 시각을 보존하고 시간 값만 저장.
4. USER 로그인 → 운영자 메뉴 비노출·직접 URL 차단 확인.

### 증거의 범위

Testcontainers MySQL·가짜 Clock 경계·반복 경합·실제 로컬 HTTP·브라우저 증거다. 테스트용 DB 상태 변경을 실제 회차 시작 증거로 보지 않는다. beforeCommit·CHECK 실패 주입은 DB 커밋 ACK 유실·네트워크 단절·전원 장애 증거가 아니다. 운영 TLS·AWS·성능·복구·자동 시작은 NOT_RUN이다. 카운트다운의 ±5분 검사는 브라우저 Date.now 주입이며 OS 시계 변경·네트워크 지연 200ms 목표의 측정이 아니다.

### 재현 중 확인한 사항

- MySQL CHECK 위반은 JDBC SQLState HY000·오류 3819이며 현재 Spring에서는 UncategorizedSQLException으로 번역됐다. 테스트는 구체적인 DB 오류 3819를 확인하도록 수정했다. 제약을 완화하지 않았다.
- timing만 바꾸는 과거 회차 테스트에서 Clock을 크게 이동시키면 JWT도 만료된다. 기존 예정 시각을 테스트 DB에서 과거로 설정해 업무 조건을 분리했다.
- PowerShell ConvertFrom-Json은 ISO 시각을 DateTime으로 변환할 수 있어 curl 증거 스크립트는 DateKind String을 사용해 원문 UTC 문자열을 비교한다.
- 실행 중인 bootRun의 engine JAR 파일 잠금 때문에 첫 clean이 실패했다. 해당 앱 프로세스를 종료한 뒤 같은 clean build를 다시 실행했고 컴파일 뒤 서버를 재기동했다. 실패 로그는 backend-clean-build-file-lock.log다.
- Playwright의 datetime-local fill에서 0초의 정규화 형태가 Malformed value를 일으킨 검증 스크립트는 밀리초를 명시해 다시 실행했다. 제품 응답을 합성하거나 검증을 건너뛰지 않았다.

### 핵심 이유·깨질 수 있는 상황·후속

- 같은 회차를 바꾸는 모든 경로가 같은 회차 행을 먼저 잠가야 변경 유실·시작 뒤 수정이 방지된다. 잠금 전 미리 읽은 엔티티·우회 쓰기·락 순서 역전은 이 보장을 깨뜨릴 수 있다. 대기는 DB 커넥션을 점유한다.
- 커밋 전 실패는 회차·레인 또는 수정값 전체를 롤백한다. 커밋 ACK 유실은 별도 문제다. 생성 후 응답을 잃고 재등록하면 중복 회차가 생길 수 있어 자동 재시도하지 않는다.
- Clock 역행은 기록 시각을 역행시키고, 앞으로 점프하면 입력이 거절될 수 있다. +1ms 요청은 검증 시점에 미래이면 유효하다. 커밋 전에 그 시각이 지났다는 이유로 다시 거절하지 않는다.
- 예정 시각 변경은 목록의 정렬 키를 바꾼다. ADR-003대로 페이지 사이 스냅샷은 보장하지 않아 이동한 행을 놓치거나 다시 볼 수 있다. 변경 후 재조회·홈 30초 갱신으로 목록을 새로 맞춘다.
- 편성 쓰기는 M1-07, 자동 시작·시작 경합·정책 고정은 M2-02다. OQ-35·OQ-38·레인 엔진·복구·성능·운영 배포는 이번 결과로 완료 처리하지 않는다.

---

## M1-05 호가 정책 (Strategy와 버전 관리)

- 작업일: 2026-10-28 (Asia/Seoul).
- 상태: **완료 (M1-05 로컬 수용 기준·아래 실제 실행 증거 기준)**. BE → GEN → FE → 통합과 최종 빌드를 마쳤다. push·dev 대상 PR 생성은 이번 사용자 승인 범위다. 원격 게시·CI 결과는 PR과 완료 보고에서 별도로 확인하며 머지는 별도 지시가 필요하다.
- 원본: TASKS 0장·M1-05·9장·1.1, PRD M-08·S-04·AC-08-1/3·A-05·6.3·9.7, API 7.4, EVENTS의 nextPrice 계약, ARCH 3·4.4, ADR-001~009와 기존 후속 항목.
- 시작 SHA: `458663009ed2f027fd8d6f92bd178788fedabeae`. fetch 후 dev=origin/dev·clean, 선행 M1-01 PR #4 ancestry 확인 후 `feature/M1-05-bid-policy` 생성.
- 승인: 사용자 ① 전용 카운터 ② JS 안전 정수 상한 ③ FE 포함 및 변경·검증·기록·로컬 커밋·push·PR 생성 승인. TASKS 9장의 기존 OQ·B 중 이번 작업에 배정된 것은 없다.
- 결정 기록: [ADR-010](adr/ADR-010-M1-05-bid-policy.md), PRD OQ-56, API 0.7, ARCH 4장·부록 A. 새 의존성·EVENTS 변경·CHALLENGES 미결 후보 선택 없음.
- 커밋: `19cc0aa` 결정·계약, `065f600` BE, `2252681` GEN, `909b9aa` FE. 이 절은 마지막 docs 커밋이다.

### 승인한 구현과 경계

- engine의 `BidIncrementPolicy`·`TieredBidIncrementPolicy`는 java.*만 사용한다. 구간 하한 포함·엄격 증가를 검사하고 방어 복사한다. 다음 호가는 현재가에 해당 구간 단위를 더하며 `Math.addExact`로 넘침을 검출한다. 엔티티 → 엔진 변환은 app의 `BidPolicyMapper`가 맡는다.
- V6 `bid_policy_version_counter`를 기존 MAX(version)으로 초기화한다. application @Transactional 안에서 입력 검증 → 카운터 id=1 PK 잠금 → Clock 1회 → 카운터 증가·새 정책·구간 INSERT → 커밋 후 응답한다. 정책·구간은 `@Immutable`·수정 불가 컬럼·persist 전용 저장 포트로 다루며 수정·삭제 API가 없다. 직접 DB 관리자 UPDATE를 차단하는 트리거·권한 분리는 이번 범위가 아니다.
- 조회는 readOnly와 JPQL 프로젝션 한 번으로 정책·구간·작성자 닉네임을 읽는다. 버전 내림차순·하한 오름차순이며 조회 결과에서 최대 버전 하나만 isLatest다. 작성자 ID는 응답하지 않는다. 시드 작성자는 null이다.
- API는 정수 아닌 JSON을 조용히 long으로 바꾸지 않는다. 각 값은 최대 9,007,199,254,740,991이며 첫 하한 0·엄격 증가·양수 호가 단위·1,000원 배수 규칙을 함께 검사한다. 구간 하한에 배수 제한을 추가하지 않는다. 입력 순서도 정렬하지 않는다.
- FE는 생성 클라이언트만 호출한다. 최신 정책을 초안으로 불러오기·구간 추가/삭제·필드 오류·새 버전 등록·등록 후 목록 재조회, 운영자 메뉴·경로·로그인 복귀·계정별 캐시 제거를 구현했다. 자동 등록 재시도는 없고 결과가 불확실하면 목록 확인을 안내한다.
- AC-08-2의 실제 회차 시작 시 고정은 TASKS대로 M2-02·M2-06 후속이다. 이번 계산·버전 불변 검증을 경매 완주 증거로 쓰지 않는다.

### 단계별 실제 실행 증거

아래 로컬 로그·JSON은 `output/m1-05/`, 화면 로그·PNG는 `output/playwright/m1-05/`에 있다. 모두 gitignore 대상이며 인증 응답·자격 값은 `.local` 파일로 분리했다.

| 단계 | 실제 결과 | 증거 |
|---|---|---|
| BE 집중 | 정책·스키마·공용 계약 28개, 실패/오류/skip 0. engine 단위 테스트 10개도 실행 통과 | `be-focused.log`, `m1-05-compile.log` (output 루트) |
| BE 게이트 | backend 전체 test의 app 193개, 실패/오류/skip 0. 아래 bootRun curl 뒤 GEN 진입 | `be-all-tests.log`, `be-test-summary.json`, `be-test-results/` |
| 최종 BE | clean build PASS. 16 actionable tasks 모두 실행. engine 10개 + app UTC 193개 + app Seoul 193개 = 396회, 실패/오류/skip 0. app은 같은 193개를 두 JVM에서 실행한 것. 시간 출처 검사 107파일·위반 0 | `backend-clean-build.log`, `test-summary.json`, `test-results/` |
| 정책 계산 | 기본 v1의 경계값 4개와 시작가 50,000 → 첫 금액 60,000. 실제 MySQL v1 행을 app Mapper로 변환한 계산도 일치 | TieredBidIncrementPolicyTest, BidPolicyHttpIntegrationTest |
| 동시성·불변 | 최종 clean build의 UTC·Seoul 각 500라운드, 합계 1,000라운드·등록 명령 2,000건. 매 라운드 서로 다른 운영자 2명의 201 두 개·N+1/N+2·각 작성자/구간·기존 행 전체 불변 확인. 집중·BE 게이트에서도 각각 별도 통과 | BidPolicyConcurrencyTest. 성능·처리량 측정 아님 |
| 원자성·시간 | beforeCommit 실패와 실제 구간 INSERT CHECK 실패 때 카운터·정책·구간 전체 롤백, 다음 등록은 v2. 락 보유 중 Clock 이동 뒤 획득 후 시각 저장. INT 버전 넘침은 500·DB 불변 | BidPolicyFailureIntegrationTest |
| 입력·인가·계약 | 누락·빈 배열·중복·역순·음수·단위·문자열·소수·boolean·안전 정수 경계, 익명 401·USER 403·DB 불변. 필수/nullable·JSON media type·OpenAPI 정수·상한·실제 응답 카탈로그 | BidPolicyValidationTest, AdminBidPolicyControllerTest, BidPolicyHttpIntegrationTest, ReservePriceContractTest |
| curl | bootRun + Compose MySQL에서 조회·v2 등록·기존 값 보존·필드 오류·401/403·OpenAPI 등 17항목 PASS | `curl-checks.json`, 개별 JSON, `curl-checks.ps1` |
| 모듈·마이그레이션 | engine JAR의 jdeps 결과 java.base만 참조. V1~V5 Git diff 없음. 기존 local DB에 V6 적용·기동 확인 | `engine-runtime-dependencies.txt`, `bootRun.log`, Git diff |
| GEN | 실제 `/v3/api-docs`에서 Orval 8.45.0 생성·typecheck PASS. 필수 tiers, 정수 상한, 작성자 nullable 타입 확인 | `gen-api.log`, `gen-typecheck.log`, `2252681` |
| FE | lint·typecheck·build PASS. FE 진입 뒤 backend 소스는 `065f600`과 동일 | `fe-lint.log`, `fe-typecheck.log`, `fe-build.log`, Git diff |
| 증거 검사 | 기존 CI JUnit 리포트 검증기 PASS. Git 추적·비무시 파일과 이번 작업의 공개 가능한 로그·스크립트 324파일에서 실제 검증용 비밀값 5개 일치 0 | `ci-report-verifier.log`, `credential-scan.json`. `.local` 자격 파일·스크립트는 공개 검사 대상에서 제외 |

### 실제 화면 검증

Chrome의 새 m105 세션에서 실제 bootRun·Vite·MySQL로 실행했다. 합성 성공·목록 응답을 사용하지 않았다. 네트워크 실패·유한 지연을 주입한 검사는 각각 후크를 제거하고 실제 응답으로 복구했다.

| 시나리오 | 결과 | 증거 |
|---|---|---|
| 로그인 복귀·기존 정책 | 익명 A-05 진입 → ADMIN 로그인 → 원래 A-05 복귀. 시드 v1·curl v2와 메뉴 표시 | `ui-admin-login.log`, `admin-initial-desktop.png` |
| 필드 오류·구간 편집 | 첫 하한·배수·상한·중복 오류 표시, 잘못된 폼 POST 0건. 구간 추가·삭제 확인 | `ui-admin-register.log`, `duplicate-field-error.png` |
| 실제 등록·중복 클릭 | POST 전 1.5초 지연만 적용. 더블클릭에도 POST 1건·실제 201/v3·v1/v2 보존·목록 재조회 | `ui-admin-register.log`, `register-pending.png`, `registered-desktop.png` |
| 로딩·오류·복구 | 실제 GET 지연으로 로딩 표시. 연결 실패 뒤 실제 GET으로 복구. POST 연결 실패는 1번만 시도하고 안내·재조회, 버전 추가 없음 | `ui-states.log`, `list-loading.png`, `list-network-error.png`, `register-network-error.png` |
| 로그아웃 중 늦은 응답 | 실제 서버가 v4 커밋·201 생성한 뒤 원본 응답만 보류. 로그아웃 후 전달해도 익명 홈·메뉴 제거 유지 | `ui-logout-during-registration.log`, `logout-late-response.png` |
| ADMIN → USER | USER 메뉴 없음·A-05 직접 URL 차단·정책 요청 0건·이전 정책 캐시 비노출 | `ui-user-guard.log`, `user-direct-url-denied.png` |
| 화면·저장소 | 1440×1050·390×844 PNG를 직접 열어 확인. 모바일 문서 폭=390. 상태 검사 구간 uncaught pageerror 0, local/sessionStorage 키 0 | `registered-desktop.png`, `registered-mobile.png`, `ui-states.log` |

정상 DB는 v1을 항상 시드하므로 실제 빈 정책 목록 화면은 **NOT_RUN**이다. 빈 상태 분기는 구현했지만 이를 검증하려고 시드나 기존 정책을 삭제하지 않았다. 초기 인증 Refresh 401·의도한 연결 실패는 콘솔에 남으므로 모든 콘솔 항목이 0이라는 뜻은 아니다.

### 수용 기준별 판정

| 기준 | 결과 | 근거 |
|---|---|---|
| AC-08-1 | PASS | 9,999/10,000/99,999/100,000 → 10,999/20,000/109,999/125,000. engine 단위·실제 DB 정책 변환 검사 |
| AC-08-3 이번 범위 | PASS | 현재가로 시작가 50,000을 넣으면 첫 입찰 금액 60,000. 실제 Lot 시작·입찰 경로는 M2 후속 |
| 잘못된 정책 400(fields) | PASS | 필드 경로·reason, 안전 정수 초과·형식·정렬·배수·누락을 MVC/HTTP/curl로 확인, DB 불변 |
| 등록하면 버전 +1 | PASS | 실제 HTTP·curl·화면 등록과 작성자/시각/구간 저장 확인 |
| 기존 정책·구간 불변 | PASS | 순차·동시 등록 전후 모든 기존 컬럼 raw SQL 비교 |
| 두 운영자의 동시 등록 | PASS | 최종 clean build의 두 JVM 합계 1,000라운드에서 매번 201 두 개·연속 고유 버전·작성자 일치 |
| A-05 | PASS | 목록·구간 편집·등록·권한·로딩/오류/복구·중복 제출·계정 전환·모바일 실제 확인. 빈 목록은 위 NOT_RUN |
| AC-08-2 | NOT_RUN — 후속 | TASKS M1-05 설명대로 회차 시작·입찰이 있는 M2-02·M2-06에서 검증 |

### 재현 중 확인한 사항

- 최초 curl은 이전 동일 checkout 앱 PID에 연결되어 새 경로 404를 받았다. 프로세스의 checkout·메인 클래스를 확인해 종료하고 최신 bootRun을 기동한 뒤 17항목을 다시 통과했다. 초기 404를 기능 PASS에 포함하지 않는다.
- 최초 jdeps의 잘못된 `--summary` 옵션은 실패했다. 올바른 `-s`로 실행한 결과만 모듈 증거로 기록했다.
- 최종 clean 전에 최신 bootRun을 종료해 Windows의 engine JAR 잠금을 해제했다. clean·컴파일이 끝난 뒤 같은 소스를 기동해 화면 검증을 했다.

### 직접 확인하는 방법

기존 이 checkout의 bootRun을 종료한 뒤 실행한다. JDK·Docker·ADMIN 시드는 [DEPLOY](DEPLOY.md)의 로컬 절차를 따른다. JWT 키·로그인 비밀번호는 환경변수·개인 로컬 설정으로 공급하며 출력·커밋하지 않는다.

```powershell
$env:JAVA_HOME = 'C:\Users\jitin81\.jdks\corretto-21.0.10'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
Set-Location C:\Users\jitin81\Documents\dev\lab\four-lane-auction\backend
.\gradlew.bat clean build
# 정책 경합만: .\gradlew.bat :app:test --tests 'com.fourlane.auction.policy.BidPolicyConcurrencyTest'
Set-Location C:\Users\jitin81\Documents\dev\lab\four-lane-auction\frontend
npm run lint
npm run typecheck
npm run build
```

루트에서 `docker compose up -d mysql`, backend에서 JWT_SECRET_BASE64를 설정해 `./gradlew.bat :app:bootRun`, frontend에서 `npm run dev`로 실행한다. 이번 로컬 검증 DB의 v1~v4는 실제 API·화면 입력으로 남아 있다.

1. ADMIN으로 `http://127.0.0.1:5173/#/admin/bid-policies`에 로그인해 버전 내림차순과 최신 배지를 확인한다.
2. 첫 하한 0을 바꾸거나 호가 단위 1,001을 넣으면 필드 오류가 보이고 POST가 발생하지 않는다.
3. 구간을 추가·삭제하고 유효한 새 버전을 등록한다. 새 버전과 이전 버전의 구간이 함께 보이며 이전 값은 유지된다.
4. USER로 전환하면 메뉴가 사라지고 직접 URL도 차단된다. 서버 권한은 아래 curl로 확인한다.

로그인 응답 Access Token을 `$ADMIN`, `$USER1`에 두고 실행한다. 아래 POST는 실제 새 버전을 추가한다. `valid-policy.json`은 로컬 증거 파일이며 다른 환경에서는 같은 형식의 JSON 파일을 만든다.

```powershell
$API = 'http://127.0.0.1:8080'
Set-Location C:\Users\jitin81\Documents\dev\lab\four-lane-auction
curl.exe -i "$API/api/admin/bid-policies" -H "Authorization: Bearer $ADMIN"
curl.exe -i -X POST "$API/api/admin/bid-policies" -H "Authorization: Bearer $ADMIN" -H 'Content-Type: application/json' --data-binary '@output/m1-05/valid-policy.json'
# 201, 이전 최대 버전 +1
curl.exe -i "$API/api/admin/bid-policies" -H "Authorization: Bearer $USER1"
# 403 FORBIDDEN
curl.exe -i "$API/api/admin/bid-policies"
# 401 UNAUTHENTICATED
```

### 핵심 이유·깨질 수 있는 상황·후속

- 고정 카운터 행을 잠가 두 등록이 같은 번호를 읽지 못하게 하고, 기존 version UNIQUE도 유지한다. 모든 등록 경로가 같은 카운터를 먼저 잠가야 한다. 우회 SQL·카운터 직접 변경·트랜잭션 분리는 보장을 깨뜨린다.
- 카운터·정책·구간을 같은 트랜잭션으로 묶어 반쪽 정책과 롤백 뒤 버전 소모를 막는다. policyId의 AUTO_INCREMENT 빈 번호와 version의 연속성은 별개다. DB 장애·락 타임아웃·버전 소진은 일반 500이며 정상 경합의 201 보장과 구분한다.
- 잠금 대기는 커넥션을 점유한다. 커밋 후 응답이 유실되면 등록 성공 여부를 모를 수 있다. 정책 등록에는 멱등 키가 없으므로 자동 재등록하지 않고 목록을 확인한다.
- Clock은 잠금 획득 후 한 번 읽는다. 시계 역행은 createdAt을 역행시킬 수 있으나 목록은 버전 순서다. 카운터는 시각을 사용하지 않는다. 레인 시계 점프 OQ-35·타이머 선택은 이번에 정하지 않았다.
- JS 안전 정수 상한은 정책 JSON의 정밀도 보존용이다. engine의 long 덧셈 overflow 검출을 실제 입찰 금액 끝단 정책으로 해석하지 않는다. 그 처리는 M2-06 후속이다.
- 후속: M2-02·M2-06의 AC-08-2와 실제 첫 입찰, 기존 M3-07 trade FK, 기존 다른 작업의 후속은 그대로 유지한다. CHALLENGES 후보를 선택하지 않았다.
- **NOT_RUN:** 실제 DB 커밋 ACK 유실·DB 네트워크 단절·전원 장애·락 타임아웃 장애 주입, 정상 DB의 빈 정책 화면, 운영 HTTPS/TLS·AWS·배포·부하/성능·경매 완주. beforeCommit·CHECK 주입·브라우저 원본 응답 지연을 이 증거로 바꾸어 쓰지 않는다.

## M1-04 출품 검수

- 작업일: 2026-10-28 (Asia/Seoul).
- 상태: **완료 (M1-04 로컬 수용 기준·아래 실행 증거 기준)**. BE → GEN → FE → 실제 화면 통합과 최종 빌드를 마쳤다. 원격 push·PR 생성은 별도 승인 대기이며 실행하지 않았다.
- 원본: TASKS 0장·M1-04·9장, PRD M-03·AC-03-1~3·A-01, API 7.1, ARCH 4.4·11·B-7, ADR-001~008과 M1-03 후속.
- 시작 SHA: `632585e6e82b4ee0334db50c78cc549aafdf83e2`. fetch 후 dev=origin/dev·clean, M1-03 PR #6 포함 확인 후 `feature/M1-04-item-review` 생성.
- 승인: 사용자 ①은 ADMIN 상세 최저 낙찰가 조회로 변경, 나머지 권장안·변경 파일·검증·로컬 커밋 계획 승인. push·PR 생성은 별도 승인이다.
- 결정 기록: [ADR-009](adr/ADR-009-M1-04-item-review.md), PRD OQ-54·55, ARCH B-7, API 0.6. 새 의존성·Flyway·EVENTS 변경 없음.
- 커밋: `7b4bfd7` 정책·계약, `83b84a8` BE, `c08d6b5` GEN, `fa5ab23` FE. 최종 기록은 별도 docs 커밋이다. 원격 push·PR 생성은 미실행이다.

### 승인한 구현과 경계

- Item PK `PESSIMISTIC_WRITE`로 승인·반려를 직렬화한다. application 트랜잭션에서 잠금 조회 → 검수 상태 확인 → Clock 1회 → 상태·사유·reviewed_at·updated_at 변경. 서비스 프록시가 커밋한 뒤 컨트롤러가 DTO를 반환한다. 조회는 readOnly다.
- ADMIN 상세 최상위 `reservePrice`만 제공한다. 운영자 목록·판매자·공개 응답에는 필드가 없다. 원문 1~500 코드 포인트의 반려 사유를 보존하고 공백만인 값은 거절한다.
- 실제 응답 카탈로그는 제품 REST handler 13개를 대조한다. ADMIN 상세 200의 정확한 최상위 필드만 실제 저장값과 일치하는지 검사하고, 나머지 필드·중첩·별칭·숫자/문자열 표식 누출은 계속 거절한다. ADMIN 전체를 검사에서 빼지 않는다.
- 운영자 목록은 `stage`·생성 시각·ID 키셋 커서다. 검수 대기는 오름차순, 다른 단계는 내림차순. 기존 Lot의 실제 최신 생성 시각·ID로 단계와 이력을 읽는다. `created_at >= reviewed_at` 경계와 동률을 검사했다. 편성 쓰기·재출품을 구현한 것은 아니다.
- FE는 생성된 클라이언트만 호출한다. ADMIN 메뉴·라우트 가드, 계정별 Query 키·계정 변경 시 조회 취소/캐시 제거, 명령 중 중복 제출 차단, 처리 후 목록·상세 재조회, 422 뒤 최신 상태 표시를 구현했다. 메뉴·가드는 UX이고 서버 403이 최종 방어선이다.
- REVIEW_RESULT는 M3-06, 감사 로그는 후속이다. 기존 판매자 목록의 편성 이후 계산 확장은 M1-07·M2 후속을 유지한다. 새 의존성·Flyway 없음, V1~V5·EVENTS 불변, CHALLENGES 미결 후보를 선택하지 않았다.

### 단계별 실행 증거

| 단계 | 실제 결과 | 로컬 증거 |
|---|---|---|
| BE 게이트 | backend `test` 178개 통과 후 보강한 조회 경계·계약·커서 테스트 10개 통과. 실패/오류/skip 0. bootRun curl 통과 후 GEN 진입 | `output/m1-04/be-all-tests.log`, `be-all-test-results/`, `be-final-focused.log` |
| 최종 BE | `clean build --console=plain --info` PASS, 14 actionable tasks 모두 실행. UTC 179개 + Seoul 179개 = 358회, 같은 179개를 두 JVM에서 실행. 각각 25클래스·실패/오류/skip 0. verifyTimeSources 89파일·위반 0 | `backend-clean-build.log`, `test-summary.json`, `test-results/test/`, `test-results/testSeoul/` |
| 검수 경합 | 승인/반려·승인/승인·반려/반려 각각 200라운드/JVM. 최종 clean build에서 총 1,200라운드·검수 명령 2,400건. ExecutorService + CountDownLatch, 실제 HTTP·MySQL. 매번 200 하나·422 하나, DB 상태·사유·시각과 승자 응답 일치 | ItemReviewConcurrencyTest의 매 라운드 assertion. 처리량·지연 측정 아님 |
| 트랜잭션·시간 | 커밋 전 예외로 상태·사유·시각 전부 롤백, 이후 승인 가능. 다른 트랜잭션이 락을 보유한 동안 Clock을 이동해 잠금 획득 뒤 시각이 저장됨을 확인 | ItemReviewFailureIntegrationTest |
| 입력·조회·인가 | 사유 500/501 이모지, 공백·누락, 원문 보존, 커서 단계 결합·동률 정렬, 과거 Lot 제외·실제 이력, USER 403·익명 401·DB 불변 | ItemReviewValidationTest, AdminItemControllerTest, AdminItemHttpIntegrationTest |
| 노출 계약 | ADMIN 상세의 저장된 표식 917431000원은 허용, 그 밖의 필드·값 누출과 상세의 중첩·별칭 누출 거절. 성공 승인·반려와 재검수·권한 오류 실제 응답 포함 | ReservePriceContractTest, ResponseContractCatalog, ReservePriceAssertions |
| curl | 실제 등록·ADMIN 상세·승인·반려·재검수 422/current status·USER 403·익명 401·사유 400·후보·판매자 목록·404·OpenAPI 등 21항목 PASS | `curl-checks.json`, 개별 JSON. 인증 응답은 `.local.json`으로 분리해 공개하지 않음 |
| GEN | 실제 `/v3/api-docs`에서 Orval 8.45.0 생성, typecheck PASS, 생성 코드 직접 수정 없음 | `gen-api.log`, `gen-typecheck.log`, 커밋 c08d6b5 |
| FE | 최종 lint·typecheck·build PASS. FE 진입 후 backend source tree는 83b84a8과 동일 | `final-lint.log`, `final-typecheck.log`, `final-frontend-build.log`, Git diff |
| 증거 검사 | 기존 CI JUnit 리포트 검증기 PASS. Git 추적·비무시 파일과 해당 작업의 비밀값 분리/가림 처리 로그 407파일에서 실제 검증용 비밀값 6개 일치 0 | `ci-report-verifier.log`, `credential-scan.json`. `.local` 자격 파일은 공개 검사 대상에서 제외 |

### 실제 화면 검증

실제 Chrome 두 독립 컨텍스트에서 bootRun + Vite를 사용했다. 검증 계정·출품물은 실제 API 입력이며 제품 하드코딩·합성 API 응답이 아니다. 아래 로그·PNG는 `output/playwright/m1-04/`에 있다.

| 시나리오 | 결과 | 증거 |
|---|---|---|
| USER 권한 | 검수 메뉴 0개, 직접 URL 차단, 관리자 요청 0건. ADMIN → 로그아웃 → USER 전환 뒤에도 상세·가격·메뉴 없음, 추가 관리자 요청 0건 | `ui-user-guard.log`, `ui-account-switch.log`, `user-forbidden.png` |
| ADMIN 로그인 | 익명 직접 진입 → 로그인 → 검수 화면 복귀, 메뉴 표시 | `ui-admin-login.log` |
| 등록 → 검수 → 판매자 | 화면에서 Item 31·32 등록 후 실제 승인·반려 POST 각각 200. 승인만 후보 목록 표시. 판매자 재조회에서 승인·반려와 원문 사유 확인, 최저가 비노출 | `ui-register.log`, `ui-admin-review.log`, `ui-seller-result.log`, `seller-result-desktop.png` |
| 반려 입력 | 빈 값·501 코드 포인트는 오류 표시, 반려 POST 0건. 유효 원문은 서버에 보존 | `ui-admin-review.log` |
| 페이지·조회 상태 | 실제 20건 다음 페이지 조회. 실제 응답에 Chrome 네트워크 지연 1500ms만 적용해 로딩 관찰. 연결 실패 주입으로 오류 → 후크 제거 후 실제 200·빈 목록 복구 | `ui-admin-states.log`, `list-loading.png`, `list-network-error.png`, `list-empty-recovered.png` |
| 다른 검수와 경합 | 실제 800×600 이미지로 Item 33 등록·상세 조회. 다른 운영자 curl 승인 200 뒤 기존 화면 반려는 422/currentStatus=APPROVED, 최신 상태 표시·폼 제거 | `ui-create-content.log`, `ui-race-open.log`, `ui-race-result.log`, `other-operator-approve.json`, `stale-review-422.png` |
| 화면·모바일 | 1440×1050, 390×844 PNG를 직접 열어 확인. 관리자·판매자 문서 폭=390, 가로 넘침 없음. 확인한 local/sessionStorage 키 0, 수집한 uncaught pageerror 0 | `admin-visible-image.png`, `admin-visible-image-mobile.png`, `seller-result-mobile.png` |

초기 익명 Refresh 401과 의도한 네트워크 실패는 브라우저 콘솔에 남는다. 모든 콘솔 항목이 0이라는 뜻은 아니다. 최초 두 출품은 1×1 투명 PNG로 기능을 검증했고, Item 33에서 별도 800×600 이미지의 실제 디코딩과 배치를 확인했다.

### 수용 기준별 판정

| 기준 | 결과 | 근거 |
|---|---|---|
| AC-03-1 | PASS | 승인 200·DB 상태·reviewedAt 저장, stage=APPROVED 후보 포함, 실제 운영자·판매자 화면 |
| AC-03-2 | PASS | 반려 사유 원문 저장·판매자 응답/화면 표시, 후보 목록 제외 |
| AC-03-3 | PASS | USER 승인·반려 403, 서비스 미호출 MVC와 실제 HTTP·DB 불변·curl |
| 이미 검수한 Item 재승인 422 | PASS | 승인/반려 뒤 두 명령 조합 모두 거절, error.reviewStatus·기존 시각/사유 보존 |
| 메뉴·직접 URL 차단 | PASS | USER 메뉴 없음·URL 차단·관리자 호출 0건, ADMIN 허용, 계정 전환 후 재확인 |
| 승인·반려 동시 요청 | PASS | 두 JVM 합계 1,200라운드에서 매번 성공 정확히 하나·422 하나, DB 상태 일치 |
| B-7·운영자 계약 검사 | PASS | ADMIN 상세만 reservePrice 제공, UI에서 가격 확인. 목록·판매자·공개 응답은 엄격 검사, 상세 예외도 최상위·저장값만 허용 |

### 재현 중 확인·수정

- 초기 Docker Desktop `run/dockerInference` 소켓 오류로 Testcontainers 초기화 실패. 사용자 재기동 뒤 Docker 28.5.1·MySQL 8.4.13으로 정상 실행했다. 설정·볼륨·데이터 초기화는 하지 않았다. 초기 실패는 PASS 증거에서 제외한다.
- 최초 MySQL 실행의 JDBC Map 시간 값은 LocalDateTime인데 테스트가 Timestamp 객체와 비교해 실패했다. Timestamp.class로 명시 조회한 Instant 값과 비교하도록 수정했고 전체 테스트를 다시 통과했다.
- 브라우저 offline 설정은 TanStack Query가 요청을 보류해 오류 상태 재현에 맞지 않았다. 연결 실패만 주입하고 즉시 후크를 해제한 뒤 실제 응답으로 복구했다. 잘못 중첩한 검증용 locator도 수정했다. 실패 시도 로그는 별도로 보존하며 제품 기능 실패나 최종 PASS로 섞지 않는다.
- 긴 목록은 화면 높이 안에서 스크롤하고, 모바일에서는 선택한 상세가 화면 밖이면 그 위치로 이동하도록 보완했다.

### 직접 확인하는 방법

Windows에서는 이 checkout의 bootRun을 종료한 뒤 빌드한다. 기본 Java 7 대신 Java 21을 명시한다.

```powershell
$env:JAVA_HOME = 'C:\Users\jitin81\.jdks\corretto-21.0.10'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
Set-Location C:\Users\jitin81\Documents\dev\lab\four-lane-auction\backend
.\gradlew.bat clean build
# 경합만 확인할 때 (UTC JVM)
.\gradlew.bat :app:test --tests 'com.fourlane.item.ItemReviewConcurrencyTest'
Set-Location ..\frontend
npm run lint
npm run typecheck
npm run build
```

실제 화면은 루트에서 `docker compose up -d mysql`, backend에서 JWT_SECRET_BASE64를 설정하고 `./gradlew.bat :app:bootRun`, 다른 터미널 frontend에서 `npm run dev`로 실행한다. ADMIN 생성은 [DEPLOY](DEPLOY.md)의 명시적 admin-seed 절차를 따른다. 비밀번호·키는 커밋하지 않는다.

1. 별도 브라우저 프로필의 USER에서 `http://127.0.0.1:5173/#/items/new`로 두 건 등록한다. 같은 프로필의 탭끼리는 쿠키를 공유하므로 ADMIN은 별도 프로필로 로그인한다.
2. ADMIN에서 `#/admin/items` → 물품 선택 → 이미지·정보·최저 낙찰가 확인 → 하나 승인, 하나 사유와 함께 반려한다.
3. 진행 단계의 승인됨·편성 대기에는 승인 건만 나온다. USER의 `#/me/items`를 다시 조회해 승인·반려와 사유를 확인한다.
4. USER에는 검수 메뉴가 없고 `#/admin/items`를 직접 입력해도 차단된다. 개발자 도구에서 관리자 조회가 발생하지 않는지 확인한다. 서버 권한은 아래 curl로 별도 확인한다.
5. 다른 운영자가 같은 Item을 먼저 처리하면 남은 검수는 422로 거절되고 최신 상태를 표시한다.

로그인 응답 토큰을 `$ADMIN`, `$USER1`에 두고, `$itemId`는 새로 등록한 ID로 바꿔 실행한다. 이미 검수한 로컬 증거 Item 31은 재검수 422를 확인할 때만 쓴다.

```powershell
$API = 'http://127.0.0.1:8080'
curl.exe -i "$API/api/admin/items?stage=PENDING_REVIEW" -H "Authorization: Bearer $ADMIN"
curl.exe -i "$API/api/admin/items/$itemId" -H "Authorization: Bearer $ADMIN"
curl.exe -i -X POST "$API/api/admin/items/$itemId/approve" -H "Authorization: Bearer $ADMIN"
# 최초 200, 반복 422 ITEM_NOT_PENDING_REVIEW + error.reviewStatus
curl.exe -i -X POST "$API/api/admin/items/$itemId/approve" -H "Authorization: Bearer $USER1"
# 403 FORBIDDEN
curl.exe -i "$API/api/me/items" -H "Authorization: Bearer $USER1"
```

### 핵심 이유·깨질 수 있는 상황·후속

- 상태를 읽고 판단하는 구간 전체를 PK 락으로 묶어 낡은 상태로 두 번 성공하지 못하게 한다. 선행 요청이 롤백하면 대기 요청이 성공할 수 있다. 모든 검수·미래 재출품 쓰기가 같은 규칙을 따라야 하며, 락 전에 읽은 낡은 엔티티를 쓰는 경로를 추가하면 보장이 깨질 수 있다.
- 잠금 대기는 DB 연결을 점유한다. DB 장애·락 타임아웃은 일반 500이며 정상 경합의 200/422 보장과 구분한다. 검수 트랜잭션 안에서 파일 I/O·외부 호출을 하지 않는다. 조건부 UPDATE도 DB 쓰기 잠금을 사용할 수 있어 무조건 대기가 없다고 설명하면 안 된다.
- 커밋 전 실패는 검수 변경 전부 롤백한다. 커밋 뒤 응답이 유실되면 재요청은 422일 수 있다. 검수 명령에 멱등 키·자동 재시도를 추가하지 않고 재조회로 결과를 확인한다.
- Clock은 잠금 획득 뒤 읽으며 DB/응답은 UTC 밀리초다. 시계가 역행하면 검수 시각도 역행할 수 있다. 페이지 간 고정 스냅샷도 보장하지 않는다. 레인 타이머·시계 점프 OQ-35는 이번에 정하지 않았다.
- 후속: M1-07 편성 쓰기·판매자 편성 이후 stage·중복 편성/OQ-28, M3-06 REVIEW_RESULT·알림함, M3-08 재출품, 감사 로그. 기존 M3-07 trade FK도 그대로다.
- **NOT_RUN:** 실제 DB 커밋 ACK 유실·전원 장애·DB 네트워크 단절·락 타임아웃 장애 주입, 운영 HTTPS/TLS·배포·AWS·부하/성능·경매 완주. beforeCommit 주입·로컬 HTTP·MySQL·브라우저 결과를 이 증거로 바꾸어 쓰지 않는다.


## M1-03 출품 등록과 내 출품 목록

- 작업일: 2026-10-28 (Asia/Seoul).
- 상태: **완료 (M1-03 로컬 수용 기준·아래 실행 증거 기준)**. 이 기록 뒤 Git push·dev 대상 PR을 생성하며 원격 링크·CI 결과는 PR 본문과 완료 보고에서 확인한다. PR 머지는 하지 않는다.
- 원본: TASKS 0장·M1-03·9장, PRD M-02·AC-02-1~7·U-01·U-02 일부·OQ-28, API 4.5·6.4·6.5·1.4, ARCH 4.4 V2·11장·B-6/7. M1-03의 직접 관련 CH는 없고 CH-05 입찰 후보를 새로 선택하지 않았다.
- 시작 SHA: `163546821e1c91e174b7abb868465a76e78a03e2`. `git fetch origin --prune` 후 dev=origin/dev, clean 확인. M1-01 PR #4 머지 `3e254b31e14ecc5c133cb2be4010755f0d0a88c8`의 ancestry 확인 뒤 `feature/M1-03-item-registration` 생성.
- 작업 위치: 기본 checkout `C:\Users\jitin81\Documents\dev\lab\four-lane-auction`. 별도 worktree 없음.
- 승인: 사용자 ①~⑩ 권장안 전체 승인. BE → GEN → FE → 통합, 실제 검증·기록·목적별 로컬 커밋에 더해 **Git push·PR 생성까지 명시 승인**했다. 이미지 push·태그 push·머지는 승인 범위가 아니다.
- 결정 기록: [ADR-008](adr/ADR-008-M1-03-item-registration.md), PRD OQ-52·53과 OQ-28 부분 결정, ARCH 11·부록 A/B. API 0.4 → 0.5를 승인 범위대로 먼저 반영했다. EVENTS 0.3은 그대로다.
- 의존성·스키마: 새 Java/npm 의존성과 Flyway 추가 없음. V1~V5 불변. Tika는 비교만 하고 도입하지 않았다.
- 커밋: `ec1b85b` 정책·계약, `c359af0` BE·관련 테스트·저장 설정, `6329482` GEN, `8b74e98` FE. 이 절·ADR 보완은 마지막 docs 커밋이다.

### 승인한 구현과 경계

- 이미지 1~10장, 장당 **10,000,000바이트**, 전체 multipart 요청 **101,000,000바이트**. JPEG·PNG·WebP 시그니처 직접 판별, EXIF 원본 유지. 시그니처는 전체 이미지 디코딩·완전한 손상 검출을 뜻하지 않는다.
- 이름 1~100 코드 포인트(공백만 거절), 설명 최대 2,000 코드 포인트. 두 가격은 1,000원 단위, 1,000원 이상 10억 원 이하, 최저 낙찰가 ≥ 시작가. 최저가 생략·빈 값은 시작가로 저장한다.
- 등록 뒤 수정·철회 API는 없다. 반려 Item은 남기고 새 Item으로 다시 신청한다. 판매자 본인 조회에도 최저 낙찰가 필드가 없다. 운영자 공개 여부는 M1-04 미결이다.
- `ImageStorage`는 Spring MultipartFile을 모른다. UUID v4의 소문자 32자리 hex 키로 CREATE_NEW 저장하며 사용자 파일명·Content-Type을 저장 판정에 쓰지 않는다.
- 파일을 모두 저장한 뒤 별도 application `@Transactional`에서 Item·이미지 행을 커밋한다. 확정 롤백·트랜잭션 시작 전 실패만 파일을 정리하고 COMMITTED/UNKNOWN은 보존한다. 컨트롤러는 커밋 후 201 DTO를 만든다.
- 이미지 GET은 커밋된 `item_image`를 먼저 확인한다. 저장 중·고아 파일은 키를 알아도 메타데이터가 없으면 404다. 검사한 Content-Type, nosniff, 365일 immutable 캐시를 보낸다.
- local bootRun의 작업 디렉터리를 backend로 명시해 `.local-data/images`를 쓴다. `ITEM_IMAGE_STORAGE_ROOT`로 재정의한다. Git·Docker build context에서 이 디렉터리를 제외했다. prod는 `/app/data/images`와 `item-images` 볼륨을 쓰며 uid/gid 10001이 쓴다.
- 기존 전역 예외 처리의 413 정규화가 실제 servlet 한도 경로에도 작동함을 확인했다. 예외 핸들러를 추가로 바꾸지 않았다. Tomcat max-swallow-size=-1은 거절 본문 소비를 허용해 JSON 수신을 돕지만 추가 네트워크 I/O 비용을 낸다.
- 내 출품 조회는 JWT sub로 회원을 한정하고 `(createdAt, id)` 내림차순 키셋 커서를 사용한다. 커서는 회원에 묶고 다른 회원의 커서를 400으로 거절한다. 이번 단계는 검수 대기·반려·승인을 표시한다. 편성 이후 계산·currentLot은 M1-07·M2 후속이다.
- FE는 생성된 함수만 호출한다. 회원별 Query 키와 로그아웃·회원 변경 시 취소/제거를 적용한다. 업로드 POST의 자동 재시도는 없고 계정 신원이 달라지면 재전송·늦은 화면 이동을 막는다. 로그인 복귀, 로딩·빈 상태·오류·다음 페이지를 처리한다.

### 단계별 실제 실행 증거

| 단계 | 실제 결과 | 로컬 증거 |
|---|---|---|
| BE 최종 | `./gradlew.bat clean build --console=plain --info` 성공, 14 actionable tasks 모두 실행. verifyTimeSources 79파일·위반 0 | `output/m1-03/backend-clean-build.log` |
| JUnit | UTC test 165회 + 서울 testSeoul 165회 = 330회. 같은 165개를 두 JVM에서 실행. 각각 20클래스, failures/errors/skipped 0 | `output/m1-03/test-summary.json`, `test-results/test/*.xml`, `test-results/testSeoul/*.xml` |
| 새 검사 | 새 테스트 46개. MVC, 실제 MySQL·HTTP, 파일 저장, 시그니처·가격·텍스트, 공용 노출 계약 | 위 XML. engine은 NO-SOURCE이며 엔진 기능 검증은 아님 |
| 실제 multipart | 10장×10,000,000바이트 수락. 파일 한도+1의 Content-Length·chunked 요청과 요청 전체 101MB 초과가 모두 413 JSON. 영구 파일·행 증가 0 | MultipartLimitHttpIntegrationTest 4개, 두 JVM에서 실행 |
| 병렬 등록 | 두 스레드를 CountDownLatch로 출발, 같은 원본 파일명의 등록 200라운드/JVM. JVM마다 400행·400파일, 키 충돌·소유자 혼합 0. 두 JVM 합계 400라운드·800등록 | ItemRegistrationConcurrencyTest. 처리량·지연 성능 측정은 아님 |
| 저장 실패 | 부분 스트림·두 번째 저장 실패 정리, 실제 MySQL fk_item_seller 위반 롤백·파일 삭제, 메타데이터 실패 주입, 커밋 직전 예외 롤백 | LocalImageStorageTest, ItemRegistrationFailureIntegrationTest |
| 커밋 뒤 실패 | 실제 MySQL 커밋 후 호출자 예외를 주입해 행·파일 유지 확인. UNKNOWN 완료 상태는 분기 정책 검사 | 같은 실패 통합 클래스. UNKNOWN의 실제 DB 네트워크 단절 재현은 NOT_RUN |
| curl | bootRun의 등록 201, 생략 최저가 DB 대조, 금액 오류, ADMIN/익명, 위장 JPG, 413, 회원 분리, 이미지 바이트·헤더 등 19항목 PASS | `output/m1-03/curl-checks.json`, 개별 응답 JSON·image-headers.txt |
| GEN | 실제 `/v3/api-docs`에서 Orval 8.45.0 재생성·typecheck 통과, 생성 코드 직접 수정 없음. 생성 함수로 실제 multipart 201·nullable 목록·비노출·200 Blob·404 JSON 오류 확인 | `gen-api.log`, `gen-typecheck.log`, `generated-client-check.json` |
| FE 최종 | lint·typecheck·build PASS. FE 진입 뒤 backend tree는 `c359af0`와 동일 | `final-lint.log`, `final-typecheck.log`, `final-frontend-build.log`, Git diff |
| 리포트·민감값 | 기존 CI 리포트 검증기 PASS. Git 추적·비무시 파일과 M1-03 앱/빌드/가림 처리한 브라우저 로그 235파일에서 실제 로컬 비밀값 5개 검색 일치 0 | `ci-report-verifier.log`, `credential-scan.json`. 무시되는 자격 파일은 검사 대상·공개 증거에서 제외 |
| 로컬 컨테이너 | Linux prod 설정으로 실제 가입·로그인·출품 후 app 컨테이너 재생성. UID 10001·이미지 디렉터리 쓰기, 새 컨테이너에서 같은 URL 200·원본 SHA-256 동일 | `docker-build.log`, `docker-proof.log`, `docker-persistence.json` |

컨테이너 검증 소스는 FE 커밋 `8b74e98ff2dd86b8de42daee3d8716fd13601b4b`, 로컬 이미지 태그는 `ghcr.io/songstone/four-lane-auction:sha-8b74e98ff2dd86b8de42daee3d8716fd13601b4b`다. app ID는 `aba093956be2…`에서 `c1c4dc25fc41…`로 달라졌고 원본·교체 전·교체 후 이미지 SHA-256은 모두 `2F27CFD21A3CACE4A37C3E6F86A0A4A3C11E6F4EF81E01B9F88993D64D8C9747`이었다. 임시 `fourlane-m103-proof` 컨테이너·네트워크만 down했고 볼륨·로컬 이미지는 유지했다. 기존 개발 MySQL은 유지했다. 레지스트리 이미지 push·AWS 배포·운영 TLS 증거가 아니다.

### 최저 낙찰가 공용 계약 검사

- 실제 제품 REST handler 9개와 카탈로그 등록 경로를 대조한다. 현재 인증·공개·본인·다른 회원·ADMIN·오류·이미지의 실제 응답 16사례를 실행한다. 제품 경로가 추가됐는데 카탈로그에 없으면 실패한다.
- 재귀 JSON 검사로 `reservePrice` 및 이름 표기 변형, 금액 표식의 숫자·문자열 노출을 검사한다. `reserveNotMet`은 허용한다. 표식 917431000원을 실제 DB에 저장했음을 먼저 확인한다. 시작가와 같은 기본 최저가 사례는 별도 DB 검증과 필드 검사로 구분한다.
- 이미지 성공은 바이트·헤더로 분리하고 오류 JSON은 검사한다. 요청 DTO에는 reservePrice가 필요하므로 요청 스키마를 응답으로 오인해 검사하지 않는다.
- M1-04·07·08, M2 이후의 응답·실제 수신 이벤트 등록 방법은 ADR-008 후속 절에 있다. 현재 제품 이벤트 발행자가 없으므로 스냅샷·브로드캐스트의 전체 AC-02-6 완료를 주장하지 않는다.

### 실제 화면 검증

실제 Chrome·bootRun·Vite를 사용했다. 아래 증거는 `output/playwright/m1-03/`에 있다.

| 시나리오 | 결과와 범위 | 증거 |
|---|---|---|
| 익명 진입·로그인 복귀 | USER 전용 URL → 로그인 → 원래 출품 등록 화면 | ui-login-return.log |
| 등록 완주 | 파일 선택 순서·삭제·대표 이미지, 실제 POST 201 → 내 목록 검수 대기, 업로드 이미지 800×600 디코딩 | ui-registration.log, registration-filled.png, my-items-desktop.png |
| 입력·이미지 사전 검사 | 이미지 필수, 시작가/최저가 단위·역전, 위장 JPG·10MB 초과·11장 선택을 차단. 잘못된 폼의 등록 POST 0건 | ui-registration.log, ui-other-user.log |
| 목록 페이지 | 실제 API로 만든 데이터 26건을 20+6건으로 표시, 중복 이름·다음 페이지 잔류 0 | ui-list-states.log |
| 검수 표시 | 실제 DB fixture의 APPROVED·REJECTED·반려 사유를 API로 조회해 표시 | review-stages-mobile.png. 검수 승인·반려 API 완주는 M1-04 |
| 로딩·오류 복구 | 실제 backend 200의 전달만 1.5초 늦춰 로딩 관찰, 네트워크 연결 실패 주입 → 오류 → 실제 200 복구 | ui-list-states.log, list-loading.png, list-network-error.png. 응답 본문·상태 합성 없음 |
| 회원 변경 | 다른 USER 로그인 시 기존 회원 목록 없음·빈 상태, 새 등록은 그 회원의 목록 1건. 최저가 생략 저장을 실제 DB 1000=1000으로 대조 | ui-other-user.log, empty-other-user.png, other-user-item.png |
| ADMIN | 출품 메뉴 없음, 등록·내 목록 직접 URL 모두 차단, USER 목록 쿼리 실행 0건 | ui-admin.log, admin-forbidden.png. 서버 403은 curl·MVC에서 별도 검증 |
| 업로드 중 로그아웃 | Chrome 네트워크 업로드 속도만 2000B/s로 제한. 원본 multipart 그대로 실제 201이 늦게 도착해도 익명 홈 유지. 재로그인 후 실제 커밋 Item 조회 | ui-logout-during-upload.log, logout-during-upload.png. 서버 응답·시간 합성 없음 |
| 모바일·저장소 | 390×844에서 폼·목록 캡처를 열어 배치·줄바꿈 확인, 가로 넘침 없음. 확인한 local/sessionStorage 키 0, uncaught JS 예외 0 | registration-mobile.png, my-items-mobile.png, ui-other-user.log |

예상한 초기 Refresh 401, 의도한 네트워크 실패와 검증 도구의 실패 실행은 콘솔에 남는다. 모든 콘솔 항목이 0이었다는 뜻은 아니다. 검증용 이미지·계정·검수 상태 fixture는 실제 저장·조회 입력이며 제품 코드의 하드코딩이나 가짜 API 응답이 아니다.

### 수용 기준별 판정

| 기준 | 결과 | 근거 |
|---|---|---|
| AC-02-1 | PASS | 실제 multipart·DB·이미지 순서·검수 대기 생성, curl 201와 U-01 → U-02 |
| AC-02-2 | PASS | 이미지 누락 400 REQUIRED, 행·영구 파일 증가 없음, 폼 사전 차단 |
| AC-02-3 | PASS | 시작가·최저가 각각 12345 등 거절, NOT_MULTIPLE_OF_1000 및 폼 메시지 |
| AC-02-4 | PASS | 최저가 < 시작가에 LESS_THAN_START_PRICE, 실제 curl·폼 |
| AC-02-5 | PASS | 생략·빈 값의 실제 MySQL 저장값 = 시작가. 브라우저 생략 등록도 1000=1000 |
| AC-02-6 이번 범위 | PASS | 실제 응답 카탈로그의 필드·값 검사, 다른 회원 분리, 생성 클라이언트·브라우저 실제 목록 확인. 미래 스냅샷·이벤트는 후속 |
| AC-02-7 | PASS | ADMIN POST 403, 익명 401, ADMIN 메뉴·직접 URL 차단 |
| 위장 JPG / 상한 초과 | PASS | 시그니처 400 UNSUPPORTED_IMAGE_TYPE, servlet 파일·요청 한도 413 IMAGE_TOO_LARGE JSON |
| 이미지 nosniff | PASS | 실제 바이트 일치·검사한 Content-Type·nosniff·immutable 캐시, 없는 파일/메타데이터 404 JSON |

### 재현 중 수정한 문제

- MVC slice의 JWT 발급 빈 누락과 null matcher 사용을 고쳤다. 커밋 직전 실패 주입은 추상 Repository의 real-method 호출 대신 실제 application 트랜잭션 안의 beforeCommit으로 옮겼다. 실제 MySQL FK 실패도 별도로 추가했다.
- 기존 SchemaAndTimeIntegrationTest의 명시적 EntityScan에 Item 패키지를 포함했다. 기존 테스트·검증 조건은 유지했다.
- OpenAPI 3.1에서 nullable $ref가 잘못 표현되는 것을 실제 JSON으로 확인해 `anyOf [CurrentLot, null]`로 보완하고 계약 검사·생성 타입을 확인했다. Orval의 이미지 Blob 오류 처리는 getImage의 지원 옵션으로 JSON error.info·status를 읽게 했다. 다른 생성 API의 응답 방식은 유지했다.
- 초기 로그 경로 오류로 남은 중복 테스트 실행이 리포트와 충돌해 해당 checkout의 실행만 종료했다. 수정 전 실패·중단 실행은 PASS에 포함하지 않고, 최종 단일 clean build의 XML을 새로 보관했다.
- 검수 화면 fixture를 넣는 CLI의 문자셋 오류는 UTF-8 hex literal로 바로잡고 실제 API를 재조회했다. 생산 API로 입력한 한글 데이터는 정상이다.
- 무기한 응답 보류 방식의 검증 후크를 없애고 전용 브라우저 컨텍스트를 다시 열었다. multipart를 도구가 재전송한 실험은 원본 파일을 보존하지 못해 폐기했다. 최종 로그아웃 경합은 원본 브라우저 요청에 Chrome 네트워크 제한만 적용해 다시 통과했다. 로딩은 유한 지연 후 원본 200 전달·후크 정리를 확인했다.

### 직접 확인하는 방법

Windows에서는 이 checkout의 bootRun을 먼저 종료하고 빌드한다. 기본 Java가 7인 환경이므로 Java 21을 명시한다.

```powershell
$env:JAVA_HOME = 'C:\Users\jitin81\.jdks\corretto-21.0.10'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
Set-Location C:\Users\jitin81\Documents\dev\lab\four-lane-auction\backend
.\gradlew.bat clean build
# 특정 검사만 재현할 때
.\gradlew.bat :app:test --tests 'com.fourlane.item.MultipartLimitHttpIntegrationTest'
.\gradlew.bat :app:test --tests 'com.fourlane.global.contract.ReservePriceContractTest'
Set-Location ..\frontend
npm run lint
npm run typecheck
npm run build
```

별도 터미널에서 실행한다. 정상 local에는 admin-seed를 켤 필요가 없다. ADMIN 확인이 필요하면 DEPLOY의 명시적 시드 절차를 따른다.

```powershell
Set-Location C:\Users\jitin81\Documents\dev\lab\four-lane-auction
docker compose up -d mysql
$env:JAVA_HOME = 'C:\Users\jitin81\.jdks\corretto-21.0.10'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
$env:JWT_SECRET_BASE64 = [Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes(48))
Set-Location backend
.\gradlew.bat :app:bootRun
# 다른 터미널: 저장소의 frontend에서 npm run dev
```

1. `http://127.0.0.1:5173/#/items/new`로 들어가 본인의 USER로 로그인한다. 등록 화면으로 복귀하는지 확인한다.
2. 정상 JPEG/PNG/WebP를 고르고 이름·시작가 50000을 입력한다. 최저가를 비워 등록하면 내 목록에 검수 대기·대표 이미지·시작가가 보인다.
3. 이미지 없음, 시작가/최저가 12345, 최저가 49000, 11장·10MB 초과·텍스트 JPG를 각각 시도해 오류와 등록 차단을 확인한다.
4. 다른 USER로 로그인하면 본인 목록만 보인다. ADMIN에는 메뉴가 없고 직접 URL도 막힌다. 승인·반려를 만드는 실제 운영 기능은 M1-04다.
5. 네트워크 탭에서 POST 201·GET 목록·이미지 200을 확인한다. 목록 JSON에 reservePrice가 없고 이미지 응답에 nosniff가 있는지 확인한다.

curl은 로그인 응답의 Access Token을 `$USER1`에 두고 실행한다. 이미지 경로는 본인 파일로 바꾼다.

```powershell
$API = 'http://127.0.0.1:8080'
curl.exe -i -X POST "$API/api/items" -H "Authorization: Bearer $USER1" `
  -F 'name=필름카메라' -F 'startPrice=50000' -F 'images=@C:\path\camera.jpg'
# 201. startPrice=12345 → 400 NOT_MULTIPLE_OF_1000
# reservePrice=49000 추가 → 400 LESS_THAN_START_PRICE
# images를 빼면 400 REQUIRED, ADMIN 토큰은 403
curl.exe -i "$API/api/me/items?size=20" -H "Authorization: Bearer $USER1"
# 응답 imageUrl을 그대로 GET: 이미지 바이트, Content-Type, nosniff 확인
curl.exe -i "$API/api/images/aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
# 404 application/json, error.code=IMAGE_NOT_FOUND
```

### 핵심 이유·깨질 수 있는 상황·후속

- 파일 I/O를 먼저 끝내 DB 연결 점유를 줄이고, DB 커밋 전 모든 파일을 확보한다. 파일과 DB는 원자적이지 않아 앱 강제 종료·정리 실패 때 고아 파일이 남는다. UNKNOWN에서 파일을 지우면 실제 커밋 행의 이미지가 깨지므로 보존한다. 자동 정리는 없으며 앱 정지 후 DB와 대조한다(DEPLOY).
- 커밋 뒤 응답이 유실되거나 로그아웃과 겹쳐도 등록 자체는 남을 수 있다. POST 멱등 계약은 없으므로 자동 재시도하지 않는다. 임의 재전송은 중복 Item을 만들 수 있다. 외부 파일 삭제·볼륨 손실·전원 장애의 원자성은 보장하지 않는다.
- Clock 역행 시 나중 등록한 물품이 앞선 시각으로 정렬될 수 있다. 커서는 동률의 id 순서를 정하며 페이지 사이 고정 스냅샷을 보장하지 않는다. 신규 락·경매 타이머·입찰 설계는 없다.
- 후속: M1-04 검수 API·운영자 B-7·공용 계약 등록, M1-07 편성 이후 stage·중복 편성/OQ-28, M1-08 회차/Lot 공개 응답, M2 스냅샷·실제 이벤트 등록, M3-08 재출품. 기존 M3-07 trade FK 후속도 그대로다.
- **NOT_RUN:** 실제 DB 커밋 ACK 유실·전원 장애·파일시스템 손실·고아 파일 자동 복구, 운영 HTTPS/Secure 쿠키·외부 배포·AWS·이미지 레지스트리 push/pull·부하/성능, 미구현 검수/편성/입찰/스냅샷/이벤트 완주. 로컬 HTTP·DB·브라우저·Linux 컨테이너의 증거를 이들과 바꾸어 쓰지 않는다.

## M1-02 STOMP 연결 인증

- 작업일: 2026-10-27 (Asia/Seoul).
- 상태: **완료 (M1-02 범위, 아래 수용 기준과 실제 실행 증거 기준)**. 원격 push·PR·CI는 실행하지 않았다.
- 원본: TASKS 0장·M1-02·9장, PRD 2.2·M-11·AC-11-1/6·OQ-39·9.4/9.6, API 1.3·3.3/3.4, EVENTS 1장, ARCH 8장·R10, CH-08. TASKS 9장에 이 작업이 새로 정할 미결 OQ·B는 없고 OQ-39는 이미 결정되어 있다.
- 시작 SHA: `3e254b31e14ecc5c133cb2be4010755f0d0a88c8`. fetch 후 dev=origin/dev, clean 확인. 선행 M1-01 PR #4의 머지와 ancestry 확인 후 `feature/M1-02-stomp-auth` 생성. develop 브랜치는 없으며 로컬 dev의 upstream은 origin/dev다.
- 작업 위치: 기본 checkout `C:\Users\jitin81\Documents\dev\lab\four-lane-auction`, 별도 worktree 없음.
- 승인: 사용자가 **권장안 A로 구현·검증·기록·로컬 커밋까지** 명시 승인했다. REST의 Principal 변환기를 공용 빈으로 추출해 STOMP와 공유한다. 별도 생성 방식은 역할 변환 중복 때문에 선택하지 않았다. push·PR 생성은 별도 승인이다.
- 결정 기록: [ADR-007](adr/ADR-007-M1-02-stomp-auth.md). ADR-003·ADR-006의 M1-02 후속 항목을 처리 완료로 표시했다. 기존 OQ-39·TASKS 0.2·ADR을 따랐으며 새 제품 규칙과 CHALLENGES 미결 후보는 선택하지 않았다.
- 계약·의존성·스키마: API 0.4, EVENTS 0.3 그대로. ERROR 값은 구현 전에 EVENTS 1.3과 ARCH 8.2의 TOKEN_EXPIRED·TOKEN_INVALID가 같음을 확인했다. 새 의존성·버전 변경·Flyway 추가가 없고 V1~V5는 변경하지 않았다. REST 변경이 없어 GEN 단계는 없다.
- 로컬 커밋: `652addd` BE, `208f95a` FE. 이 기록과 ADR·후속 표시는 마지막 docs 커밋으로 묶는다.

### 승인한 구현과 경계

- REST와 CONNECT가 같은 JwtDecoder·Clock·JwtAuthenticationConverter를 쓴다. Principal 이름은 회원 ID, 역할은 ROLE_USER·ROLE_ADMIN이다. Security 7.1의 FACTOR_BEARER 권한도 두 경로에 동일하게 적용된다.
- Authorization 없는 CONNECT는 익명, 빈 값·잘못된 Bearer·중복 헤더·서명/claim 오류는 TOKEN_INVALID, 검증된 만료는 TOKEN_EXPIRED다. ERROR 뒤 연결을 종료한다. Authorization은 디코딩 전에 브로커 헤더에서 제거하고 Decoder 예외 원문은 거절 예외에 넣지 않는다.
- HTTP Upgrade에서 신원을 받지 않도록 `/ws`만 HTTP Bearer 추출을 생략한다. 다른 공개 REST의 잘못된 토큰은 여전히 401이다. 숫자 레인 토픽은 공개, 개인 큐는 인증 필요, 직접 큐·허용 목록 밖·SEND 거절은 유지한다. 서버는 ADMIN의 개인 큐 구독도 허용한다.
- 연결 중 만료는 재검증·종료하지 않는다. 새 CONNECT와 REST는 같은 서버 Clock으로 검증한다. 토큰이 갱신되어도 같은 회원의 기존 연결은 유지하며 로그인·로그아웃은 실제 소켓 종료 후 새 신원으로 연결한다.
- 프론트는 accessTokenExpiresAt을 메모리에 보관하고 JWT 본문을 해석하지 않는다. beforeConnect에서 최신 토큰과 만료 60초 전 여부를 확인하고 기존 재발급 Promise·Web Locks 경로를 사용한다. 거절된 같은 토큰의 재연결을 막고 복구 실패 시 익명으로 전환한다. 늦은 응답은 이전 신원을 복원하지 못한다.
- USER는 앱에서 연결과 개인 큐 구독을 유지한다. 홈의 익명·ADMIN 연결은 기존 진단 예외이며 관전/제어 화면 기준의 수명 정리는 M1-08이다. 알림 생성·저장·표시는 M3-06이다. 테스트 메시지는 src/test에서만 발행한다.

### 단계별 실제 실행 증거

| 단계 | 결과와 실제 실행 | 증거 |
|---|---|---|
| BE 최종 | Corretto 21.0.10에서 `./gradlew.bat clean build` 성공. 14 actionable tasks 모두 실행, verifyTimeSources 운영 55파일·위반 0 | `output/m1-02/backend-clean-build.log` |
| JUnit | UTC test 119회 + 별도 JVM 서울 testSeoul 119회 = 총 238회. 같은 119개를 두 JVM에서 실행했고 각 failures/errors/skipped 0 | `output/m1-02/test-summary.md`, `output/m1-02/test-results/test/*.xml`, `output/m1-02/test-results/testSeoul/*.xml` |
| 실제 STOMP | JVM마다 신규 실제 WebSocketStompClient 12회, 인터셉터 단위 17회. 유효 USER/ADMIN, 익명, 만료 정각, 위조·형식 오류, ERROR 값·종료, 실제 메시지 수신 확인 | StompAuthenticationIntegrationTest, StompAuthChannelInterceptorTest |
| 동시 전달 | 두 회원을 동시에 연결한 뒤 두 작업 스레드가 CountDownLatch로 출발하는 개인 메시지 전송을 200라운드 반복. 각 회원이 본인 메시지만 수신. JVM마다 수행해 합계 400라운드·800메시지 | concurrentMembersReceiveOnlyOwnMessages. 연결은 JVM마다 두 개이며 연결 200회를 뜻하지 않음 |
| 시간 | 2020년 MutableClock으로 만료 1ms 전 허용·정각 거절. 연결 후 16분 이동해 기존 개인 큐 메시지와 새 레인 구독 수신, 같은 토큰의 새 CONNECT 거절 | expiredConnectReturnsTokenExpiredAtExactBoundary, connectedSubscriptionSurvivesTokenExpiry. 실제 시간 대기 없음 |
| curl | 최신 BE bootRun에서 공개 조회 200·필수 값 누락 400·가입 201·로그인/me 200·공개 API의 잘못된 Bearer 401·health UP·Upgrade 101 | `output/m1-02/curl-checks.json`. Upgrade만으로 STOMP 성공을 판정하지 않음 |
| FE 게이트·최종 | lint, typecheck, build 모두 성공. 수정 후 최종 명령을 다시 실행 | `output/m1-02/final-lint.log`, `final-typecheck.log`, `final-frontend-build.log` |
| 범위 보존 | FE 진입 후 backend diff 0. API·EVENTS·의존성 파일·Flyway가 시작 버전과 동일 | Git diff 확인. GEN 생략 |
| 민감값 | 실제 로컬 비밀번호·JWT 키·curl Access·Refresh 값 4개를 추적 텍스트와 M1-02 명령/앱 로그에서 검색해 일치 0 | `output/m1-02/credential-scan.json`. 기본 실행 로그와 검색한 값의 범위 |

기존 CI 리포트 검증기도 PASS다. 표의 MySQL executed 35는 검증기가 원래 필수로 지정한 Schema/Session 두 클래스의 수다. 신규 STOMP 통합 클래스도 실제 MySQL Testcontainers 기반의 전체 앱을 띄우지만 JWT 신원 검증 자체는 DB 조회를 하지 않는다. engine은 도메인 코드가 없어 test NO-SOURCE이며 엔진 기능 검증을 뜻하지 않는다.

### 통합 화면 검증과 재현 중 수정

실제 headed Chrome, bootRun, Vite로 실행했다. 프레임 관찰은 명령·Authorization 유무·목적지·오류 코드만 기록했고 토큰 문자열은 증거 JSON에 넣지 않았다.

| 시나리오 | 판정과 확인 | 로컬 증거 |
|---|---|---|
| 익명 → 로그인 → 로그아웃 | PASS. 익명 CONNECT에 헤더 없음, 로그인 CONNECT에 Bearer와 user-name, 개인 큐 SUBSCRIBE. 배지는 인증 연결·익명 연결로 전환 | `output/playwright/m1-02/basic.json` |
| USER 화면 이동·정상 재발급 | PASS. 내 정보 이동에도 소켓 유지, 실제 Refresh 200과 토큰 변경 후에도 소켓 수 불변 | basic.json |
| 반복 신원 전환 | PASS. 로그인·로그아웃 10회, 소켓 최대 1개·uncaught JS 예외 0 | stress.json |
| 실제 인증 거절 후 복구 | PASS. 로컬 JWT 키를 바꿔 서버 재시작 → 구 토큰 CONNECT에 실제 TOKEN_INVALID → Refresh 200 한 번 → 새 인증 연결·개인 큐 재구독 | key-recovery.json |
| 재발급 실패 | PASS. 별도 실제 logout 요청으로 Refresh를 폐기하고 키 변경 재시작 → TOKEN_INVALID → Refresh 401 두 번(API 1.3 재시도 포함) → 비로그인, 홈에서 헤더 없는 CONNECT | failure-prepare.json, failure-recovery.json |
| 연결 전 사전 재발급 | PASS. 브라우저의 추정 시각만 16분 앞당겨 실제 소켓을 닫으면 새 CONNECT 전에 실제 Refresh 200. 서버 Clock은 그대로다. 서버 만료 판단은 위 MutableClock 테스트로 검증 | proactive-refresh.json |
| 재발급 중 로그아웃 | PASS. beforeConnect의 실제 Refresh 200 응답 전달만 보류 → UI 로그아웃 → 익명 CONNECT → 원래 응답을 그대로 전달해도 익명 유지·logout 204·Refresh 쿠키 없음 | logout-during-refresh.json |
| 화면·복구·저장소 | PASS. 데스크톱과 390×844 캡처를 열어 확인. 모바일 문서 폭 390, 로그인 새로고침 복구, 로그아웃 후 익명, local/sessionStorage 사용 0 | mobile-before.json, mobile-after.json, authenticated.png, anonymous.png, mobile-authenticated.png, mobile-anonymous.png |

키 변경 복구 검증은 개인 화면에서 진행해 회차 목록의 30초 재조회가 먼저 토큰을 갱신하지 않게 했다. 서버 코드·응답 상태·본문을 합성하지 않았다. 응답 보류 테스트는 실제 backend 200의 전달만 지연한 것이다. 정상 초기 복구의 401과 의도한 서버 재시작 중 네트워크 오류는 브라우저 네트워크 콘솔에 남으며, uncaught JavaScript 예외 0과 구분한다.

- 첫 컴파일에서 StompHeaders에 없는 getCommand 사용을 고쳤다. 역할 테스트는 Security 7.1의 FACTOR_BEARER와 순서 없는 권한 집합을 정확히 검사하도록 수정했다.
- SessionSubscribeEvent 수신만으로 실제 브로커 등록 완료를 보장하지 못해 수신 테스트가 한 번 실패했다. 테스트 전용 ExecutorChannelInterceptor가 실제 구독 처리 후 완료 신호를 주도록 고쳤다. 테스트 삭제·skip·제품용 테스트 토픽 추가는 없다.
- HTTP Principal 상속은 실제 회귀 테스트에서 발견했다. 핸드셰이크에서 null 반환만으로는 native session의 Principal 복원이 남아, `/ws` HTTP Bearer 추출을 생략하는 최종 구현으로 고쳤다.
- Windows에서 실행 중인 bootRun이 engine JAR를 잡아 최초 clean이 실패했다. 이 checkout의 앱만 종료한 뒤 최종 clean build를 통과시켰다. 다른 checkout을 변경하거나 MySQL 볼륨을 삭제하지 않았다.
- 최초 화면 검사에서 강제 deactivate가 실제 close 전에 완료되어 소켓 최대 수가 2였다. 정상 deactivate의 close 완료를 기다리도록 FE에서 수정하고 반복·복구·경합 검증을 다시 통과했다. 실패한 실행을 최종 PASS로 세지 않았다.

### 수용 기준별 판정

| 기준 | 결과 | 근거 |
|---|---|---|
| AC-11-1 익명 레인 허용·개인 큐 거절 | PASS | 실제 STOMP 레인 메시지 수신, 개인 큐의 SUBSCRIPTION_DENIED·종료. 기존 직접 큐·SEND 거절 테스트도 통과 |
| AC-11-6 만료 CONNECT 거절·무토큰 익명 | PASS | 서버 Clock 만료 정각의 TOKEN_EXPIRED·종료, 무토큰 CONNECTED. 위조·형식 오류는 TOKEN_INVALID |
| AC-11-6 연결 중 만료 유지 | PASS | Clock 16분 이동 후 같은 연결·기존 개인 큐 메시지 수신, 새 레인 구독 수신. 새 만료 CONNECT는 거절 |
| 로그아웃 시 토큰 없는 재연결 | PASS | 실제 UI 로그아웃 후 익명 배지·Authorization 없는 새 CONNECT, 반복 10회 및 늦은 Refresh 응답 경합 |
| ADR-003 임시 제한 해소 | PASS | 유효 USER/ADMIN CONNECT 허용·회원 ID와 역할·인증 개인 큐 검증. ADR-003/006 후속과 ARCH 부록 A 갱신 |

### 직접 확인하는 방법

이미 실행 중인 이 checkout의 bootRun이 있으면 그 터미널에서 먼저 Ctrl+C로 종료한다. Windows에서는 JAR 잠금 때문에 clean이 실패할 수 있다. 현재 환경의 기본 java는 7이므로 Java 21을 명시한다.

```powershell
# 저장소 루트의 PowerShell 7
$env:JAVA_HOME = 'C:\Users\jitin81\.jdks\corretto-21.0.10'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
Set-Location backend
.\gradlew.bat clean build
# 만료·구독만 따로 재현할 때
.\gradlew.bat :app:test --tests 'com.fourlane.realtime.StompAuthenticationIntegrationTest'
```

별도 실행으로 화면을 확인한다. JWT 키는 이 로컬 프로세스에만 넣고 출력·커밋하지 않는다.

```powershell
# 저장소 루트
docker compose up -d mysql
$env:JAVA_HOME = 'C:\Users\jitin81\.jdks\corretto-21.0.10'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
$env:JWT_SECRET_BASE64 = [Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes(48))
Set-Location backend
.\gradlew.bat :app:bootRun
# 다른 터미널: 저장소 루트에서
# Set-Location frontend
# npm run dev
```

1. `http://127.0.0.1:5173`에서 WebSocket 익명 연결을 확인한다.
2. 기존 가입 화면에서 USER를 만들고 로그인한다. 인증 연결 배지와 개발자 도구 Network → `/ws` → Messages의 Authorization 포함 CONNECT·개인 큐 SUBSCRIBE를 확인한다.
3. 내 정보로 이동해도 같은 연결이 유지되는지 보고 홈으로 돌아온다.
4. 로그아웃하면 익명 연결 배지와 새 CONNECT의 Authorization 부재를 확인한다.
5. 만료는 위 테스트 명령으로 Clock을 이동해 재현한다. 실제 만료 시간을 기다리지 않는다.
6. smoke curl: `curl.exe 'http://127.0.0.1:8080/api/sessions?status=SCHEDULED'` → 200, `curl.exe -i 'http://127.0.0.1:8080/api/sessions'` → 400, `curl.exe 'http://127.0.0.1:8081/actuator/health'` → UP.
7. 프론트 검사는 `Set-Location frontend` 뒤 `npm run lint`, `npm run typecheck`, `npm run build` 순서다.

### 핵심 이유·깨질 수 있는 상황·후속

- 공용 Decoder와 변환기로 REST·STOMP의 서명·만료·회원 ID·역할 해석을 맞췄다. 연결 중 만료를 유지하므로 연결을 통한 즉시 토큰 폐기 장치는 아니다. 다른 탭·탈취 Access의 즉시 무효화 정책을 추가하지 않았다.
- 현재 시각의 앞으로 점프는 새 CONNECT를 일찍 만료시키고 역행은 유효 시간을 늘릴 수 있다. 기존 연결에는 만료 타이머가 없으며 개인 정보는 본인 큐에만 보낸다. 레인 OQ-35는 미결 그대로다.
- 소켓 종료와 재발급이 겹칠 때 종료 대기·세대 확인이 빠지면 중복 연결이나 과거 신원 복원이 가능하다. 실제 소켓 최대 수와 늦은 응답을 관찰해 검증했다.
- 새 트랜잭션·락·경매 타이머는 없다. Refresh 회전의 트랜잭션·잠금·커밋 실패 한계는 ADR-006을 따른다.
- 후속: M1-08의 익명/ADMIN 관전 화면 연결 수명, M3-06의 알림 생성·알림함·UI, M2/M3의 레인 동기화·재연결 복구. CH-08 미결 후보와 다른 제품 OQ·B는 정하지 않았다.
- 증거 범위: 로컬 단위·실제 MySQL 앱·HTTP·STOMP TCP·실제 브라우저다. 원격 push·PR·CI, 운영 HTTPS·배포, 부하·성능·대규모 재연결·경매 복구와 실제 알림 기능은 **NOT_RUN**이다. 본 절의 PASS는 M1-02 수용 기준 범위다.

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
- M1-01 당시 익명 STOMP 진단과 토큰 CONNECT 임시 거절은 그대로였다. **후속 M1-02 처리 완료**: 인증 CONNECT·개인 큐·신원 전환은 위 M1-02 절과 ADR-007에 기록했다. 관전 진입에 맞춘 연결 수명 정리는 M1-08이다.

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
- **M1-02 처리 완료**: 공용 Decoder로 CONNECT 인증, 임시 TOKEN_INVALID 거절 제거, Principal·개인 큐·연결 신원 전환(위 M1-02 절·ADR-007). M1-08: 익명 진단 연결 수명 정리. M3-07: trade FK와 경고 쓰기·거래 구현. 패널티 OQ-26은 미결 그대로다.
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

- **M1-02 처리 완료 (2026-10-27)**: M0-03 당시의 Authorization 포함 CONNECT 일괄 거절을 제거했다. 공용 Decoder로 유효/만료/위조를 구분하고 Principal·인증 개인 큐·프론트 신원 전환을 구현·검증했다(위 M1-02 절·ADR-007). 아래 M0-03의 실행 증거는 당시 범위 그대로다.
- **M1-08**: 홈의 익명 STOMP 연결은 M0 진단용 예외다. EVENTS 1.1의 비로그인 연결 시점(관전 화면)에 맞춰 연결 수명을 정리한다.
- **M1-06에서 매핑 확장**: M0-03 당시 조회용 부분 매핑·@Immutable이었던 회차·레인·Lot을 쓰기용으로 확장했다. 회차·READY 레인 생성과 회차 설정 수정은 M1-06, Lot 편성·경매 상태 전이는 후속 작업이다. M0-03 당시 증거의 범위는 그대로다.

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

API.md·EVENTS.md·TASKS.md 원문은 수정하지 않았다. 진단 API와 테스트 토픽은 추가하지 않았다. M0-03 당시 승인한 임시 제한 중 CONNECT 인증은 후속 M1-02에서 해소했고 홈의 익명 연결 수명은 M1-08에 남아 있다(위 후속 작업·ADR-003).

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
