# Four-Lane Auction — 진행·검증 기록

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
- 상태: 진행 중 — [BE]·[GEN]·[FE] 완료
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
- **[FE] PASS — 타입·빌드**: `@stomp/stompjs` 7.5.0 설치, `npm run typecheck`·`npm run build` 성공 (`output/m0-03-fe-checks.log`). 세 상태별 Infinite Query와 다음 커서의 더 보기, 로딩·빈 상태·오류·다시 조회, 30초/화면 복귀 재조회, 서버 시각의 마지막 수신값, 독립된 REST·STOMP 배지를 구현했다. 브라우저 REST는 생성된 요청 함수만 호출한다. STOMP는 현재 location의 같은 출처 `/ws`, 10초 하트비트, 최대 30초의 지수 백오프와 시도별 jitter를 사용한다. 구독자 하나가 떠난 뒤 소켓 종료를 기다려 StrictMode 재마운트의 중복 연결을 막는다. 이 단계의 브라우저 통합은 아직 NOT_RUN이다.
