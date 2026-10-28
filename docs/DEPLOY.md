# 첫 배포 절차 (M0-05)

M1-03 이미지 저장 추가 (2026-10-28): prod Compose는 app의 `ITEM_IMAGE_STORAGE_ROOT=/app/data/images`와 `item-images` named volume을 사용한다. 이미지의 디렉터리는 uid/gid 10001이 쓸 수 있게 이미지 빌드 시 생성한다. app 컨테이너를 교체해도 같은 Compose 프로젝트의 볼륨을 재사용해야 한다. `down -v`나 볼륨 삭제는 이미지와 DB 데이터를 잃게 하므로 배포·복구 명령으로 쓰지 않는다. DB 백업만으로 이미지가 복구되지는 않는다.

local bootRun 기본 저장 위치는 backend의 `.local-data/images`이고 `ITEM_IMAGE_STORAGE_ROOT`로 바꿀 수 있다. 이 디렉터리는 Git·Docker build context에서 제외한다. 자동 고아 파일 정리는 없다. DB 커밋 결과가 불명확하거나 앱이 파일 저장 뒤 종료되면 파일이 남을 수 있으므로, 앱을 정지한 뒤 `item_image.storage_key`와 파일 목록을 비교하고 DB 연결 상태·완료된 트랜잭션을 확인해 고아 파일만 정리한다. 실행 중 파일·DB를 비교해 즉시 삭제하면 등록 중인 파일을 지울 수 있다. 실제 서버 배포·백업 복원 검증의 완료를 뜻하지 않는다. 결정은 [ADR-008](adr/ADR-008-M1-03-item-registration.md), 실행 증거는 PROGRESS M1-03에 남긴다.

이 문서는 **이미지 태그 하나를 골라 수동 배포**하는 절차다. 현재 세션에서는 이미지·서버 compose·이 문서와 로컬 검증까지만 준비한다. AWS 서버에서 실행한 증거는 아직 없으며, M0-05 전체 완료는 실제 배포 검증 뒤 판정한다. 실행 현황은 [PROGRESS](PROGRESS.md), 결정은 [ADR-005](adr/ADR-005-M0-05-first-deploy.md)가 원본이다.

## 1. 배포 단위와 준비

### M1-01 인증 설정 추가

- M1-01 이미지부터 `JWT_SECRET_BASE64`가 필수다. 32바이트 이상의 무작위 값을 Base64로 만든 후 서버 환경 파일에만 보관한다. 예: 서버 Bash의 `openssl rand -base64 32` 결과를 안전하게 저장한다. 키를 출력한 터미널 기록·채팅·Git에 남기지 않는다. 키 교체는 기존 Access를 무효화하며, 유효한 Refresh로 다시 발급받을 수 있다.
- prod 쿠키는 항상 `Secure; HttpOnly; SameSite=Strict; Path=/api/auth`다. ADR-005의 HTTP·WS 배포 준비만으로 로그인·쿠키·탭 간 Web Locks 동작을 검증했다고 볼 수 없다. **운영 인증에는 HTTPS가 필요하다.** TLS 구성과 실제 서버 인증 검증은 아직 NOT_RUN이다.
- ADMIN은 가입 API로 만들 수 없다. `admin-seed` 프로필을 명시적으로 켤 때만 환경변수 `ADMIN_SEED_EMAIL`, `ADMIN_SEED_PASSWORD_HASH`를 사용한다. 해시는 `{bcrypt}$2…` 형식이며 저장소에 넣지 않는다. 기존 ADMIN은 그대로 두고 기존 USER 승격·기존 비밀번호 덮어쓰기는 거절한다. 정상 앱 기동에는 이 변수가 필요 없다.
- 서버에서 실행할 때는 먼저 app을 stop하고 다음 명령으로 별도 비웹 프로세스를 실행한 뒤 정상 app을 start한다. 이미지·DB·환경 파일과 기존 승인 경계는 아래 배포 절차와 같다. 아래는 절차이며 실제 서버 실행 증거가 아니다.

```bash
# 이미 안전하게 설정한 ADMIN_SEED_EMAIL, ADMIN_SEED_PASSWORD_HASH를 환경으로 전달한다.
docker compose -p fourlane-prod -f docker-compose.prod.yml --env-file /opt/fourlane/.env stop app
docker compose -p fourlane-prod -f docker-compose.prod.yml --env-file /opt/fourlane/.env run --rm --no-deps \
  -e SPRING_PROFILES_ACTIVE=prod,admin-seed -e SPRING_MAIN_WEB_APPLICATION_TYPE=none \
  -e ADMIN_SEED_EMAIL -e ADMIN_SEED_PASSWORD_HASH app
docker compose -p fourlane-prod -f docker-compose.prod.yml --env-file /opt/fourlane/.env start app
```

`member_warning`은 V5에 먼저 생성됐다. 이후 M3-07의 trade 생성 마이그레이션은 이 테이블을 다시 만들지 않고 `fk_warning_trade`를 반드시 추가해야 한다 (ADR-006).

- 서버 제안: 서울 `ap-northeast-2`, EC2 `t3.medium`(x86_64, 4GiB). 측정용 서버는 M4-03에서 따로 정한다. Linux x86_64와 Docker Engine·Compose v2가 필요하다. 아래 서버 명령은 Bash 기준이다.
- 서비스는 app·mysql 두 개다. app 메모리 상한 1,536MiB와 JVM `-Xms256m -Xmx768m`, mysql 상한 1,536MiB와 buffer pool 512MiB를 초기 예산으로 둔다. 실제 EC2 자원 확인은 나중에 한다.
- SPA·REST `/api/**`·WebSocket `/ws`는 앱이 한 출처에서 제공한다. 공개 웹 포트는 기본 TCP 80 하나다. MySQL 3306·관리 8081은 publish하지 않고 보안 그룹에서도 허용하지 않는다. 서버 관리 접속은 사용자가 마련한다.
- **TLS는 M6-01로 연기했다.** 이번 주소는 `http://<host>`와 `ws://<host>/ws`다. TASKS M0-05의 HTTPS 예시는 이번 단계에서는 HTTP로 검증한다. 인증·입찰을 포함한 HTTPS·WSS 검증은 수행한 것으로 기록하지 않는다.
- AWS 리소스·보안 그룹·도메인/DNS·접속·서버 명령 실행은 사용자가 한다. 에이전트와 진행할 때는 각 단계 결과를 확인한 뒤 다음 단계로 간다. 비밀번호·토큰·.env 원문은 채팅으로 보내지 않는다.
- 이미지 push, Git push, PR 생성, 태그 push는 실행 직전 별도 승인을 받는다. 이 문서의 명령은 그 자체로 원격 쓰기 승인이 아니다. PR 머지는 사용자가 한다.

## 2. 이미지 태그와 소스 대응

| 용도 | 선택할 이미지 태그 | 빌드할 Git 소스 |
|---|---|---|
| M0-05 절차 검증 | `sha-<40자리 커밋 SHA>` | feature/M0-05-first-deploy의 검증할 커밋 |
| M0 정식 배포 | `v0.1.0` | release/0.1.0을 main에 머지한 뒤 생성한 실제 Git 태그 v0.1.0 |
| 이후 배포 | `sha-<전체 SHA>` 또는 정식 버전 태그 | 해당 커밋 또는 Git 태그 |

- 저장소는 `ghcr.io/songstone/four-lane-auction`이다. `latest`·`dev` 같은 이동 태그는 사용하지 않고 같은 태그를 덮어쓰지 않는다. 레지스트리가 태그 변경을 자동으로 막아 준다는 뜻은 아니다.
- Dockerfile은 `org.opencontainers.image.revision`에 원본 커밋, `version`에 이미지 태그, `source`에 GitHub 저장소를 기록한다. 배포 때 이 값과 pull한 digest를 확인·보관한다.
- feature 이미지의 이름만 v0.1.0으로 바꾸지 않는다. 정식 배포는 실제 v0.1.0 Git 태그의 소스로 다시 빌드한다. 이 작업은 release 브랜치·Git 태그를 만들지 않는다.
- 문서 증거만 추가한 후속 커밋이 있어도 이미지의 원본 SHA는 바꾸지 않는다. 실제 이미지가 만들어진 소스 커밋을 기록한다.

## 3. 로컬 검사와 이미지 생성 (Windows PowerShell)

저장소 루트에서 시작한다. JDK 21·Node 24·npm 11 이상·실행 중인 Docker가 필요하다. 아래 각 명령은 성공(exit 0)을 확인하고 다음 명령으로 간다. Dockerfile 내부 빌드는 별도 Testcontainers 실행을 대신하지 않는다.

```powershell
$env:JAVA_HOME = 'C:\Users\jitin81\.jdks\corretto-21.0.10' # 자신의 JDK 21 경로
Set-Location backend
./gradlew.bat --no-daemon clean build --no-build-cache --console=plain
Set-Location ..
python .github/scripts/verify-test-results.py
Set-Location frontend
npm ci
npm run lint
npm run typecheck
npm run build
Set-Location ..
docker info
```

Ubuntu 등에서는 `./gradlew`를 쓴다. `build`에는 UTC test·서울 testSeoul·시간 출처 검사가 포함된다. 기존 bootRun·Vite가 파일을 열어 두었다면 해당 checkout의 프로세스만 종료하고 검사 후 필요하면 복원한다.

검사 후 변경을 의미 있는 커밋으로 만들고 작업 트리가 clean인지 확인한다. 소스와 label의 SHA가 어긋나지 않게 더티 작업 트리의 배포 이미지는 만들지 않는다.

```powershell
git status --short
if (git status --porcelain) { throw 'Commit the intended source before building the deployment image.' }
$revision = (git rev-parse HEAD).Trim()
$imageTag = "sha-$revision"
$image = "ghcr.io/songstone/four-lane-auction:$imageTag"
docker build --platform linux/amd64 --build-arg "VCS_REF=$revision" --build-arg "IMAGE_VERSION=$imageTag" --tag $image .
if ($LASTEXITCODE -ne 0) { throw 'Image build failed.' }
docker image inspect --format '{{.Id}} {{.Architecture}} {{.Config.User}} {{json .Config.Labels}}' $image
```

성공하면 linux/amd64, 사용자 `10001:10001`, 선택한 revision·version인지 확인한다. Node는 빌드 단계에서만 쓰고 Vite dist를 JAR에 포함한다. 로컬 resources/static에는 출력하지 않는다.

로컬 생성만으로 GHCR에 게시되지 않는다. 이미지 push는 다음 절의 별도 작업이며 실제 실행 여부는 PROGRESS에 남긴다.

### 로컬에서 생성한 이미지를 직접 확인할 때

이미 로컬에 생성된 이미지 태그는 PROGRESS의 이미지 기록에서 확인한다. 서버를 만들기 전에 확인할 수 있다. 샘플 복사는 최초 한 번만 하고 기존 설정 파일은 유지한다.

```powershell
New-Item -ItemType Directory -Force output | Out-Null
if (-not (Test-Path output/local-image.env)) {
    Copy-Item deploy/.env.example output/local-image.env
}
notepad output/local-image.env
```

IMAGE_TAG에는 로컬 이미지의 태그, DB_PASSWORD·MYSQL_ROOT_PASSWORD에는 새 로컬 검증용 비밀번호를 지정한다. APP_BIND_ADDRESS는 `127.0.0.1`, APP_HTTP_PORT는 미사용 포트 `18080`, WS_ALLOWED_ORIGINS는 `http://127.0.0.1:18080`으로 바꾼다. 나머지 DB 값은 샘플대로 쓰며 서버 비밀값은 사용하지 않는다.

```powershell
docker compose -p fourlane-image-check -f docker-compose.prod.yml --env-file output/local-image.env up -d --wait --wait-timeout 180
curl.exe --fail --show-error --include 'http://127.0.0.1:18080/api/sessions?status=RUNNING'
docker compose -p fourlane-image-check -f docker-compose.prod.yml --env-file output/local-image.env exec -T app curl --fail --silent http://127.0.0.1:8081/actuator/health
```

브라우저 `http://127.0.0.1:18080/`에서 홈·빈 상태 3개와 두 연결 배지를 확인한다. 이후 해당 임시 프로젝트만 종료한다. `down`은 named volume을 유지하므로 같은 프로젝트를 다시 쓸 때는 DB 비밀번호도 유지한다. 기존 로컬 개발 compose는 별도다.

```powershell
docker compose -p fourlane-image-check -f docker-compose.prod.yml --env-file output/local-image.env down
```

### 나중에 정식 릴리스를 배포할 때

사용자가 release PR을 머지하고 Git 태그 v0.1.0을 준비한 뒤에만 실행한다. 작업 트리가 clean인지 확인한 뒤 소스를 선택한다.

```powershell
git fetch origin --tags
git switch --detach v0.1.0
$revision = (git rev-parse 'v0.1.0^{commit}').Trim()
$imageTag = 'v0.1.0'
$image = "ghcr.io/songstone/four-lane-auction:$imageTag"
docker build --platform linux/amd64 --build-arg "VCS_REF=$revision" --build-arg "IMAGE_VERSION=$imageTag" --tag $image .
if ($LASTEXITCODE -ne 0) { throw 'Release image build failed.' }
docker image inspect --format '{{index .Config.Labels "org.opencontainers.image.revision"}}' $image
```

## 4. GHCR push (별도 승인 후)

처음에는 사용자가 `write:packages` 권한의 GitHub PAT classic을 준비한다. 토큰은 명령 인수·채팅·저장 로그에 쓰지 않는다. PowerShell에서 숨김 입력하고 표준 입력으로 Docker에 전달한다.

```powershell
$registrySecret = Read-Host 'GHCR token (write:packages)' -AsSecureString
$registryCredential = [System.Net.NetworkCredential]::new('', $registrySecret)
$registryCredential.Password | docker login ghcr.io --username songstone --password-stdin
if ($LASTEXITCODE -ne 0) { throw 'Registry login failed.' }
Remove-Variable registryCredential,registrySecret
docker push $image
if ($LASTEXITCODE -ne 0) { throw 'Image push failed.' }
docker image inspect --format '{{json .RepoDigests}}' $image
docker logout ghcr.io
```

최초 패키지는 private이다. 사용자가 GitHub package settings에서 저장소 연결과 공개 범위를 확인하고 승인한 **public**으로 바꾼다. 소스 저장소가 public이어도 패키지는 자동으로 public이 되지 않는다. 서버에는 GHCR용 PAT를 두지 않고, 로그인하지 않은 서버에서 pull 가능한지 확인한다.

push 결과의 digest·이미지 태그·원본 SHA를 기록한다. 기존 태그에 다시 push하지 않는다. 코드 push·dev 대상 PR 생성도 별도 승인 후 하며 PR은 사용자가 머지한다.

## 5. 서버 준비와 설정 (사용자 실행)

여기부터는 **이번 세션 NOT_RUN**이다. AWS 서버를 준비한 뒤 각 단계 결과를 확인한다. 서버에서 빌드하지 않는다. Docker·Compose 설치와 서버 접속은 사용자가 담당한다.

이미지 소스와 같은 버전의 파일 두 개를 사용자 접속 수단으로 서버에 전달한다. 다음 PowerShell 예시의 `<user>`·`<server>`는 실제 값으로 바꾼다.

```powershell
scp docker-compose.prod.yml deploy/.env.example '<user>@<server>:/tmp/'
```

서버 Bash:

```bash
set -eu
docker version
docker compose version
uname -m
free -m
df -h /var/lib/docker
sudo install -d -m 0750 -o "$(id -un)" -g "$(id -gn)" /opt/fourlane
install -m 0644 /tmp/docker-compose.prod.yml /opt/fourlane/docker-compose.prod.yml
if [ ! -f /opt/fourlane/.env ]; then
  install -m 0600 /tmp/.env.example /opt/fourlane/.env
fi
nano /opt/fourlane/.env
```

`uname -m`은 x86_64여야 한다. 기존 .env는 덮어쓰지 않는다. 환경변수를 바꾸면 이후 Compose 명령에서 다시 읽는다.

| 변수 | 서버에서 지정할 값 |
|---|---|
| IMAGE_TAG | push·공개를 마친 이미지 태그 하나 |
| MYSQL_DATABASE | DB 이름. DB_URL의 DB 이름과도 일치시킨다 |
| DB_USERNAME | root가 아닌 앱 전용 사용자 |
| DB_PASSWORD / MYSQL_ROOT_PASSWORD | 각각의 비밀값. 빈 값은 허용하지 않는다 |
| DB_URL | 샘플의 내부 호스트 mysql:3306을 사용하고 DB 이름을 맞춘다. MySQL 공개 IP를 쓰지 않는다 |
| WS_ALLOWED_ORIGINS | 실제 브라우저 Origin 하나. 예: http://auction.example.com. 끝 슬래시·경로·와일드카드 없음 |
| APP_BIND_ADDRESS / APP_HTTP_PORT | 서버는 0.0.0.0 / 80. 로컬 검증 때만 127.0.0.1 / 미사용 포트로 바꿀 수 있음 |

비밀값의 `$` 등이 Compose에 의해 보간되지 않도록 .env의 비밀번호 값은 작은따옴표로 감싼다. 작은따옴표를 포함하지 않는 충분한 길이의 무작위 값을 쓰면 편집하기 쉽다. DB_URL은 Compose가 문자열로 읽으므로 셸에서 `source .env`를 실행하지 않는다.

```bash
chmod 600 /opt/fourlane/.env
dc() {
  docker compose -p fourlane-prod -f /opt/fourlane/docker-compose.prod.yml --env-file /opt/fourlane/.env "$@"
}
dc config --quiet
dc config --services
dc config --images
```

서비스 출력은 app·mysql 두 개여야 한다. `config --quiet`는 비밀값을 표시하지 않고 문법과 필수 변수를 확인한다. 인수 없는 `config` 출력이나 `docker inspect` 전체 출력에는 비밀값이 들어가므로 공유하지 않는다. 새 셸에서는 dc 함수를 다시 정의한다. 로컬 docker-compose.yml을 `-f`에 추가하지 않는다.

## 6. pull·MySQL 준비·stop → start

최초 배포와 갱신 모두 같은 프로젝트 이름 `fourlane-prod`를 쓴다. 이미지는 기존 app을 멈추기 전에 pull한다.

```bash
dc pull
APP_IMAGE=$(dc config --images | sed -n '\#^ghcr.io/songstone/four-lane-auction:#p')
test -n "$APP_IMAGE"
EXPECTED_REVISION='<빌드 기록의 40자리 SHA로 교체>'
ACTUAL_REVISION=$(docker image inspect --format '{{index .Config.Labels "org.opencontainers.image.revision"}}' "$APP_IMAGE")
test "$ACTUAL_REVISION" = "$EXPECTED_REVISION"
docker image inspect --format '{{.Architecture}} {{index .Config.Labels "org.opencontainers.image.version"}} {{json .RepoDigests}}' "$APP_IMAGE"
dc up -d --wait --wait-timeout 180 mysql
dc ps
```

SHA 일치와 MySQL healthy를 확인한 뒤 app을 바꾼다. MySQL 환경변수는 최초 볼륨 초기화용이며 .env의 비밀번호만 바꿔서는 기존 DB 사용자 비밀번호가 바뀌지 않는다.

```bash
dc stop app
test -z "$(dc ps --status running --quiet app)"
dc up -d --no-deps --wait --wait-timeout 180 app
dc ps
dc exec -T app curl --fail --silent --show-error http://127.0.0.1:8081/actuator/health
curl --fail --show-error --include 'http://127.0.0.1/api/sessions?status=RUNNING'
```

app·mysql은 healthy, 내부 health는 UP, API는 200이어야 한다. 단일 인스턴스 가드는 M3-09에서 구현하므로 현재는 없다. 같은 DB를 쓰는 별도 프로젝트나 앱을 동시에 띄우지 않는다.

```bash
date -u +'%Y-%m-%dT%H:%M:%SZ' > /opt/fourlane/deployment.txt
docker image inspect --format '{{index .Config.Labels "org.opencontainers.image.version"}} {{index .Config.Labels "org.opencontainers.image.revision"}} {{json .RepoDigests}}' "$APP_IMAGE" >> /opt/fourlane/deployment.txt
docker inspect --format '{{.Name}} {{json .HostConfig.PortBindings}}' "$(dc ps --quiet app)"
docker inspect --format '{{.Name}} {{json .HostConfig.PortBindings}}' "$(dc ps --quiet mysql)"
docker stats --no-stream "$(dc ps --quiet app)" "$(dc ps --quiet mysql)"
```

비밀값 없이 태그·SHA·digest·실행 일시를 기록한다. app은 80 하나만 publish하고 mysql은 publish하지 않아야 한다. stats는 자원 상태 확인이며 성능 목표 측정이 아니다.

## 7. 외부 검증과 M0-05 판정

서버 외부 PC에서 실행한다. `<host>`에는 실제 DNS 이름 또는 IP를 넣는다.

```powershell
curl.exe --fail --show-error --include 'http://<host>/api/sessions?status=RUNNING'
curl.exe --include 'http://<host>/actuator/health'
Test-NetConnection -ComputerName '<host>' -Port 80
Test-NetConnection -ComputerName '<host>' -Port 8081
Test-NetConnection -ComputerName '<host>' -Port 3306
```

| 확인 | 기대 결과 |
|---|---|
| REST | 200, 계약 형식의 JSON. 초기 DB의 items는 빈 배열 |
| 홈 | http://<host>/ 에 홈·진행 중/예정/종료의 빈 상태·REST와 WebSocket 녹색 배지 |
| WebSocket | 브라우저 Network의 /ws는 101, STOMP CONNECTED 수신. 101만으로 완료 처리하지 않음 |
| 공개 앱의 /actuator/health | 404, API 에러 형식 |
| 외부 TCP | 80 성공, 8081·3306은 TcpTestSucceeded=False |
| 설정 | prod, Origin 일치, 비밀값은 서버 환경변수만 사용, 관리·MySQL publish 없음. SG에서도 두 포트 미허용 |
| 재배포 | 필요할 때 같은 stop→start로 실행하고 이전 app 정지 후 새 app만 실행. 복구 엔진 검증은 아님 |

각 결과·실행 일시·배포한 태그/SHA/digest·화면 확인을 PROGRESS에 추가한다. 로컬 컨테이너 성공을 EC2·DNS·SG·외부 TLS 성공으로 취급하지 않는다. 이번에 미룬 항목은 NOT_RUN으로 남긴다. M0 완료 기준 6·7은 나중에 v0.1.0을 실제 배포해 확인한다.

## 8. 실패 진단과 이전 이미지 재배포

```bash
dc ps -a
dc logs --tail 100 app
dc logs --tail 100 mysql
docker inspect --format '{{.State.Status}} healthy={{if .State.Health}}{{.State.Health.Status}}{{end}} oom={{.State.OOMKilled}} exit={{.State.ExitCode}}' "$(dc ps --all --quiet app)"
```

로그는 직접 확인하고 공유할 때 비밀값·접속 정보를 가린다. DB 연결·Flyway·Origin·health 실패를 확인한다. health 대기 실패를 무시한 채 배포 성공으로 처리하지 않는다. Compose는 unhealthy 상태만으로 재시작하지 않는다.

이전 호환 이미지로 되돌릴 때는 nano로 .env의 IMAGE_TAG를 이전 태그로 바꾸고 6절의 EXPECTED_REVISION도 그 SHA로 바꾼다. 6절의 pull → DB 확인 → app stop → app start와 7절의 확인을 반복한다. 실패한 새 앱도 stop한 뒤 이전 앱을 띄운다.

MySQL 데이터는 named volume에 남는다. `down -v`나 볼륨 삭제를 복구 절차로 쓰지 않는다. Flyway가 적용한 스키마는 이미지 변경으로 되돌아가지 않으므로 이전 이미지의 스키마 호환성을 확인한다. DB 백업·복원과 릴리스 자동화를 실행한 것으로 기록하지 않는다.

## 9. 로컬 개발

기존 흐름을 유지한다. 루트의 `docker compose up -d`는 mysql만 띄운다. backend의 bootRun과 frontend의 npm run dev를 쓰고 로컬 개발에 prod compose를 겹치지 않는다.

## 10. 공식 자료

- [Docker Compose 명령과 파일 지정](https://docs.docker.com/reference/cli/docker/compose/), [up --wait](https://docs.docker.com/reference/cli/docker/compose/up/), [필수 환경변수와 $ 이스케이프](https://docs.docker.com/reference/compose-file/interpolation/).
- [GHCR 공개·PAT·image label·digest](https://docs.github.com/en/packages/working-with-a-github-packages-registry/working-with-the-container-registry).
- [Docker Hub Node](https://hub.docker.com/_/node), [Corretto](https://hub.docker.com/_/amazoncorretto), [MySQL](https://hub.docker.com/_/mysql). 베이스 이미지 digest는 Dockerfile·compose에 고정한다. 갱신할 때 manifest와 실제 빌드를 확인한다.
