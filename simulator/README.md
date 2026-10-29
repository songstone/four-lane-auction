# 시연 데이터 시드 (M1-10)

기존 REST API로 판매자·입찰자, 이미지 출품, 검수 결과, 예정 회차와 편성을 만든다. 스크립트는 SQL이나 ADMIN 승격을 사용하지 않는다. 결정은 [ADR-015](../docs/adr/ADR-015-M1-10-demo-seed.md), 실제 실행 증거는 [PROGRESS](../docs/PROGRESS.md)에서 확인한다.

## 준비

- 프로젝트가 이미 사용하는 **Node 24**를 사용한다. 검증 버전은 24.11.1이다. `npm install`이나 새 라이브러리는 필요 없다.
- MySQL과 정상 백엔드 `bootRun`, Vite가 실행 중이어야 한다. 로컬 기본 주소는 API `http://127.0.0.1:8080`, 화면 `http://127.0.0.1:5173`이다.
- 새 DB에는 Flyway V1~V7 적용과 **기존 M1-01 ADMIN 준비가 먼저** 필요하다. ADMIN 생성은 시드 CLI의 기능이 아니다.

ADMIN 이메일과 유효한 `{bcrypt}` 해시를 `ADMIN_SEED_EMAIL`, `ADMIN_SEED_PASSWORD_HASH` 프로세스 환경변수로 안전하게 설정한 뒤 다음 비웹 실행을 한 번 수행한다. 해시는 기존 Spring Security BCrypt 인코더로 만들며 명령 인자·채팅·Git에 넣지 않는다. 자세한 운영 절차는 [DEPLOY](../docs/DEPLOY.md#m1-01-인증-설정-추가)를 따른다.

```powershell
cd backend
.\gradlew.bat :app:bootRun '--args=--spring.profiles.active=local,admin-seed --spring.main.web-application-type=none'
# 위 프로세스 종료 뒤 정상 local 앱으로 실행한다.
.\gradlew.bat :app:bootRun
```

ADMIN 준비는 기존 ADMIN의 비밀번호를 바꾸지 않고 기존 USER를 승격하지 않는다. 아래 `DEMO_ADMIN_EMAIL`·`DEMO_ADMIN_PASSWORD`는 실제 준비한 ADMIN의 로그인 정보여야 한다. 해시는 비밀번호 대신 로그인 요청에 보낼 수 없다.

저장소 루트에서 예시를 복사하고 로컬 파일에만 다섯 계정의 비밀번호를 입력한다. 예시 파일의 이메일은 시연용이며 필요하면 바꾼다. 이메일은 서로 달라야 한다. 비밀번호는 기존 API의 8~64 코드 포인트·UTF-8 최대 72바이트 규칙을 따른다.

```powershell
Copy-Item simulator/.env.example simulator/.env
```

`.env`, `.env.*`는 Git에서 제외하고 `.env.example`만 커밋한다. Node `--env-file`보다 이미 설정한 프로세스 환경변수가 우선하므로 다른 DB/API용 값이 남아 있지 않은지 확인한다. Compose의 `.env`는 `bootRun`이 자동으로 읽지 않는다. 다른 DB를 쓰면 같은 DB 설정을 ADMIN 준비와 정상 `bootRun`에 전달한다.

## 실행

```powershell
node --env-file=simulator/.env simulator/seed/seed-demo.mjs --lanes 2 --lots-per-lane 2 --starts-in-minutes 30
# DB를 초기화하지 않고 같은 명령을 다시 실행한다.
node --env-file=simulator/.env simulator/seed/seed-demo.mjs --lanes 2 --lots-per-lane 2 --starts-in-minutes 30
```

| 인자/환경변수 | 의미 |
|---|---|
| `--lanes N` | 1~4, 기본 2 |
| `--lots-per-lane M` | 레인당 Lot 수, 양의 정수, 기본 2 |
| `--starts-in-minutes X` | 회차 생성 직전에 조회한 서버 시각에서 X분 뒤, 양의 정수, 기본 30 |
| `--output 경로` | 비밀 없는 실행 결과 JSON. 기존 파일은 덮어쓰지 않음. 기본 `simulator/output/<실행UUID>.json` |
| `DEMO_API_URL` / `DEMO_WEB_URL` | API와 브라우저 서버의 origin. 계정정보·경로·query 없이 지정 |
| `DEMO_<ADMIN/SELLER_1/SELLER_2/BIDDER_1/BIDDER_2>_EMAIL` / `_PASSWORD` | 서로 다른 5계정의 자격 정보. 환경변수 또는 ignored 파일에서만 읽음 |

기본 실행은 판매자 2명·입찰자 2명을 준비하고 이미지가 있는 출품 5개를 등록한다. 4개는 승인해 2×2로 편성하고 1개는 사유와 함께 반려한다. 시작가와 최저 낙찰가가 같은 기본값 사례와 더 높은 최저 낙찰가 사례를 섞는다. 이미지 4종은 직접 작성한 도형 일러스트이며 [출처·이미지 라이선스](seed/assets/README.md)에 설명한다.

성공하면 exit 0과 `PASS`, 실패하면 exit 1과 안전한 오류 코드가 나온다. 결과 JSON에는 실제 계정·출품·회차·레인·Lot ID, 예정시각, 확인 URL, 요청별 상태 수와 검증 결과가 있다. 비밀번호·해시·토큰·쿠키는 기록하지 않는다. 기본 결과 폴더는 Git에서 제외된다.

## 다시 실행할 때와 실패했을 때

- 정확히 `409 EMAIL_ALREADY_EXISTS`인 계정만 기존 비밀번호로 로그인해 재사용한다. 비밀번호나 역할이 다르면 중단하며 바꾸지 않는다.
- 매 실행마다 **새 출품·새 회차**를 만든다. 동일 실행의 멱등 재개나 기존 회차 덮어쓰기가 아니다. 데이터가 누적된다.
- 요청은 순서대로 실행한다. HTTP 호출 전체가 하나의 트랜잭션은 아니므로 중간 실패 전에 커밋한 자원은 남는다. 결과 JSON의 `phase`, `failure`, 완료 ID로 API·화면에서 확인한다. 생성 응답 자체를 잃은 경우에는 ID가 기록되지 않았어도 생성되었을 수 있다.
- 출품·회차 생성 POST에는 멱등 계약이 없고 편성 PUT은 Lot ID를 재생성한다. **네트워크 오류·timeout·5xx 뒤 쓰기를 자동 재전송하지 않는다.** 보호 요청의 확정 `401 UNAUTHENTICATED`만 기존 refresh API로 한 번 복구한 뒤 한 번 재전송한다. 종료 시 계정별 Refresh 쿠키를 로그아웃하고 메모리에서 지운다. 로그아웃 실패도 보고한다.
- X는 서버 시각 조회 순간부터의 여유다. 큰 M이나 네트워크 지연·시계 점프로 편성 완료 전에 예정시각이 지나면 실패할 수 있다. 큰 데이터는 충분한 X를 준다. 자동 시작·입찰·성능·복구 검증은 M2/M4 후속이다.
- 같은 회차를 다른 운영자가 편집하면 마지막 전체 편성이 남을 수 있다. 스크립트는 새 회차를 사용하고 최종 조회로 실제 상태를 검사한다.

## 직접 확인

익명 홈에서 예정 회차를 선택하거나 결과 JSON의 `session.viewerUrl`로 P-03에 직접 들어가 레인별 M개 미리보기와 Lot 상세·이미지를 확인한다. 판매자 1·2로 로그인해 `/#/me/items`에서 편성됨 및 반려 사유를 확인한다. ADMIN은 결과의 `session.adminUrl`에서 모든 레인 편성과 시작 가능 표시를 확인한다. 이 시드에서 경매 시작 버튼은 누르지 않는다.

```powershell
$API = 'http://127.0.0.1:8080'
$result = Get-Content simulator/output/<실행UUID>.json -Raw | ConvertFrom-Json
$sessionId = $result.session.sessionId
$lotId = $result.session.lanes[0].lotIds[0]
curl.exe "$API/api/sessions?status=SCHEDULED"
curl.exe "$API/api/sessions/$sessionId"
curl.exe "$API/api/lots/$lotId"
```

두 결과의 `accounts[].memberId`는 같고 `sessionId`, `items[].itemId`, `lanes[].lotIds`는 서로 달라야 한다. 각 결과의 `verification`에서 N×M·반려 1건·이미지 원본 바이트·최저가 기본값·공개 필드 비노출·판매자 stage·시작 가능·미래 시각을 확인한다. ADMIN 상세의 `reservePrice`는 기존 승인된 예외다.

```powershell
node --test simulator/seed/api-client.test.mjs simulator/seed/seed-demo.test.mjs
cd backend
.\gradlew.bat clean build
cd ../frontend
npm run lint
npm run typecheck
npm run build
```

Node 테스트의 통제 HTTP 서버·fake 클라이언트는 인증·실패 분기 확인용이다. 실제 서비스 두 번 실행·curl·브라우저 증거와 구분한다. 빈 DB 검증은 기존 개발 DB를 지우지 않고 별도 MySQL·이미지 경로에서 한다.

## 후속 구조

`seed/`는 시연 준비용이다. M2-10·M4의 k6는 `k6/`, M4-02 봇은 `bot/`에 별도 진입점으로 추가할 수 있다. 아직 도입하거나 구현하지 않는다. 결과 JSON `schemaVersion: 1`을 후속 도구의 데이터 입력 후보로 쓰되, Node `fetch`·파일 API가 k6에서도 동작한다고 가정하지 않는다. CH-11 부하 발생 구성과 측정 지점은 해당 작업에서 결정한다.
