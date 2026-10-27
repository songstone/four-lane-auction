# Backend — Spring Boot

## 패키지 구조
com.fourlane.auction
├── auth/        인증, JWT, WebSocket 인증 인터셉터
├── event/       경매 회차
├── lane/        레인, 레인 엔진
├── lot/         출품물
├── bid/         입찰
├── realtime/    STOMP 설정, 이벤트 발행
└── global/      config, exception, common
각 도메인 패키지: controller / service / repository / domain / dto

## 설계 규칙
- Entity를 API 응답으로 직접 반환하지 않는다. 요청·응답 DTO는 record로 만든다
- 경매 규칙(입찰 가능 여부, 연장, 마감, 상태 전이)은 Entity 도메인 메서드에 둔다. Service는 흐름만 조율한다
- Entity에 @Setter를 쓰지 않는다. 상태 변경은 의미 있는 이름의 메서드로 한다 (lot.placeBid(), lot.close())
- @Transactional은 Service에만 붙이고, 조회는 readOnly = true
- 생성자 주입만 사용한다
- 연관관계는 기본 LAZY. 필요한 조회는 fetch join이나 QueryDSL로 해결한다

## 시간 규칙
- 모든 시간 판단은 서버 시계 기준. 클라이언트가 보낸 시각은 신뢰하지 않는다
- 시각은 Instant(UTC)로 저장하고 전송한다. 이벤트에는 절대 시각(endsAt)을 담는다
- 시간이 필요한 코드는 java.time.Clock을 주입받는다. 테스트에서는 고정·조작 가능한 Clock을 쓴다
- 테스트에서 Thread.sleep으로 시간을 기다리지 않는다

## 입찰·실시간 규칙
- 입찰 수락 판단(상태, 마감, 최소 금액, 최고 입찰자 본인 여부)과 반영은 하나의 원자적 연산 안에서 한다
- 입찰 API는 Idempotency-Key 헤더를 필수로 받고, 같은 키는 첫 번째 결과를 그대로 돌려준다
- 브로드캐스트는 트랜잭션 커밋 이후에만 한다 (@TransactionalEventListener(phase = AFTER_COMMIT))
- 모든 레인 이벤트에는 레인별 seq를 붙인다
- 마감 처리와 다음 출품물 전환은 멱등해야 한다 (두 번 실행돼도 결과가 같아야 함)
- 브로드캐스트하는 입찰자 정보는 마스킹한다

## 예외 처리
- 비즈니스 예외는 BusinessException(ErrorCode)으로 던진다
- ErrorCode enum에 HTTP 상태, 코드, 메시지를 정의한다 (docs/API.md의 에러코드와 일치)
- @RestControllerAdvice에서 { "error": { "code", "message" } } 형식으로 응답한다

## DB
- 스키마 변경은 Flyway 마이그레이션으로만 한다 (ddl-auto: validate)
- 이미 적용된 마이그레이션 파일은 수정하지 않고 새 파일을 추가한다

## 테스트
- 도메인 규칙: 순수 단위 테스트 (Clock 주입)
- Repository·통합 테스트: Testcontainers(MySQL, Redis). H2는 쓰지 않는다
- Controller: @WebMvcTest
- 동시성 관련 기능은 멀티스레드 테스트(ExecutorService + CountDownLatch) 없이 완료 처리하지 않는다
- WebSocket 기능은 STOMP 테스트 클라이언트로 구독·수신을 검증한다

## AI 작업 방식
- 구현 후 핵심 결정의 이유를 설명한다. 나는 이 코드를 면접에서 설명해야 한다
- 동시성, 트랜잭션, 타이머 관련 코드는 "어떤 상황에서 깨질 수 있는지"를 함께 설명한다