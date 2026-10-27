# Backend — Spring Boot

## 모듈과 패키지 (ARCHITECTURE 3.1~3.3)
engine 모듈: com.fourlane.engine — 순수 Java. java.*에만 의존한다 (Spring·JPA·Jackson 금지)
├── lane/      LaneStateMachine(판단), LaneState, LaneView, LaneCommand, LaneEvent, LaneRuntime(큐·단일 작성자, v2 후보)
├── session/   SessionAutoStarter, SessionStartRule
├── policy/    BidIncrementPolicy (Strategy)
└── port/      LaneStore, LaneEventPublisher, LaneNotifier, TimerScheduler
app 모듈: com.fourlane
├── global/        config, security, error, idempotency, metrics
├── auth/          회원가입·로그인·재발급·로그아웃
├── member/        회원, 경고, 입찰 정지
├── item/          출품·검수·재출품, ImageStorage
├── auction/       session(회차), control(정지·재개·강제 종료), policy(호가 정책 버전), runtime(LaneRegistry, JpaLaneStore, 타이머, 재시작 복구)
├── bid/           입찰 API, 조건 1~4 검증, 내 입찰 내역
├── trade/         거래, 모의 결제, 결제 기한 만료
├── realtime/      STOMP 설정, 인증 인터셉터, LaneEventPublisher 구현, 스냅샷 API
├── notification/  알림함, 개인 큐 전송
└── audit/         운영자 감사 로그
도메인 패키지 안: api(컨트롤러·DTO) / application(유스케이스·트랜잭션) / domain(엔티티·리포지토리) / infra(조회 쿼리·외부 어댑터)
의존: api → application → domain ← infra. 도메인 사이는 bid → auction, auction → trade·notification·audit, trade → member·notification, realtime → auction.runtime만

## 설계 규칙
- 엔티티와 엔진 상태를 응답·이벤트로 직접 직렬화하지 않는다. 요청·응답 DTO는 record로 api 층에서만 만든다. 공개 DTO·이벤트·스냅샷에는 최저 낙찰가 필드가 없다
- 레인·Lot 상태 전이, 입찰 조건 5~9, 마감 연장·판정은 engine의 LaneStateMachine에 둔다. 상태 머신 표현 방식(CHALLENGES CH-02 (가))은 미결이다
- 엔티티에 @Setter를 쓰지 않는다. 상태 변경은 의미 있는 이름의 메서드로 한다
- 레인 전이의 영속화는 JpaLaneStore가 맡는다. lot 쓰기는 조건부 UPDATE(status·bid_count 조건)로 하고, 영향받은 행이 1인지 확인한다
- 트랜잭션 경계
  - 일반 유스케이스: application 층 @Transactional, 조회는 readOnly = true
  - 레인 전이: 전이 1번 = 트랜잭션 1개. 여는 위치는 M2-01에서 정한다 (아래 "입찰 동시성 구조")
  - spring.jpa.open-in-view=false
- 생성자 주입만 사용한다
- 연관관계는 기본 LAZY이고 같은 도메인 패키지 안에서만 둔다. 다른 도메인은 ID 값으로 참조한다
- 조회 전용 쿼리는 @Query(JPQL) + DTO 프로젝션. QueryDSL은 쓰지 않는다

## 시간 규칙
- 모든 시간 판단은 서버 시계 기준. 클라이언트가 보낸 시각은 신뢰하지 않는다
- 현재 시각은 주입된 Clock 빈 하나에서만 얻는다. 빈은 Clock.tick(Clock.systemUTC(), Duration.ofMillis(1))이다 (밀리초로 자름)
- 쓰지 않는다: Instant.now(), LocalDateTime.now(), System.currentTimeMillis(), new Date(), DB의 NOW()·CURRENT_TIMESTAMP 기본값. created_at·updated_at도 Clock으로 채운다
- 시점은 Instant, 기간은 Duration. 도메인에 LocalDateTime·ZonedDateTime·Date를 쓰지 않는다. OffsetDateTime은 요청 파싱에만 쓴다
- DB는 DATETIME(3) UTC (hibernate.jdbc.time_zone=UTC). 응답 시각은 UTC(Z)에 밀리초 3자리, 요청 시각에 오프셋이 없으면 400
- 이벤트와 스냅샷에는 절대 시각(deadlineAt, gapEndsAt)과 serverTime을 담는다
- 경계: 입찰 수락은 처리 시각 < 마감, 마감 판정은 처리 시각 ≥ 마감
- 테스트는 손으로 움직이는 가짜 Clock과 FakeScheduler를 쓴다. Thread.sleep으로 시간을 기다리지 않는다
- 예외: 메트릭의 소요 시간은 Micrometer의 단조 시계로 잰다

## 입찰·실시간 규칙
- 입찰 동시성 구조: v1은 DB 비관적 락이다 (TASKS 0.2). 락 대상, 처리 시각의 기준값, seq 부여와 발행, 스냅샷 출처, 503 상한, 멱등 저장, 레인 전이 트랜잭션을 여는 위치는 M2-01에서 정하고 ADR에 남긴다. ARCHITECTURE의 레인 직렬 큐(5·6장)는 v2 후보다
- 아래 규칙은 v1·v2 어느 구조든 지킨다
- 검증 순서는 PRD 6.3 그대로: 인증·역할 → 형식(400) → 레인 존재(404) → 2 멱등 → 3 입찰 정지 → 4 본인 출품 → 과부하(503) → 5~9. 5~9는 줄을 선 뒤 처리 시각 기준으로 검사한다
- 금액은 같아야 한다: expectedPrice = 서버 현재가, amount = 다음 호가. lotId는 지금 BIDDING인 Lot이어야 한다
- 한 레인의 입찰, 타이머(마감 확인·간격 종료·Tick), 운영자 명령(정지·재개·강제 종료)은 줄을 서서 하나씩 처리한다 (PRD C2)
- 처리 시각은 줄을 선 뒤 Clock을 한 번 읽은 값이고, 그 처리 안의 모든 판단에 같은 값을 쓴다 (PRD C3)
- 커밋한 뒤에만 응답하고 발행한다. 커밋이 실패하면 발행하지 않는다
- 모든 레인 이벤트에 epoch와 seq를 붙인다. 서버가 매긴 seq에는 갭이 없고, 레인별 커밋 순서대로 발행한다. HEARTBEAT와 거절된 입찰은 seq를 올리지 않는다
- 멱등 (API 1.6): Idempotency-Key 필수. 같은 키·같은 요청(경로 포함)이면 처음 결과, 요청이 다르면 422 IDEMPOTENCY_KEY_REUSED. 성공은 입찰과 같은 트랜잭션에 기록해 재시작 뒤에도 재생하고, 거절은 재시작 전까지만 재생한다. 503은 기록하지 않는다. 500 BID_RESULT_UNKNOWN 뒤 같은 키 재전송은 그 입찰의 실제 결과를 받는다 (AC-09-13)
- 마감 확인과 간격 종료는 세대 번호로 낡은 명령을 버린다. 두 번 실행돼도 결과가 같아야 한다
- 입찰자는 마스킹 닉네임으로만 보낸다. 회원 ID는 넣지 않는다

## 예외 처리
- 비즈니스 예외는 ApiException(ErrorCode)으로 던진다 (global/error)
- ErrorCode enum에 HTTP 상태, 코드, 메시지를 정의한다 (docs/API.md 9장과 일치)
- @RestControllerAdvice에서 API.md 1.4 형식으로 응답한다: { "error": { "code", "message", … } }
  - 레인 상태로 거절된 입찰(409, 422의 LANE_NOT_RUNNING·LOT_NOT_BIDDING)과 운영자 레인 명령 거절에는 error.lane { epoch, seq }. 운영자 거절에는 error.laneStatus 또는 error.lotStatus도
  - 400 INVALID_REQUEST에는 error.fields
  - 스택 트레이스와 내부 예외 이름은 넣지 않는다

## DB
- 스키마 변경은 Flyway 마이그레이션으로만 한다 (ddl-auto: validate)
- 이미 적용된 마이그레이션 파일은 수정하지 않고 새 파일을 추가한다

## 테스트
- 엔진(engine 모듈): Spring 없이 순수 단위 테스트. 가짜 Clock + FakeScheduler
- Repository·통합 테스트: Testcontainers(MySQL). H2는 쓰지 않는다. Redis는 MVP에 없다
- Controller: @WebMvcTest
- 동시성 관련 기능은 멀티스레드 테스트(ExecutorService + CountDownLatch로 한 순간에 출발, 수백~수천 번 반복) 없이 완료 처리하지 않는다. 한 번 통과한 것은 증거가 아니다
- WebSocket 기능은 WebSocketStompClient로 구독·수신을 검증한다

## AI 작업 방식
- 구현 후 핵심 결정의 이유를 설명한다. 나는 이 코드를 면접에서 설명해야 한다
- 동시성, 트랜잭션, 타이머 관련 코드는 "어떤 상황에서 깨질 수 있는지"를 함께 설명한다
