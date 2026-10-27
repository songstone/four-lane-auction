# ADR-002: M0-02 스키마 적용과 시간 출처 검사

- 상태: 승인됨 (사용자, 2026-10-27)
- 범위 원본: [TASKS M0-02](../TASKS.md#m0-02-flyway-스키마와-시간-출처-검사)
- 시작 SHA: `95720b944daf83b73fb17100687ef59096c7252b` (`dev`, 작업 트리 clean)
- 작업 위치: 기본 checkout `C:\Users\jitin81\Documents\dev\lab\four-lane-auction` (별도 worktree 없음)

## 승인한 결정과 대안

| 결정 | 선택 | 비교한 대안과 이유 |
|---|---|---|
| V1~V4 적용 시점 | 네 파일을 M0-02에서 함께 적용 | 각 테이블 사용 시점에 나누면 변경 여지는 있지만 TASKS M0-02 수용 기준과 다르고 M0-03의 회차 조회를 준비할 수 없다 |
| 시간 출처 정적 검사 | Gradle 빌드 단계 검색 | ArchUnit은 Java 호출 대상을 더 정확히 판별하지만 SQL 검사를 별도로 해야 한다. 운영 Java·SQL·쿼리를 함께 검사하며 추가 의존성이 없는 방식을 선택했다 |
| Flyway 버전 | core·mysql을 13.11.1로 고정 | Boot 4.1.2 BOM은 12.4.0이다. 최신 안정판 도입을 위해 사용자 승인으로 Flyway에 예외를 둔다. Boot 자체와 나머지 직접 의존성의 기준 BOM은 유지한다 |
| V4 CHECK 보강 | ARCH 4.4를 먼저 수정한 뒤 SQL을 그대로 추출 | 원문 그대로라면 `PAUSED`의 원인과 `UNSOLD`의 사유가 NULL일 때 조건식이 UNKNOWN이 되어 통과한다. 각각 해당 상태 분기에 `IS NOT NULL`을 추가하도록 승인받았다 |

MySQL은 CHECK 조건이 TRUE 또는 UNKNOWN이면 통과시킨다 ([공식 설명](https://dev.mysql.com/doc/refman/8.4/en/create-table-check-constraints.html)). 두 보강 외에 V1~V4 SQL의 이름·컬럼·인덱스·시드 값은 변경하지 않는다. `member`는 MySQL 8.4.13의 비예약어다. 기본 정책 v1의 `created_by`는 원문대로 NULL이며 ADMIN 계정을 만들지 않는다.

## 구현 경계

- Flyway가 스키마를 생성하고 Hibernate는 `ddl-auto=validate`를 유지한다. `schema.sql`·`data.sql` 초기화와 섞지 않는다. baseline·out-of-order·clean은 활성화하지 않는다.
- `spring-boot-starter-flyway`는 Boot 4.1.2의 기동 연동을 제공한다. core와 MySQL 모듈은 같은 버전으로 엄격하게 고정한다. 별도 Flyway Gradle 플러그인은 추가하지 않는다.
- Testcontainers 2.0.5의 MySQL·JUnit Jupiter 모듈로 테스트 클래스별 MySQL 컨테이너를 시작한다. 컨테이너가 없으면 실패하고 테스트를 건너뛰지 않는다. 클래스가 끝나면 Spring 컨텍스트도 폐기하여 다음 테스트 클래스가 종료된 컨테이너의 DataSource를 재사용하지 않게 한다.
- 테스트 이미지는 Compose의 MySQL 8.4.13과 동일한 manifest digest다. 랜덤 호스트 포트를 사용하고 개발 DB와 볼륨을 공유하지 않는다.
- 실제 Hibernate 저장 경로는 `src/test`의 `member` 매핑으로 확인한다. 이 매핑과 USER 테스트 데이터는 제품 엔티티·시드가 아니며 트랜잭션으로 롤백한다. 실제 제품 엔티티와 Repository는 후속 작업에서 만든다.
- 일반 테스트 JVM은 UTC, `testSeoul` JVM은 Asia/Seoul이다. 같은 테스트를 실행하며 JDBC 세션과 Hibernate는 UTC를 유지한다. 재조회 값뿐 아니라 DB의 원시 UTC 문자열도 검사해 대칭적인 시간대 변환이 오류를 숨기지 않게 한다.

## 정적 검사와 CI 진입점

- 단일 CI 명령: `cd backend && ./gradlew clean build`. 정적 검사, 일반 테스트, Seoul 테스트와 빌드가 포함된다. 실제 CI 워크플로 작성·실행은 M0-04다.
- 기존 `./gradlew test`에도 `verifyTimeSources`가 선행한다.
- 범위: engine/app의 `src/main` 아래 Java·SQL·XML·YAML·properties. 테스트 코드, 생성된 빌드 산출물, 외부 라이브러리 소스는 검사하지 않는다.
- Java의 금지 호출은 일반 표기·완전 수식명·정적 import와 공백 변형을 검사한다. Java 주석은 제외하고, 문자열과 text block은 SQL·JPQL 금지 출처 검사에 포함한다. SQL·리소스의 주석도 제외한다.
- DB 시간 출처는 대소문자와 정밀도 인자를 포함해 `NOW()`·`CURRENT_TIMESTAMP`를 검사한다. 정규식 검색이라 SQL 문맥을 완전히 구분하지 못해 문자열 데이터에도 보수적으로 반응할 수 있다. 리플렉션·문자열 조합·유니코드 이스케이프로 만든 호출의 완전한 판별은 보장하지 않는다.
- Flyway의 자체 이력 테이블은 외부 라이브러리가 관리하는 메타데이터다. 그 안의 설치 시각은 경매 업무 시각이 아니며, 업무 테이블의 시간 기본값 검사에서 제외한다.
- 임시 운영 Java와 임시 새 SQL 파일로 실제 빌드 실패를 확인하고 제거한 다음 최종 명령을 성공시킨다. 이미 적용한 V1~V4를 실패 재현용으로 수정하지 않는다.

## 마이그레이션 번호와 후속 범위

V1~V4 뒤의 새 파일은 **실제로 적용하는 순서대로** 다음 번호를 매긴다 (TASKS 11장 3번). ARCH 4.3의 V5~V8은 계획 당시 번호이므로 미리 예약하지 않는다. 이미 적용한 파일은 수정하지 않으며 변경은 새 버전 파일로 낸다. ARCH 4.3 표의 정리는 TASKS가 지정한 M2-01에서 한다.

ADMIN 계정 생성 방식은 M1-01에서 정한다. 제품 API·엔티티·WebSocket·보안·CI 워크플로·프론트 기능은 M0-02에 추가하지 않는다. 시간 출처 검사 결정은 엔진의 판단·실행 방식, 타이머, 시계 점프에 관한 CHALLENGES 미결 후보를 선택한 것이 아니다.

실행 증거·버전 출처·수용 기준별 결과와 직접 확인 절차는 [PROGRESS](../PROGRESS.md)에 남긴다.
