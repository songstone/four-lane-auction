# Four-Lane Auction

1~4개 레인의 중고 물품 경매를 한 화면에서 실시간으로 보고 입찰하는 웹 플랫폼입니다.

로컬 실행에는 JDK 21, Node.js 24, npm, Docker Compose가 필요합니다. 저장소 루트에서 MySQL을 시작합니다. `.env` 파일을 만들 필요는 없습니다.

```sh
docker compose up -d
docker compose ps  # mysql이 healthy가 된 뒤 백엔드 실행
cd backend
./gradlew :app:bootRun
```

Windows PowerShell에서는 JDK 21을 가리키는 `JAVA_HOME`을 설정하고 `./gradlew.bat :app:bootRun`을 실행합니다. 다른 터미널에서 프론트를 시작합니다.

```sh
cd frontend
npm ci
npm run dev
```

화면은 [localhost:5173](http://localhost:5173), 앱은 [localhost:8080](http://localhost:8080), 상태 확인은 [관리 포트 health](http://localhost:8081/actuator/health)입니다. `.env.example`은 공개된 로컬 기본값과 선택적 재정의 항목입니다. Compose의 `.env`는 bootRun이 자동으로 읽지 않으므로, DB 기본값을 바꾸면 같은 값을 bootRun 프로세스 환경변수로도 전달합니다.

문서는 [docs/TASKS.md](docs/TASKS.md), [PRD](docs/PRD.md), [아키텍처](docs/ARCHITECTURE.md), [REST 계약](docs/API.md), [이벤트 계약](docs/EVENTS.md), [진행·검증 기록](docs/PROGRESS.md), [ADR](docs/adr/)에 있습니다. 이 README는 M0-01 초안이며 정식 README는 M6-02에서 작성합니다.
