# 4Lane Auction — 웹 기반 4레인 실시간 경매 플랫폼

4개 레인에서 동시에 진행되는 중고차 경매를 한 화면에서 실시간으로 보고 입찰하는 웹 플랫폼.
백엔드 역량을 증명하기 위한 포트폴리오 프로젝트다.

## 문서 (작업 전에 관련 문서를 먼저 읽을 것)
- docs/PRD.md: 기능, 경매 규칙, Non-goals
- docs/ARCHITECTURE.md: 구조, 데이터 모델, 입찰·레인 엔진 흐름
- docs/API.md, docs/EVENTS.md: REST·WebSocket 계약
- docs/TASKS.md: 작업 목록 / docs/PROGRESS.md: 진행 상황과 결정
- docs/adr/: 기술 의사결정 기록

## 구조
- backend/: Spring Boot → 규칙은 backend/CLAUDE.md
- frontend/: React + Vite + Tailwind → 규칙은 frontend/CLAUDE.md
- simulator/: 봇 입찰기, k6 부하 테스트 스크립트

## 명령어
- 인프라: docker compose up -d
- 백엔드 실행/테스트: cd backend && ./gradlew bootRun / ./gradlew test
- 프론트 실행/검사: cd frontend && npm run dev / npm run typecheck / npm run build
- API 타입 생성: cd frontend && npm run gen-api (백엔드 실행 중이어야 함)

## 작업 방식
- 한 세션에서는 docs/TASKS.md의 작업 하나만 한다. 범위 밖 파일은 수정하지 않는다
- 코드를 쓰기 전에 변경할 파일, 접근 방식, 내가 결정해야 할 사항을 보여주고 승인을 기다린다
- 설계 결정(트랜잭션 경계, 락 전략, 타이머 방식, 이벤트 구조)은 대안을 비교한 뒤 내가 결정한다
- 새 의존성을 추가하기 전에 이유를 말하고 허락을 받는다
- 확실하지 않으면 추측하지 말고 질문한다
- 완료 = 테스트와 빌드 통과 + 수용 기준 항목별 확인 + 내가 직접 확인할 방법 보고

## 계약 변경 순서
- REST: 백엔드 DTO·컨트롤러 → 백엔드 테스트 → npm run gen-api → 프론트 수정 → typecheck
- WebSocket 이벤트: docs/EVENTS.md, 백엔드 이벤트 클래스, frontend/src/realtime/events.ts를 같은 커밋에서 수정

## 금지
- 테스트를 삭제하거나 @Disabled·skip 처리해서 통과시키기
- 가짜 데이터나 하드코딩으로 기능을 완성한 것처럼 처리하기
- 비밀값 커밋 (.env, application-local.yml은 .gitignore에 포함)
- 직접 측정하지 않은 성능 수치를 문서에 쓰기