# Four-Lane Auction — 웹 기반 멀티레인 실시간 경매 플랫폼

회차마다 1~4개 레인에서 동시에 진행되는 C2C 중고 물품 경매를 한 화면에서 실시간으로 보고 입찰하는 웹 플랫폼.
백엔드 역량을 증명하기 위한 포트폴리오 프로젝트다.

## 문서 (작업 전에 관련 문서를 먼저 읽을 것)
- docs/PRD.md: 기능, 경매 규칙, Non-goals
- docs/ARCHITECTURE.md: 구조, 데이터 모델, 입찰·레인 엔진 흐름
- docs/API.md, docs/EVENTS.md: REST·WebSocket 계약. 요청·응답·메시지 형식과 에러 코드의 원본
- docs/CHALLENGES.md: 기술 과제와 해결 후보. 미결 후보는 임의로 고르지 않는다
- docs/IDEA.md: 배경, 10주 마일스톤
- docs/TASKS.md: 작업 목록, 착수 전 결정, 수용 기준. 0.2절에 정한 것(입찰 v1 = DB 비관적 락 등)이 있다
- 결정 기록: 제품 규칙은 PRD 10장 OQ 표, 기술 결정은 docs/adr/에 남기고 CHALLENGES 후보 표·ARCHITECTURE 부록 A에 결과를 표시한다. 진행 상황은 docs/PROGRESS.md (둘 다 M0-01에서 만든다)

## 구조
- backend/: Spring Boot (engine / app 멀티 모듈) → 규칙은 backend/AGENTS.md
- frontend/: React + Vite + TypeScript + Tailwind → 규칙은 frontend/AGENTS.md
- simulator/: 봇 입찰기, k6 부하 테스트 스크립트 (7주차 예정)

## 명령어
- 인프라: docker compose up -d mysql (로컬에서 bootRun을 쓸 때 compose의 app 서비스는 띄우지 않는다)
- 백엔드 실행/테스트: cd backend && ./gradlew :app:bootRun / ./gradlew test
- 프론트 실행/검사: cd frontend && npm run dev / npm run typecheck / npm run build
- API 타입 생성: cd frontend && npm run gen-api (백엔드 실행 중이어야 함. 엔드포인트가 아직 없으면 API.md 기준 목 서버로 개발한다)

## 작업 방식
- 한 세션에서는 docs/TASKS.md의 작업 하나만 한다. 범위 밖 파일은 수정하지 않는다
- 코드를 쓰기 전에 변경할 파일, 접근 방식, 내가 결정해야 할 사항을 보여주고 승인을 기다린다
- 설계 결정(트랜잭션 경계, 락 전략, 타이머 방식, 이벤트 구조)은 대안을 비교한 뒤 내가 결정한다
- TASKS 0.2에서 정한 것을 먼저 따르고, 그다음 ARCHITECTURE의 "결정됨"·"잠정안"을 따른다. 그와 다르게 하거나 CHALLENGES의 미결 후보를 고를 때만 대안을 비교해 결정을 받는다
- 새 의존성을 추가하기 전에 이유를 말하고 허락을 받는다
- 확실하지 않으면 추측하지 말고 질문한다
- 완료 = 테스트와 빌드 통과 + 수용 기준 항목별 확인 + 내가 직접 확인할 방법 보고

## 계약 변경 순서
- 수직 작업은 TASKS 0.4의 [BE] → [GEN] → [FE] → [통합] 순서로 한다. 계약(API.md·EVENTS.md)을 바꿔야 하면 멈추고 승인을 받은 뒤 아래 순서로 한다
- REST: docs/API.md 수정(버전 올림) → 백엔드 DTO·컨트롤러 → 백엔드 테스트(계약 테스트 포함) → npm run gen-api → 프론트 수정 → typecheck
- WebSocket 이벤트: docs/EVENTS.md(버전 올림), 백엔드 이벤트 클래스, frontend/src/realtime/events.ts를 같은 커밋에서 수정
- 응답·메시지에 필드를 더하는 것은 하위 호환이다. 필드 삭제, 이름·타입·의미 변경은 계약 변경이다

## 금지
- 테스트를 삭제하거나 @Disabled·skip 처리해서 통과시키기
- 가짜 데이터나 하드코딩으로 기능을 완성한 것처럼 처리하기
- 비밀값 커밋 (.env, application-local.yml은 .gitignore에 넣는다)
- 직접 측정하지 않은 성능 수치를 문서에 쓰기
