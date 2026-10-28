# ADR-008: M1-03 출품 등록과 로컬 이미지 저장

- 상태: 사용자 ①~⑩ 권장안 전체 승인 (2026-10-28, Asia/Seoul). BE → GEN → FE → 통합, 검증·기록·로컬 커밋·push·dev 대상 PR 생성까지 승인. 머지는 별도 지시가 필요하다.
- 시작 SHA: `163546821e1c91e174b7abb868465a76e78a03e2`. dev=origin/dev, clean, M1-01 PR #4 ancestry 확인 후 `feature/M1-03-item-registration` 생성.
- 범위: TASKS M1-03, PRD M-02·AC-02-1~7·U-01·U-02 일부, API 4.5·6.4·6.5, ARCH 4.4 V2·11장.

## 승인한 선택과 대안

| 항목 | 선택과 이유 | 비교한 대안 |
|---|---|---|
| 제한 | 1~10장, 장당 10,000,000바이트, 요청 전체 101,000,000바이트. JPEG·PNG·WebP. EXIF 원본 유지 | 5장·5MB·JPEG/PNG는 출품 자료와 형식 지원을 줄인다 |
| 본인 최저 낙찰가 조회 | 조회 DTO에도 필드 없음. 현재 응답 구조 유지 | 본인 목록에 추가하면 사후 확인은 쉽지만 계약·노출 예외가 필요하다. ADMIN은 M1-04 미결 |
| 수정·재신청 | 수정·철회 API 없음. 반려 이력은 보존하고 새 Item으로 재등록 | 같은 Item의 수정·재신청은 새 계약과 상태 전이가 필요 |
| 입력 | 이름 1~100 코드 포인트(공백만 거절), 설명 2,000까지, 두 가격 모두 최대 10억 원 | 최저가 long 전체 범위는 프론트 BigInt/문자열 정수 처리가 필요 |
| 시그니처 | 세 형식 직접 판별, 고정 길이 헤더만 읽음. 새 의존성 없음 | tika-core 4.1.0은 검증된 탐지 구현이나 지원 범위·의존성이 늘어난다. Boot 4.1.2 BOM 비관리. https://tika.apache.org/download.html |
| 저장 위치·키 | local 기본 .local-data/images, ITEM_IMAGE_STORAGE_ROOT 재정의. prod 명시 경로·named volume. UUID v4의 32자리 소문자 hex, CREATE_NEW | 모든 환경 절대경로 필수는 설정 부담. SHA-256 키는 공유 파일의 소유·삭제 정책이 필요 |
| 파일과 DB | 파일을 모두 저장한 뒤 별도 application @Transactional에서 Item·이미지 메타데이터 저장. 커밋 후 응답 | DB 먼저는 파일 실패 때 깨진 참조. 상태 테이블·비동기 작업자는 복구 가능하지만 범위·계약 증가 |
| 실패 정리 | 확정 롤백·트랜잭션 시작 전 실패만 파일 삭제. COMMITTED/UNKNOWN이면 보존 | 예외마다 삭제하면 실제 커밋된 이미지가 사라질 수 있다 |
| 413 전달 | servlet 파일·요청 한도와 Tomcat max-swallow-size=-1, API 1.4 JSON | 유한 swallow 한도는 더 큰 요청에서 연결 종료로 JSON 수신을 방해할 수 있다. 무제한 소비는 거절 본문의 네트워크 I/O 비용을 낸다 |

## 경계와 실패 상황

- 서블릿이 받은 MultipartFile을 api에서 스트림 공급자로 바꾼다. domain/storage 포트는 Spring MultipartFile을 모른다. 파일 복사는 DB 트랜잭션 밖에서 한다. ID는 JWT sub만 사용한다.
- 저장이 완료되어도 커밋된 item_image 메타데이터가 없으면 GET으로 공개하지 않는다. Content-Type은 검사 결과만 사용하며 nosniff·immutable 캐시를 붙인다.
- 시그니처는 형식 식별이며 전체 디코딩·완전한 손상 검출·재인코딩 보장이 아니다. 파일명과 클라이언트 Content-Type은 신뢰하지 않는다.
- 일부 저장 실패·확정 롤백은 이번 요청이 생성한 파일만 보상 삭제한다. 정리 자체 실패는 식별 가능한 키로 기록한다. 요청 데이터·최저가·토큰은 로그에 넣지 않는다.
- 프로세스가 파일 저장 후 죽거나 커밋 결과가 UNKNOWN이면 고아 파일이 남을 수 있다. 자동 정리 작업은 없다. 반대로 외부 삭제·볼륨 손실·전원 장애의 DB/파일시스템 원자성은 보장하지 않는다.
- 커밋 성공 뒤 응답이 유실되면 DB·파일은 유지된다. POST 등록은 멱등 계약이 없어 자동 재시도하지 않는다. 사용자가 다시 등록하면 중복 Item이 생길 수 있다.
- 등록 시각은 주입 Clock의 값이다. 역행하면 나중에 등록한 Item이 이전 시각으로 정렬될 수 있다. 커서는 createdAt·id로 동률을 처리하며 페이지 사이 고정 스냅샷을 제공하지 않는다.
- V1~V5는 그대로이며 새 마이그레이션·의존성·이벤트 변경은 없다. 검수 단계 뒤의 stage 계산은 M1-07·M2에서 확장한다. 거래 FK는 기존 M3-07 후속 그대로다.
- 공개 응답 계약 장치는 실제 REST 실행 카탈로그와 재귀 JSON 검사로 구성하고 handler 누락을 실패시킨다. 후속 작업은 실제 응답·WebSocket 수신을 등록한다. 미구현 이벤트의 완료를 주장하지 않는다.

실제 검증·실패 재현·직접 확인 방법과 NOT_RUN은 PROGRESS M1-03에 기록한다.

## 후속 작업의 응답 계약 등록

GEN 검증 보완: Swagger nullable 객체 참조는 OpenAPI 3.1 `anyOf [CurrentLot, null]`로 만든다. Orval 8.45.0은 기본 Blob 디코더가 JSON 오류도 Blob으로 읽으므로 getImage에만 `forceSuccessResponse: true`를 적용해 성공은 Blob·오류는 JSON `error.info`와 `status`로 제공한다. 이미지 화면은 계약대로 응답 imageUrl을 img src에 사용한다. 나머지 API는 기존 status/data 반환을 유지하며 생성 파일은 직접 고치지 않는다. 실제 생성 함수의 업로드·조회·404 검증 결과는 PROGRESS에 있다.

- `ResponseContractCatalog.rest(method, path, audience, status, actualCall)`에 실제 MockMvc/HTTP 실행 결과를 등록한다. 실제 제품 handler와 경로 집합을 비교하므로 엔드포인트 추가 후 카탈로그가 빠지면 실패한다. 인증 API와 오류 JSON도 포함하고, 이미지 바이트는 별도 binary 항목으로 검사한다.
- `event(type, audience, actualCapture)`는 WebSocketStompClient 등으로 실제 수신한 JSON을 공급한다. M1-03에는 제품 이벤트 발행자가 없으므로 이벤트 실행·범위 집합 모두 비어 있다. M2 이후 해당 작업이 구현한 이벤트 enum/종류 집합을 `verifyEventCoverage`에 공급해야 한다. 이것은 현재 미구현 이벤트에 대한 실행 증거가 아니다.
- `ReservePriceAssertions`는 모든 중첩 필드 이름과 숫자·문자열 표식을 검사한다. 표식은 시작가·ID·현재가와 겹치지 않는 917431000원이며 별도 실제 저장 행에서 값이 있음을 먼저 확인한다. 시작가와 같은 기본 최저가는 값 부재 검사로 구별할 수 없으므로, 그 사례는 DB 기본값 검증과 필드명 검사로 확인한다.
- 후속: M1-04 운영자 검수 응답·B-7, M1-07 편성 응답, M1-08 회차 상세·Lot 상세, M2 스냅샷·레인 이벤트. 운영자에게 최저가를 허용하는 결정이 생기면 승인된 해당 경로·audience만 좁게 허용하고 공개 경로 검사에는 예외를 두지 않는다.
