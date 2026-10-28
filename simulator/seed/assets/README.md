# 시연용 이미지

이 디렉터리의 PNG 네 장은 이 프로젝트를 위해 직접 작성한 도형 일러스트다. 실제 중고 물품 사진이나 상품 상태 증거가 아니며, 각 이미지의 `DEMO` 표시는 시연 자료임을 나타낸다. 외부 사진·이미지·폰트·브랜드 로고를 사용하지 않았다.

- 원본 출처: [`../generate-images.mjs`](../generate-images.mjs)의 직접 작성한 도형과 색상. 외부 원본 URL은 없다.
- 제작·라이선스 결정: M1-10 사용자 승인, 2026-10-29. [ADR-015](../../../docs/adr/ADR-015-M1-10-demo-seed.md).
- 적용 범위: **이 디렉터리의 `camera.png`, `headphones.png`, `keyboard.png`, `lamp.png` 네 일러스트에만 CC0-1.0을 적용한다.** 저장소 전체 또는 PNG 인코더·생성 프로그램 코드의 라이선스를 변경하는 선언이 아니다.
- 라이선스: [CC0 1.0 Universal 설명](https://creativecommons.org/publicdomain/zero/1.0/), [법률 문서](https://creativecommons.org/publicdomain/zero/1.0/legalcode).
- 규격: 640×480, 8-bit RGB PNG. 이미지 파일 자체에도 제목·원본 출처·CC0 식별 메타데이터를 넣는다.

시드는 아래 파일을 로컬에서 읽어 실제 출품 등록 API에 업로드한다. 실행 중 외부 이미지 사이트에 접속하거나 임의의 인터넷 이미지를 받지 않는다.

| 파일 | 내용 | 바이트 | SHA-256 |
|---|---|---:|---|
| `camera.png` | 카메라 | 7,246 | `020cd40f8f47b1a499794e0875daba95a87aef1e5152dab9c8ad8d784fde53ae` |
| `headphones.png` | 헤드폰 | 5,790 | `89bbf98c9fc68fb2b8cb89486ec35b8a1661d1b0a8f911c4f7ee0be4e1609b73` |
| `keyboard.png` | 키보드 | 5,508 | `583e2685285ef72d8a93323f67fd359acdb092516762254d35fadee5e065e430` |
| `lamp.png` | 탁상 조명 | 6,113 | `7bd08f0c16d6cd7d17d415376417f7219a234e4a4e0503da4bfa3a47e9f8165b` |

저장소 루트에서 기존 Node 24로 재생성한다. 추가 npm 패키지는 필요 없다.

```powershell
node simulator/seed/generate-images.mjs
```

생성기는 Node의 `fs`, `crypto`, `zlib`만 사용하며 난수·시각·네트워크 입력을 사용하지 않는다. 동일 Node 24.11.1 환경에서 두 번 생성한 파일의 SHA-256이 위 값과 모두 일치함을 확인했다. PNG 압축 구현이 바뀌는 다른 런타임에서는 화면이 같아도 압축 바이트가 달라질 수 있으므로 이 표는 확인한 실행 환경의 파일을 식별한다. import만 하면 파일을 만들지 않는다.
