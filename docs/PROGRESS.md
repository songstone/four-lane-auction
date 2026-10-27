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
