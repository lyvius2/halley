# AGENTS.md

Halley — 부동산 매물 비교·평가 웹앱. 2인 전용 폐쇄형 서비스.

## 빌드 · 실행 · 테스트

```bash
./gradlew bootRun              # 로컬 실행 (profile=local)
./gradlew test                 # 단위 테스트
./gradlew test --tests "*Scoring*"   # 특정 테스트만
./gradlew build                # 전체 빌드 (테스트 포함)
```

로컬 실행 전 `docker compose up -d`로 PostgreSQL·Redis 기동 (`compose.yaml` 참조).

## 스택 (고정 — 임의로 바꾸지 말 것)

- Java 25 / Spring Boot 4.1.x / Gradle
- 화면: Mustache(App Shell 전용) + **Alpine.js**(클라이언트 렌더링). React/Vue 등 추가 프레임워크 도입 금지
- DB: **PostgreSQL 단일** + Redis(세션·캐시·rate limit 전용, 영속 데이터 저장 금지)
- 지도: **카카오맵 JS SDK** + 카카오 로컬/Directions/Roadview REST API. 타사 지도 SDK 추가 금지
- 대중교통 경로: ODsay API
- 알림: Slack **Incoming Webhook**, URL은 `application.yaml`의 `${SLACK_WEBHOOK_URL}` 환경변수로 주입 (하드코딩 금지)
- 이미지 저장: 로컬 볼륨. S3 등 외부 스토리지 클라이언트 추가 금지

## 절대 규칙

- **매물 데이터는 네이버 붙여넣기 텍스트 파싱으로만 등록한다.** 네이버 서버를 크롤링하는 코드(Jsoup으로 `fin.land.naver.com`/`new.land.naver.com` fetch 등)를 작성하지 말 것 — 등록 경로는 파싱뿐이며, 크롤링은 오직 "생존 확인 배치"(설계서 12장)에서만 허용된다.
- **채점 로직에 LLM 호출을 넣지 않는다.** 모든 채점은 결정론적 규칙(`scoring/criterion/*Scorer`)이어야 하며, 이유를 코드로 설명할 수 있어야 한다.
- **가격 채점(`PriceScorer`)은 호가 기준.** KB시세(`kb_price`)는 대출 계산에만 쓰고 채점식에 넣지 말 것.
- **국토부 실거래가는 참고 표기 전용.** `PRICE` 채점식에 절대 반영하지 말 것 (설계서 5.5).
- 매매/전세는 **별도 순위표**로 유지한다. 통합 정렬 로직을 만들지 말 것.
- 신규 API 엔드포인트나 외부 연동을 추가하기 전에 `docs/design.md`에 해당 결정이 있는지 먼저 확인한다. 없으면 구현하지 말고 질문할 것.

## 확정 스코어링 규칙 (요약 — 근거는 docs/design.md 5장)

- 층수(숫자): 1~6층 선형 증가(1층=0, 6층=100), **7층 이상은 전부 100점 동점**
- 층수(밴드 저/중/고): 저=0, **중=고=100(동점)**
- 가격: `100 × (1 − 호가 / 예산상한)`, 예산상한 = 활성 사용자 `available_budget`(현금만, 부채 제외) 합 + 매물별 예상 대출한도
- 건물동수: 1동=0(나홀로), 2~4동 선형 증가, 5동 이상 전부 100점. **입력은 수기** (K-apt 연동 없음)
- 가중치: `weight(rank) = 3.0 − (rank−1) × 0.2` (등차, 12개 항목)

## 디렉터리 컨벤션

`docs/DESIGN.md`(이 설계서) 16장 부록의 패키지 구조를 따른다. 새 패키지를 만들기 전 기존 구조에 맞는 위치가 있는지 먼저 확인.

## 코딩 규칙

- **모든 프로덕션 코드에는 JUnit5 테스트를 반드시 작성한다.** 기능 구현·수정 시 해당 단위/통합 테스트를 함께 추가하고, 테스트가 통과하기 전에는 커밋하지 않는다.
- 로깅은 Lombok `@Slf4j` + `log.info`/`log.warn`/`log.error`로만 사용한다. `System.out.println` 금지.

## 테스트 필수 항목

- `ingest/parser/`: `src/test/resources/fixtures/`의 실제 매물 상세 붙여넣기 샘플로 회귀 테스트 (레이아웃 변경 감지용)
- `scoring/criterion/`: 각 Scorer는 경계값(1층/6층/7층, 1동/4동/5동 등) 테스트 필수
- 정규식 파싱 실패는 예외를 던지지 않고 `MISSING`으로 기록하는지 테스트로 검증

## 하지 말 것

- Bing Maps, Google Maps 등 대안 지도 SDK 제안 금지 (검토 후 기각됨 — docs/DESIGN.md 3.1)
- MongoDB 등 추가 DB 도입 금지
- `raw_paste_text`를 외부 서비스(LLM API 등)로 전송하는 코드 작성 금지
- 회원가입 플로우 구현 금지 — 계정은 Admin이 D4에서만 생성

## 참고 문서

- `docs/DESIGN.md` — 전체 설계서(ERD, API 명세, 화면 정의, 결정 이력)
- 이 파일과 상충하면 `docs/DESIGN.md`가 최신 확정 사항이므로 그쪽을 따르고, 이 파일을 갱신할 것
