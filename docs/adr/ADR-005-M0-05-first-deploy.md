# ADR-005: M0-05 첫 배포 이미지와 수동 배포 절차

- 상태: 사용자 승인 (2026-10-27, Asia/Seoul).
- 원본: [TASKS M0-05](../TASKS.md#m0-05-첫-배포), ARCH 1.4·11장, 사용자 보충 지시.
- 시작 SHA: `9f1381f8c2c74658421a4b6c3f718e0bf638f98d`. 최신 `origin/dev`에서 `feature/M0-05-first-deploy` 생성. 기본 checkout, 시작 시 clean.
- 이번 세션 범위 변경도 사용자 승인: **이미지·서버 compose·배포 문서와 로컬 검증까지만 준비한다. 실제 서버 배포·외부 검증은 나중에 한다.** M0-05 전체 완료로 처리하지 않는다.
- AWS 리소스·보안 그룹·DNS·서버 접속과 서버 명령 실행은 사용자가 한다. 에이전트는 결과를 확인하고 다음 단계를 안내한다. 이미지 push·Git push·PR 생성은 각각 별도 승인 후 실행한다. 머지는 사용자가 한다.

## 대안과 결정

| 항목 | 비교한 대안 | 승인한 선택과 이유 |
|---|---|---|
| 서버 | t3.small(2GiB), t3.medium(4GiB), t4g.medium(ARM, 4GiB) | 서울 `ap-northeast-2`의 x86 `t3.medium` 제안. 앱·MySQL을 함께 둘 메모리 여유와 기존 개발 환경의 아키텍처를 기준으로 선택. 실제 생성은 사용자 작업 |
| 메모리 | 2GiB에 작은 힙 / 4GiB에 컨테이너별 상한 | app 1,536MiB, `-Xms256m -Xmx768m`; mysql 1,536MiB, InnoDB buffer pool 512MiB. 나머지 약 1GiB는 OS·Docker 용도. 초기 배치 예산이며 성능 보장이나 측정 결과가 아니다 |
| 이미지 전달 | GHCR / ECR / Docker Hub / 서버에서 빌드 | 공개 GHCR. 서버는 익명 pull. 최초 패키지 공개 전환이 필요하며 공개 여부도 push 승인 단계에서 확인한다. 서버에서 빌드 자원을 쓰지 않는다 |
| 배포 실행 | 수동 / Actions | 로컬 수동 빌드·승인 후 push, 서버 수동 pull·stop·start. Actions 자동 배포는 M6-01 |
| 이미지 식별 | 이동하는 latest / SHA / Git release tag | 검증은 `sha-<전체 SHA>`, 정식 배포는 실제 Git 태그 `v0.1.0` 소스로 만든 `v0.1.0`. 같은 태그를 덮어쓰지 않는 운영 규칙. OCI revision/version과 배포 digest 기록으로 소스·이미지 연결 |
| SPA | 앱 서빙 / 프록시 컨테이너 | 이미지 빌드에서만 Vite dist를 JAR의 static에 포함. 프록시 컨테이너를 더하면 두 서비스 제한과 충돌하므로 이번에는 앱 서빙 |
| TLS | 지금 앱에서 종료 / M6-01로 연기 | M6-01로 연기. 이번 검증 절차는 HTTP·WS. TASKS의 HTTPS curl 예시를 HTTP로 실행하는 단계 차이를 PROGRESS에 명시한다 |
| compose | 별도 파일 / profile | 독립 `docker-compose.prod.yml`과 명시적 `-p`·`-f`·`--env-file`. 로컬 compose와 병합하지 않아 MySQL publish가 상속되지 않는다 |

## 구현 경계

- 서버 compose 서비스는 app·mysql 두 개다. Prometheus·Grafana는 M4-01, 단일 인스턴스 가드는 M3-09다. 이 ADR은 CH-09 복구 후보나 CH-11 측정 방식을 선택하지 않는다.
- 웹 공개 포트는 기본 80 하나다. 관리 8081은 컨테이너의 127.0.0.1에 바인딩하고 publish하지 않는다. MySQL 3306도 publish하지 않는다. 보안 그룹 차단과 외부 접속 실패는 실제 서버 검증 때 확인한다.
- prod의 DB 정보·WebSocket Origin은 서버 환경변수로만 공급한다. 빈 필수값은 Compose 해석 때 거부한다. 서버 .env는 커밋하지 않는다. 샘플 비밀번호는 비워 둔다.
- 이미지 실행은 uid/gid 10001이고, Java가 PID 1이다. Corretto 베이스의 curl로 컨테이너 내부 health를 검사한다. 새 Java/npm 의존성은 없다. 컨테이너 health 확인 도구의 필요성은 계획 승인에 포함됐다.
- 기존 app을 stop하고 정지 확인 후 새 app을 start한다. 구 이미지 재배포도 같은 순서다. 이미지 되돌리기는 DB 마이그레이션을 되돌리지 않는다.
- bootRun·npm run dev·로컬 compose·API·EVENTS 계약·스키마는 바꾸지 않는다. SPA의 미래 하위 라우트 fallback은 현재 홈 연결 확인 작업 범위에 넣지 않는다. API 404는 JSON으로 유지한다.
- 로컬 이미지 검증과 AWS 검증을 구분한다. 서버 미구성 상태에서 보안 그룹·외부 접속·EC2 메모리 확인을 PASS로 적지 않는다. 측정 인스턴스와 성능 설정은 M4-03에서 정한다.

## 확인한 공식 자료

- [EC2 사양](https://docs.aws.amazon.com/ec2/latest/instancetypes/gp.html), [서울·도쿄 리전](https://docs.aws.amazon.com/global-infrastructure/latest/regions/aws-regions.html).
- [GHCR 인증·최초 private·공개 익명 pull·OCI label](https://docs.github.com/en/packages/working-with-a-github-packages-registry/working-with-the-container-registry), [ECR 인증](https://docs.aws.amazon.com/AmazonECR/latest/userguide/registry_auth.html).
- [Compose 단일 파일·프로젝트 이름](https://docs.docker.com/reference/cli/docker/compose/), [필수 환경변수 보간](https://docs.docker.com/reference/compose-file/interpolation/), [Compose health 대기](https://docs.docker.com/reference/cli/docker/compose/up/).
- [Spring Boot 정적 리소스](https://docs.spring.io/spring-boot/reference/web/servlet.html).
- Node·Corretto 이미지 digest는 2026-10-27 `docker buildx imagetools inspect`로 공식 Docker Hub manifest를 조회해 Dockerfile에 고정했다. MySQL digest는 로컬 승인 버전을 유지했다. 실제 빌드의 버전과 검증 결과는 PROGRESS에 기록한다.

명령은 [DEPLOY](../DEPLOY.md), 실제 실행과 NOT_RUN은 [PROGRESS](../PROGRESS.md)에 기록한다.
