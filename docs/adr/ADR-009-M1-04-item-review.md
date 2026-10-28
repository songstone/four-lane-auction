# ADR-009: M1-04 출품 검수와 동시 상태 전이

- 상태: 사용자 승인 (2026-10-28, Asia/Seoul). ①은 운영자 상세 최저가 노출로 변경 승인, ②~④와 변경·검증·로컬 커밋 계획 승인. push·PR 생성은 별도 승인.
- 범위: TASKS M1-04, PRD M-03·AC-03-1~3·A-01, API 7.1. 알림 M3-06, 감사 로그 후속, 편성 쓰기 M1-07은 별도 작업이다.
- 시작 SHA: `632585e6e82b4ee0334db50c78cc549aafdf83e2`. fetch 후 dev=origin/dev, clean, M1-03 PR #6 포함 확인. `feature/M1-04-item-review`.

| 항목 | 승인한 결정과 이유 | 비교한 대안 |
|---|---|---|
| B-7 | ADMIN 상세에서만 reservePrice를 제공해 운영자가 확인한다. 공개·판매자·운영자 목록은 비노출 | 모두 비노출은 운영자 확인 요구를 충족하지 못함. 목록 공개는 필요 이상의 노출 |
| 경합 | application @Transactional에서 Item PK PESSIMISTIC_WRITE → 현재 상태 확인 → Clock 1회 → 상태·사유·두 시각 변경 → 커밋 후 응답 | 조건부 UPDATE는 실패 상태 재조회·JPA 동기화가 추가됨. @Version은 스키마·충돌 변환 필요 |
| 계약 | 실제 응답 카탈로그에 운영자 4경로와 오류·권한 사례 등록. GET /api/admin/items/{itemId}, ADMIN, 200의 최상위 reservePrice만 허용 | 운영자 전체 제외는 다른 필드·중첩 경로 누출을 놓침 |
| 사유 | 원문 1~500 코드 포인트, 공백만 거절, 원문 보존 | strip 후 저장은 입력 내용을 조용히 바꿈 |
| 조회 | readOnly 트랜잭션과 JPQL DTO 프로젝션. 기존 Lot을 읽어 stage·이력 계산. 시각·ID 키셋, PENDING_REVIEW 오름차순·그 밖 내림차순 | reviewStatus를 stage로 복사하면 편성 이후 필터가 틀림 |

새 의존성·Flyway·EVENTS 변경 없음. API 0.6부터 ADMIN 상세 필드 예외를 명시한다. V1~V5는 수정하지 않는다. TASKS 0.2의 입찰 v1 및 CHALLENGES 미결 후보 선택과 별개다.

## 실패 경계

- 같은 Item을 바꾸는 검수 경로는 모두 같은 PK 락을 사용해야 한다. 잠금 전 미리 엔티티를 읽거나 별도 락 없는 쓰기를 추가하면 보장이 깨질 수 있다. OSIV는 끈다.
- 락은 커밋·롤백까지 유지된다. 대기는 커넥션을 점유한다. DB 장애·락 타임아웃은 일반 500이며 200/422 경합의 정상 완료 보장과 구분한다. 자동 재시도하지 않는다.
- 커밋 전 실패는 상태·사유·시각 전부 롤백한다. 커밋 뒤 응답이 유실되면 다음 요청은 422일 수 있어 재조회로 확인한다. 검수 명령에는 멱등 키 계약이 없다.
- Clock은 락 획득 뒤 한 번 읽고 reviewed_at·updated_at에 같은 값을 쓴다. 시계 역행 시 과거 검수 시각이 기록될 수 있다. 레인 시계 점프 OQ-35는 여기서 정하지 않는다.
- ADMIN 상세의 허용 필드 밖에서는 reservePrice 이름 변형·중첩 필드·값 표식을 계속 검사한다. 허용 경로도 저장된 금액과 정확히 일치해야 한다.

실제 실행·수용 기준·NOT_RUN은 PROGRESS M1-04에 기록한다.

근거: [MySQL 잠금 읽기](https://dev.mysql.com/doc/refman/8.4/en/innodb-locking-reads.html), [Spring Data JPA Lock](https://docs.spring.io/spring-data/jpa/reference/jpa/locking.html).
