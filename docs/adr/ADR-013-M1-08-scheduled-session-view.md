# ADR-013: M1-08 예정 회차 관전과 연결 수명

- 상태: 사용자 ①~⑥ 권장안과 비예정 상세 임시 오류 처리 승인 (2026-10-28, Asia/Seoul). 구현·검증·기록·목적별 로컬 커밋·push·dev 대상 PR 생성 승인. 머지는 별도 지시가 필요하다.
- 시작 SHA: `8cc1e523781af71ed739ebfd755cc1ff3e501a2c`. fetch 후 dev=origin/dev·clean, 선행 M1-07 PR #10 포함 확인 후 `feature/M1-08-scheduled-session-view` 생성.
- 범위: TASKS 0장·M1-08·9장, PRD P-03 예정 모드·AC-02-6, API 4.2·4.4, EVENTS 1.1·6.1·6.7·7, CH-01. ADR-003의 홈 진단 연결과 M0-03의 데이터 있는 카드·더 보기 검증 후속을 처리한다.

| 결정 | 승인한 선택과 이유 | 대안과 비용 |
|---|---|---|
| ① 재조회 | 예정 상세 60초. 홈 목록은 기존 30초 | 상세 30초는 반영이 빠르지만 요청량 증가 |
| ② 카운트다운 | ADR-011의 B1 재사용. 실제 수신 performance.now, 최근 60초 표본 최댓값. 0초에는 곧 시작합니다, 10초 뒤 운영자 시작 대기 중 | B3는 응답별 전달 지연이 직접 반영됨 |
| ③ 조회 일관성 | 공개 상세 application readOnly REPEATABLE_READ 트랜잭션. 잘못된 외부 TX 합류 차단 | READ_COMMITTED는 편성 전후 혼합 가능. 행 잠금은 관전 조회가 쓰기를 지연 |
| ④ 홈 배지 | 연결 상태 관찰과 연결 소유 분리. 익명·ADMIN 홈은 중립적인 관전 화면에서 연결, USER는 실제 연결 상태 | 배지 숨김은 간단하지만 연결 정책 설명이 없음 |
| ⑤ READY 배지 | READY/PENDING reserveNotMet=false. 진행 중 OQ-22는 미결 유지 | 최저가 비교는 미결 정책 선결정, null/생략은 계약 위반 |
| ⑥ 모달 | 재조회에서 Lot이 사라지면 편성 변경 안내와 재선택. 직접 404는 LOT_NOT_FOUND | 오래된 내용을 유지하면 삭제된 Lot이 현재 편성처럼 보임 |

## 계약과 구현 경계

- API 0.10·EVENTS 0.4에 승인 범위를 먼저 기록한다. 새 필드·이벤트 종류는 없다. READY 스냅샷의 기존 계약을 구현한다. 부팅당 공통 무작위 epoch, seq=0, heartbeatIntervalMs=5000이며 READY 비컨을 발행하지 않는다.
- 편성된 레인은 첫 PENDING Lot, position=1, remainingLots=편성 수-1이다. 빈 레인은 lot=null, position=remainingLots=0이다. 공개 프로젝션과 응답 DTO에는 reservePrice·판매자 정보·유찰 사유가 없다. 내부 판매자 ID는 myLotIds 계산에만 쓴다.
- 조회의 header·lineup·READY snapshot·myLotIds는 같은 DB 읽기 시점이다. 일반 SELECT만 사용하며 Clock은 응답 생성 시 한 번 읽는다. Lot 상세의 finalPrice는 SOLD 또는 입찰 있는 UNSOLD에만 currentPrice다.
- 사용자 승인 임시 제한: 회차 상세는 SCHEDULED만 제공한다. RUNNING·FINISHED 요청은 기존 500 INTERNAL_ERROR로 실패한다. 홈 관전 링크도 예정 카드에만 둔다. 진행/종료 상세는 M2-04에서 이 제한을 제거한다.
- 이번에는 실제 CONNECT·레인 SUBSCRIBE·구독 뒤 상세 재조회·UNSUBSCRIBE와 예정 재조회까지 구현한다. 이벤트 발행·진행 화면·갭/비컨 복구·실시간 전환은 M2-04/05 후속이다. 테스트 메시지를 실제 제품 이벤트 증거로 쓰지 않는다.
- USER는 앱 전체에서 연결·개인 큐를 유지한다. 익명·ADMIN은 P-03에서 연결하고 떠나면 끊는다. A-02/A-03은 A-04가 아니므로 연결하지 않는다. 탭당 하나, 신원 교체 시 이전 종료 대기는 ADR-007을 따른다.
- 회차 캐시는 ['session', id], 레인 캐시는 ['lane', id]로 분리한다. 레인 자동 재조회는 끄고 applySnapshot만 갱신한다. READY는 편성 변경에도 seq=0이므로 동률 스냅샷도 적용하되 요청 취소·세대 검사로 낡은 요청 완료를 막는다. 신원 변경 때 개인화 캐시를 정리한다.
- 표본 만료 시 표시를 보류한다. 캐시를 새 시간 표본으로 세지 않는다. 모달은 열 때 조회하고 자동 재조회하지 않는다.
- 새 의존성·Flyway 없음. V1~V7 유지, 다음 번호 V8. 기존 쓰기 잠금·입찰 v1·OQ-35/38은 바꾸지 않는다.

## 실패 경계와 후속

- 읽기 TX 분리, 격리수준 변경, 조회에 잠금 읽기 혼합은 응답 내 일관성을 깨뜨릴 수 있다. 응답이 도착할 때 이미 더 새 편성이 존재할 수 있으며 다음 재조회로 갱신한다. 장시간 TX는 MVCC 과거 버전 유지 비용이 있다.
- 편성은 Lot ID를 새로 만든다. 배열 일부 병합·오래된 me 유지·seq 동률 무조건 버리기는 잘못된 화면을 만든다. 인증·경로 변경의 늦은 응답도 폐기해야 한다.
- 브라우저 백그라운드 제한·네트워크 지연으로 실제 갱신은 늦어질 수 있다. 복귀 시 다시 읽는다. 시간 표시는 서버 판정이 아니며 자동 시작·200ms 성능·복구 검증을 뜻하지 않는다.
- M2-04: 비예정 상세와 레인 단독 스냅샷, 진행 중 OQ-22. M2-05: 이벤트 적용·갭/비컨 복구·첫 LOT_STARTED의 0~3초 지연 재조회. M3-06: 실제 개인 알림. OQ-27 데이터 시드는 M1-10이다.

근거: [MySQL consistent reads](https://dev.mysql.com/doc/refman/8.4/en/innodb-consistent-read.html), [Spring propagation](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/tx-propagation.html). 실제 실행 결과와 증거의 범위는 PROGRESS M1-08에 기록한다.
