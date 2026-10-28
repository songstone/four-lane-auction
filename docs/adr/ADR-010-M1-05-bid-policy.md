# ADR-010: M1-05 호가 정책과 버전 배정

- 상태: 사용자 승인 (2026-10-28, Asia/Seoul). 전용 카운터·JS 안전 정수 상한·FE 포함, 구현·검증·기록·로컬 커밋·push·dev 대상 PR 생성 승인. 머지는 별도 지시가 필요하다.
- 시작 SHA: `458663009ed2f027fd8d6f92bd178788fedabeae`. fetch 후 dev=origin/dev, clean, 선행 M1-01 PR #4 포함 확인 후 `feature/M1-05-bid-policy` 생성.
- 범위: TASKS 0장·M1-05·9장, PRD M-08·S-04·AC-08-1/3·A-05, API 7.4, ARCH 3·4.4. AC-08-2의 실제 회차 고정은 M2-02·M2-06 후속이다.

| 항목 | 승인한 선택과 이유 | 비교한 대안 |
|---|---|---|
| 버전 배정 | V6의 singleton 카운터 PK `PESSIMISTIC_WRITE`. 고정된 잠금 대상과 정책 데이터의 역할을 분리한다 | v1 행 잠금은 시드 정책이 전체 등록 잠금 역할을 겸한다. MAX+1/UNIQUE 재시도는 트랜잭션 재시작과 상한 처리가 필요하다 |
| 수치 | 각 값 9,007,199,254,740,991 이하. 기존 JSON number·생성 클라이언트로 정밀도를 보존한다 (PRD OQ-56) | 10억 제한은 출품물 상한을 정책에 확대한다. BIGINT 전체 범위는 FE 정밀도 보존·직렬화 변경이 필요하다 |
| 화면 | A-05 포함. 기존 생성 클라이언트·TanStack Query·hash 경로 사용 | 일정 축소 시 TASKS 1.1에 따라 FE를 제외하고 API를 남길 수 있다 |

## 트랜잭션과 모듈

- engine의 `BidIncrementPolicy`·`TieredBidIncrementPolicy`는 java.*만 사용한다. 구간은 방어 복사로 불변이며 입력 순서를 정렬하지 않는다. 다음 호가는 `Math.addExact`로 계산한다. DB 행 → 엔진 정책 변환은 app의 Mapper가 맡는다.
- application 쓰기 트랜잭션에서 입력 검증 → 카운터 행 잠금 → Clock 1회 → 카운터 증가 → 정책·구간 INSERT → 커밋 후 응답. 정책·구간에는 변경 메서드·수정/삭제 API를 두지 않는다. 카운터 변경과 정책 INSERT는 같은 트랜잭션이다.
- 조회는 readOnly 트랜잭션과 JPQL 프로젝션이다. 작성자는 member ID로 참조하며 닉네임만 응답한다. 목록은 버전 내림차순·구간 하한 오름차순이며 `isLatest`는 해당 조회의 최대 버전이다.
- V6은 기존 최대 version으로 초기화한다. V1~V5는 수정하지 않는다. 이후 마이그레이션은 실제 순서대로 V7부터다. 새 의존성·EVENTS 변경·CHALLENGES 미결 후보 선택은 없다.

## 실패 경계와 후속

- 모든 등록 경로가 같은 카운터를 먼저 잠가야 한다. 직접 SQL·우회 쓰기나 카운터와 정책의 트랜잭션 분리는 보장을 깨뜨린다. 기존 version UNIQUE도 유지한다. DB 관리자 직접 UPDATE를 차단하는 트리거·권한 분리는 이번 범위가 아니다.
- 정상 동시 등록 두 건은 서로 다른 연속 번호를 받는다. 커밋 전 실패는 카운터·정책·구간 전체가 롤백된다. AUTO_INCREMENT policyId는 롤백으로 빈 번호가 생길 수 있으며 version과 구분한다.
- DB 장애·락 타임아웃·정수 버전 소진은 일반 서버 오류다. 등록의 네트워크 오류·5xx는 자동 재시도하지 않는다. 커밋 뒤 응답 유실 후 재등록하면 새 버전이 추가될 수 있어 목록을 먼저 확인한다.
- 잠금 대기는 커넥션을 점유한다. 잠금 중 외부 I/O를 하지 않는다. 서버 Clock 역행은 createdAt을 역행시킬 수 있지만 버전 순서는 바뀌지 않는다. 레인 타이머·OQ-35는 정하지 않는다.
- `isLatest`는 응답 생성 시점의 값이다. 응답 도착 전 다른 버전이 등록될 수 있으므로 화면은 등록 후 목록을 재조회한다.
- AC-08-2의 회차 시작 시 고정과 실제 입찰의 금액 끝단 처리는 M2-02·M2-06에서 검증·결정한다. 이번 정책 계산 검증을 실제 경매 완주 증거로 쓰지 않는다.

근거: [MySQL locking reads와 counter 예제](https://dev.mysql.com/doc/refman/8.4/en/innodb-locking-reads.html), [Spring Data JPA Lock](https://docs.spring.io/spring-data/jpa/reference/jpa/locking.html). 실제 수용 기준·검증·NOT_RUN은 PROGRESS M1-05에 기록한다.
