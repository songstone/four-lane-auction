# ADR-012: M1-07 회차 편성과 중복 방지

- 상태: 사용자 ①~⑤ 권장안·변경·검증·로컬 커밋·push·dev 대상 PR 생성 승인 (2026-10-28, Asia/Seoul). 머지는 별도 지시가 필요하다.
- 범위: TASKS 0장·M1-07·9장, PRD M-05·AC-05-1/2/4 거절·A-03·OQ-28/46, API 6.5·7.1·7.2, ARCH 4.4·4.6·5.5.1, CH-02.
- 시작 SHA: `f2a842c0dedc05e8a1b07cec13f2a53223f61a99`. dev=origin/dev·clean, 선행 M1-04 PR #7·M1-06 PR #9 ancestry 확인 후 feature/M1-07-session-lineup 생성.

| 항목 | 승인한 선택과 이유 | 대안과 비용 |
|---|---|---|
| ① 중복 편성 | 다른 레인·회차 중복 금지. 같은 레인 전체 재편성 허용. 이동은 원본 제거·저장 후 대상 추가 | 허용은 물품 중복 경매, 자동 이동은 다른 편성까지 바꾸는 새 계약 필요 |
| ② 재출품 가격 | 기존 시작가·최저 낙찰가 유지, 본문 없는 relist 계약 유지. 구현은 M3-08 | 가격 변경 허용은 relist 계약·화면·검증 확장 필요 |
| ③ 잠금 | 회차 조건부 갱신 → 기존·요청 Item 합집합 PK 오름차순 PESSIMISTIC_WRITE | 생성 컬럼 UNIQUE는 새 스키마·후속 상태 전이 관리, 전역 잠금은 무관한 편성까지 직렬화 |
| ④ 조회 | 편성 application TX만 READ_COMMITTED. 다른 격리수준 외부 TX 합류는 실패로 차단 | REPEATABLE READ는 선행 조회의 낡은 스냅샷을 피하도록 판정 전체에 잠금 읽기 규칙 필요 |
| ⑤ Clock 역행 | 잠금 뒤 Clock 1회가 승인 시각보다 이르면 INTERNAL_ERROR·전체 롤백 | 승인 시각 보정은 생성 시각 의미를 변경. OQ-35 일반 시계 점프 정책은 후속 |

## 트랜잭션과 계약

- 회차 행을 status=SCHEDULED 조건으로 맨 먼저 갱신한다. 무변경 UPDATE는 Connector/J 기본 useAffectedRows=false의 matched rows 의미를 사용한다. 0행이면 없는 회차와 시작한 회차를 구별한다. 실제 MySQL에서 무변경 반환 건수·대기·상태 거절을 검증한다.
- 이 회차 잠금은 같은 회차의 편성과 M2-02 시작을 직렬화한다. 회차가 다르면 별도 잠금이므로 Item 잠금이 필요하다. 기존·요청 Item 합집합을 오름차순으로 잡아 제거·교체도 동일 규약에 참여한다.
- auction.session은 item.domain의 편성 조회·잠금 포트만 사용한다. Item 테이블을 수정하지 않는다. 조회 결과는 내부 값 객체이며 엔티티를 응답으로 직렬화하지 않는다.
- stage는 공통 JPQL 식으로 계산한다. reviewStatus 우선, created_at >= reviewed_at인 최신 (created_at, id) Lot을 사용한다. 판매자 목록·운영자 stage 필터·편성 판정이 같은 식을 사용한다. 활성 Lot의 다른 레인 점유도 별도로 확인한다.
- 기존 Lot 전체 삭제·flush 뒤 요청 순서대로 새 PENDING Lot을 만든다. 시작가·최저 낙찰가를 복사하고 current_price=start_price, bid_count=0이다. 검증·삭제·삽입·응답 조회는 한 TX, 서비스 프록시 커밋 뒤 컨트롤러 응답이다. 같은 요청도 새 lotId를 만든다.
- API 0.9를 먼저 반영했다. error.itemIds·error.items는 기존 계약의 구현이다. 편성 이벤트는 없으므로 EVENTS 변경 없음. 새 의존성·Flyway 없음, V1~V7 불변, 다음 번호 V8.

## 실패 경계와 후속

- 모든 편성 경로가 같은 회차·Item 잠금 규칙을 따라야 한다. 우회 SQL, 잠금 순서 역전, 낡은 스냅샷·관리 엔티티 사용은 중복·교착을 만들 수 있다. DB 장애·락 타임아웃·교착은 일반 500이며 자동 재시도하지 않는다. 잠금 대기는 커넥션을 점유한다.
- 커밋 전 실패는 삭제한 기존 Lot까지 롤백한다. 커밋 후 응답 유실이면 상세를 다시 조회한다. PUT을 재전송하면 Lot ID가 다시 바뀌므로 화면은 응답·재조회의 ID로 교체한다. 같은 레인의 동시 전체 저장은 직렬 처리의 마지막 저장이 남으며 revision 계약은 없다.
- Clock 역행 가드는 이번 승인보다 이른 편성 기록을 막는 국소 검증이다. 이후 재출품 재승인 시각과 과거 Lot의 밀리초 동률·시계 점프는 M3-08에서 같은 계약 경계를 확인해야 한다. 일반 시계 점프 OQ-35는 정하지 않는다.
- 실제 시작·편성 종합 경합은 M2-02, 공개 관전 편성은 M1-08, tradeStatus 실제 연동은 M3-07, relist는 M3-08이다. 이번 SOLD/UNSOLD/BIDDING fixture를 경매 완주·거래 구현 증거로 쓰지 않는다.

근거: [MySQL consistent reads](https://dev.mysql.com/doc/refman/8.4/en/innodb-consistent-read.html), [locking reads](https://dev.mysql.com/doc/refman/8.4/en/innodb-locking-reads.html), [Connector/J useAffectedRows](https://dev.mysql.com/doc/connector-j/en/connector-j-connp-props-connection.html), [Spring Transactional isolation](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/transaction/annotation/Transactional.html).

실제 실행·수용 기준별 결과·직접 확인 절차·NOT_RUN은 PROGRESS M1-07에 기록한다.
