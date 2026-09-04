# Halley 아키텍처·서비스 품질 감리 보고서

> 감리일: 2026-09-03  
> 범위: 저장소의 설계 문서, 프로덕션 코드, 테스트, 설정, 정적 리소스 및 로컬 테스트 실행 결과  
> 제외: 운영 DB 데이터, 실제 배포 인프라, 외부 API 실호출, 브라우저 전 구간 수동 사용성 시험

## 1. 한 줄 결론

Halley는 **개인 프로젝트로 보기 어려울 만큼 문제 정의와 실패 대응이 잘 설계된 서비스**다. 특히 채점 근거, 외부 API 장애 처리, 그룹 격리 의도, 비동기 보정 흐름은 수준이 높다. 다만 현재는 기능 확장이 구조 정리를 앞질렀고, **그룹 접근 통제 누락, 세션 기반 서비스의 CSRF 비활성화, 깨진 회귀 테스트**가 있어 “안심하고 오래 쓰는 서비스”라고 판정하기에는 이르다.

종합 판정은 다음과 같다.

| 영역 | 평가 | 판단 |
|---|---:|---|
| 제품·도메인 설계 | 8/10 | 실제 신혼집 의사결정 과정을 매우 구체적으로 모델링했다 |
| 백엔드 아키텍처 | 7/10 | 경계는 좋으나 일부 서비스가 비대하고 접근 통제가 일관되지 않다 |
| 테스트·재현성 | 6/10 | 테스트 양과 경계값 검증은 좋지만 현재 기본 테스트가 통과하지 않는다 |
| 보안·개인정보 | 4/10 | 폐쇄형 서비스의 핵심인 객체 단위 인가와 CSRF에 즉시 보완이 필요하다 |
| 프런트엔드 유지보수성 | 5/10 | Alpine.js 선택은 적절하나 단일 5천 줄 파일은 이미 분리 시점을 넘었다 |
| 운영성 | 6/10 | 장애·캐시·로그 고려는 좋지만 스키마 배포와 빌드 재현성에 수작업 위험이 있다 |
| 문서화 | 7/10 | 결정 근거는 매우 풍부하나 현행 규칙과 역사가 한 문서에 섞여 탐색 비용이 크다 |

**종합: 6.5/10, “좋은 설계의 고급 프로토타입이지만 보안과 구조 정리 전에는 운영 완성품으로 보지 말 것.”**

## 2. 확인한 규모와 검증 결과

- 프로덕션 Java: 516개 파일, 약 30,826줄
- 테스트 트리: 160개 파일, 약 21,910줄
- 프런트엔드: `app.js` 5,142줄, `app.css` 2,375줄, `index.mustache` 단일 App Shell
- 테스트 메서드 계열 어노테이션: 약 866개
- 외부 Feign client: 14개, 대응 `FallbackFactory`: 14개
- Java 프로덕션 코드의 주석성 행: 약 5,756줄(전체의 18.7%)
- `app.js`의 주석성 행: 약 1,015줄(전체의 19.7%)

### 테스트 실행

1. `./gradlew test`
   - 테스트 실행 전 AOT 컴파일 구간에서 Gradle 데몬이 GC thrashing으로 종료됐다.
   - `org.graalvm.buildtools.native` 플러그인이 일반 테스트 경로에 불필요한 AOT 부담을 주는지 확인할 필요가 있다.
2. `./gradlew test -x processTestAot -x compileAotTestJava --no-daemon`
   - 919개 실행, **4개 실패**, 9개 건너뜀
   - 실패 4개는 모두 `KbPriceParsingTest`가 `src/test/resources/fixtures/naver-sanggye-7.txt`를 직접 읽지만 파일이 없어서 발생했다.

따라서 이 감리 시점의 저장소는 **기본 테스트 명령이 성공하지 않으며**, AOT를 우회해도 전체 테스트가 녹색이 아니다.

## 3. 잘된 점

### 3.1 도메인 규칙이 코드에 명시되어 있다

- `PriceScorer`는 호가와 현금·예상 대출한도로만 계산하며 KB시세나 실거래가를 섞지 않는다.
- 층수, 연식, 역세권 등 기준이 각각의 `criterion/*Scorer`로 분리돼 있다.
- `DealType.SALE`과 `DealType.JEONSE`를 쿼리 단계에서 분리해 별도 순위표를 만든다.
- “미측정”을 0점과 구분한다. 외부 API 실패를 나쁜 매물로 오인하지 않게 하는 중요한 설계다.
- 결과에 계산 설명을 남겨 사용자가 점수의 이유를 볼 수 있다.

### 3.2 외부 연동 실패를 본 기능 실패와 분리하려는 설계가 좋다

- Feign client마다 `FallbackFactory`가 존재한다.
- 등록 트랜잭션과 느린 보정 작업을 분리하고, 먼저 매물 카드를 보여 주는 흐름은 UX와 DB 커넥션 보호 측면에서 합리적이다.
- API별 캐시, 동시 실행 제한, 초당 제한, 실패와 빈 결과의 구분을 실제 장애 경험에 근거해 다룬다.
- AI 결과와 전망에 prompt hash, 모델, 출처 등을 남기려는 태도는 사후 검증에 유리하다.

### 3.3 계층 분리는 대체로 일관적이다

- Controller가 Repository나 외부 Adapter에 직접 의존하는 사례는 발견하지 못했다.
- 도메인 계산은 Spring/JDBC와 분리되어 단위 테스트가 가능하다.
- 실제 교체 가능성이 있는 외부 API와 캐시에만 port를 둔 결정은 이 규모에서 과도한 추상화를 피한다.
- 이미지 조회에도 매물 접근 통제를 적용하고 private cache 지시를 둔 것은 세심하다.

### 3.4 테스트의 의도와 폭은 좋은 편이다

- scorer 경계값, 그룹 격리, 외부 응답 변형, 캐시, 비동기 흐름, 이미지 접근 등을 폭넓게 다룬다.
- 대부분 `@DisplayName`을 사용하고 실제 운영 장애를 회귀 시나리오로 남겼다.
- 파서가 잘못된 입력에서 예외를 던지는 대신 `MISSING`을 남기는 테스트가 있다.

이 장점들은 생성 모델이 코드를 썼다는 사실보다 **요구사항과 장애 사례를 꽤 집요하게 코드로 옮겼다**는 점을 보여 준다. 단순한 CRUD 생성물은 아니다.

## 4. 즉시 조치가 필요한 문제

### P0. 그룹 경계를 우회하는 매물 API가 있다

설계는 `PropertyAccessGuard`를 “유일한 길목”으로 선언하지만 다음 사용자 요청 경로는 그 길목을 지나지 않는다.

| API | 현재 흐름 | 영향 |
|---|---|---|
| `POST /api/properties/{id}/rescore` | `ScoringService.rescore()`가 `PropertyRepository.findById()` 직접 호출 | 다른 그룹의 매물 정보와 점수 응답 노출 가능 |
| `POST /api/properties/{id}/scores/recompute` | 위와 동일 | 다른 그룹 매물 재계산 및 응답 노출 가능 |
| `PUT /api/properties/{id}/scores` | `saveManualScores()`가 repository만 확인 | 다른 그룹 매물 점수 변경 가능 |
| `GET/POST /api/properties/{id}/land-use` | `LandUseService`가 guard 없이 캐시/DB 조회·갱신 | 다른 그룹의 토지이용 정보 조회·변경 가능 |
| `GET /api/properties/{id}/llm-recommendation` | 추천 repository/cache를 id로 직접 조회 | 다른 그룹의 AI 평가와 사유 노출 가능 |

매물 id가 순차적인 `Long`이므로 추측도 쉽다. 2인 전용이어도 계정·세션 탈취나 향후 그룹 기능을 고려하면 **가장 먼저 고칠 결함**이다.

권고:

1. 사용자 요청용 public service method의 첫 줄에서 `PropertyAccessGuard.require(propertyId)`를 강제한다.
2. 배경 작업용 메서드는 이름과 가시성을 분리한다. 예: `rescoreAuthorized()`와 package-private `rescoreBackground()`.
3. 각 매물 하위 API에 대해 “다른 그룹 id → 404, DB·캐시 변경 없음” 통합 테스트를 표 형태로 추가한다.
4. Controller에서 임시로 guard를 부르는 방식보다 서비스 경계에서 보장한다. 다른 진입점이 생겨도 안전해야 한다.

### P0. 세션 쿠키 인증인데 CSRF가 완전히 꺼져 있다

`SecurityConfig`는 `.csrf(AbstractHttpConfigurer::disable)`을 사용한다. 이 서비스는 `JSESSIONID` 쿠키 기반이며 매물 삭제, 점수 수정, 그룹 초대·이동, 웹훅 변경, 비밀번호 변경 등 상태 변경 API가 많다. `SameSite=Lax`는 방어층 하나일 뿐 CSRF 방어의 완전한 대체가 아니다.

권고:

- Spring Security의 CSRF 보호를 다시 켜고, SPA가 읽을 수 있는 cookie/header 방식으로 토큰을 전달한다.
- 최소한 모든 POST/PUT/PATCH/DELETE 통합 테스트에 “토큰 없음 거부 / 정상 토큰 허용”을 둔다.
- 로그인 시도 제한도 구현 근거를 찾지 못했다. 외부 공개 주소를 운영한다면 IP와 계정 기준의 제한을 추가한다.

### 확인 완료. LLM 통근 추정은 의도된 예외다

`CommuteScorer`는 전달받은 소요시간을 확정 산식으로 환산한다. ODsay 장애나 할당량 소진 시 `TransitWithLlmFallback`이 LLM으로 소요시간을 추정하고, 그 값을 `COMMUTE` 계산 입력으로 사용하는 것은 서비스 소유자가 확인한 가용성 정책이다. 따라서 이는 결함이나 P0 위반으로 판정하지 않는다.

현재 구현은 추정 결과에 `LLM_ESTIMATE` 출처를 저장하고 이후 ODsay 값으로 교체할 수 있게 해 정책 방향과도 맞는다. 다만 LLM이 점수 자체를 직접 정하거나 다른 결정론적 기준으로 확장되지 않도록 예외 범위를 `AGENTS.md`에 명시했다.

## 5. 높은 우선순위의 품질 부채

### P1. 폐쇄형 서비스인데 회원가입 기본값이 열려 있다

`membership.sign-up.open`의 기본값이 `true`이고 로그인 전 가입 API가 `permitAll`이다. 현재 최신 설계 이력은 가입 토글을 허용하지만, 프로젝트의 최상위 운영 설명은 “2인 전용 폐쇄형”이고 AGENTS 규칙은 가입 플로우 금지를 말한다.

최소 조치는 기본값을 `false`로 두고 운영 환경에서 명시적으로 열 때만 가입이 가능하게 하는 것이다. 더 근본적으로는 `DESIGN.md`, `AGENTS.md`, README 중 어느 정책이 현행인지 한 번 확정해야 한다.

### P1. 회귀 fixture 정책이 서로 충돌하고 테스트를 깨뜨린다

- AGENTS 규칙은 `src/test/resources/fixtures/`의 실제 붙여넣기 샘플로 회귀 테스트하라고 요구한다.
- `NaverListingTextParserTest`는 개인정보 때문에 fixture를 저장소에서 제외하고 없으면 테스트를 skip한다.
- `KbPriceParsingTest`는 같은 종류의 fixture를 파일 경로로 강제해 없으면 fail한다.

권고는 실제 원문을 익명화한 fixture를 저장소에 넣는 것이다. 이름·전화번호·주소·매물번호를 합성값으로 바꾸되 줄 배치와 라벨은 보존하면 개인정보와 재현성을 모두 지킬 수 있다. 모든 fixture loader도 classpath 기반 하나로 통일해야 한다.

### P1. 운영 DB 변경 절차가 수작업 DDL에 의존한다

local은 `schema.sql`을 자동 적용하지만 live는 SQL init이 꺼져 있고 `docs/DDL.sql`, `DDL-repair.sql`을 별도로 관리한다. 스키마 동기화 테스트는 좋은 안전장치지만 배포 순서, 적용 이력, 롤백 여부는 DB 자체가 보장하지 않는다.

2인 서비스라 당장 복잡한 플랫폼은 필요 없지만 최소한 다음은 필요하다.

- 적용 완료 버전을 DB에 기록하는 단순 migration 체계
- 배포 전 백업과 DDL 적용 순서 문서
- PostgreSQL을 대상으로 하는 CI 또는 Testcontainers 통합 테스트

실제로 설계 이력에는 H2와 PostgreSQL의 JSON/JSONB 차이로 운영에서만 난 장애가 기록돼 있다. local H2는 빠르지만 운영 동등성은 낮다.

### P1. 기본 빌드 경로가 지나치게 무겁고 현재 실패한다

일반 `./gradlew test`가 AOT 단계에서 메모리 과부하로 종료됐다. native image를 실제 배포하지 않는다면 GraalVM 플러그인을 기본 빌드에서 제거하거나 별도 profile/task로 분리하는 편이 낫다. native 배포가 목적이라면 Gradle JVM 메모리와 AOT 전용 CI를 명시해야 한다.

### P1. 입력 검증과 인증 방어가 약하다

- Bean Validation starter와 `@Valid` 사용을 찾지 못했다.
- 로그인/가입/비밀번호 변경 DTO에 일관된 길이·형식 제한이 보이지 않는다.
- live DB 접속 정보가 환경변수 미설정 시 `halley/halley`로 동작한다.
- 최초 admin 비밀번호를 INFO 로그에 출력한다. 최초 부팅 편의는 있으나 중앙 로그 수집·백업에 자격증명이 남을 수 있다.

권고는 “운영은 안전한 환경변수가 없으면 기동 실패”하도록 fail-closed로 바꾸고, 요청 DTO 검증과 로그인 제한을 추가하는 것이다.

## 6. 구조와 유지보수성

### 6.1 백엔드

`ScoringService`는 908줄이며 생성자 의존성이 21개다. 이것은 단순히 파일이 길다는 문제가 아니라 다음 책임이 한 객체에 모였다는 신호다.

- 가시 매물 조회와 정렬
- 채점 context 조립
- 수동 점수 정책
- 채점 실행과 저장
- 응답 DTO 조립
- 캐시/version/lock
- 사용자·그룹 표시 정보 조립

권고 분리 예시:

- `ScoringOrchestrator`: 채점 실행·잠금·저장
- `ScoringContextFactory`: 사용자, 통근, POI, 대출 입력 조립
- `ScoreQueryService`: 목록/상세 DTO 조회와 batch loading
- `ManualScoreService`: 수동 입력 허용 정책과 저장

`PropertyController`도 16개 의존성을 가진 API 집합이다. URL은 유지하면서 클래스만 `PropertyQueryController`, `PropertyInsightController`, `PropertyMediaController`, `PropertyScoringController` 정도로 나누면 변경 충돌과 테스트 준비 비용이 줄어든다.

### 6.2 프런트엔드

Alpine.js 선택 자체는 2인용 폐쇄형 서비스에 잘 맞는다. 문제는 프레임워크가 아니라 **모든 상태와 동작이 `app.js` 하나의 거대한 Alpine component에 모인 것**이다.

현재 파일에는 인증, 목록, 지도, 로드뷰, 대출, 실거래, 임장, 관리자, 그룹, 업로드, 폴링이 함께 있다. 화면 하나를 고칠 때 다른 상태를 깨뜨릴 가능성이 계속 커진다.

React/Vue를 도입할 필요는 없다. 다음 ES module 정도면 충분하다.

```text
static/js/
  api.js
  state/session.js
  features/properties.js
  features/map-kakao.js
  features/scoring.js
  features/itinerary.js
  features/admin.js
  app.js
```

서버 상수와 프런트 상수를 수동으로 복제한 `COMPARE_MIN_PROPERTIES`, `PAGE_SIZE` 등은 drift 위험이 있다. 공개 설정 응답이나 서버가 내려 주는 응답 필드로 단일화하는 것이 낫다.

### 6.3 비동기 작업

가상 스레드와 semaphore/rate gate의 구분은 합리적이다. 다만 작업 상태가 인메모리/Redis TTL과 분리된 스레드에 의존하므로 프로세스 재시작 시 “진행 중”과 실제 작업이 어긋날 수 있다. 2인 서비스에서는 메시지 브로커까지 도입할 필요는 없지만, 시작 시 stale job marker를 정리하고 완료/실패 시각을 DB에 남기는 정도는 권장한다.

`ENRICHMENT_MAX_CONCURRENCY=400` 기본값은 외부 공공 API를 보호하는 값으로 보기에는 크다. API별 rate gate가 없는 연동도 포함해 운영 호출량을 계측한 뒤 보수적으로 낮추는 것이 안전하다.

## 7. 주석에 대한 감리 의견

불만은 타당하다. 주석이 단순히 “조금 많은” 정도가 아니라 Java와 JavaScript 모두 약 19%가 주석성 행이고, 다음 패턴이 읽기 흐름을 방해한다.

- 코드 바로 위에 이미 자명한 동작을 긴 이야기로 반복한다.
- 과거 장애의 전체 서사가 서비스 코드에 남아 있다.
- `(설계 Ixxx)`가 거의 모든 작은 결정에 붙어 코드가 문서 색인처럼 보인다.
- 서로 다른 대상을 설명하는 Javadoc 블록이 연속 배치되는 등 생성·편집 잔재가 있다.
- JavaScript 주석에 `<p>`, `<b>` 같은 Javadoc식 HTML이 섞여 있다.

예를 들어 `app.js` 시작부에는 `todayIso()` 설명과 무관한 “주소 밀기” Javadoc이 연속 배치돼 있어 어느 선언을 설명하는지 흐려진다. 이는 모델 생성 코드에 대한 사람의 편집 단계가 부족했다는 신호다.

다만 주석을 일괄 삭제하면 안 된다. 다음은 유지 가치가 높다.

- 보안 경계와 404 선택 이유
- 외부 API의 비정상 응답·quota 특성
- `MISSING`과 0의 차이
- H2/PostgreSQL 타입 차이
- 비동기 트랜잭션 경계
- 수학식과 규제 근거

권고 규칙:

1. 코드에는 **왜 이 선택이 필요한지**만 1~3줄로 남긴다.
2. 장애의 시간순 서사와 뒤집힌 결정은 `DESIGN.md` 또는 별도 ADR로 옮긴다.
3. “코드가 무엇을 하는지”를 다시 말하는 주석은 삭제한다.
4. 설계 id는 보안·법규·외부 계약 같은 추적 필요 지점에만 둔다.
5. 리팩터링 PR은 기능 변경과 주석 정리를 섞지 않는다.

## 8. 문서 거버넌스

`DESIGN.md`는 468KB로 매우 크며 “앞 장은 과거, 16장의 더 큰 번호가 현행”이라는 독특한 규칙을 쓴다. 결정 역사 보존에는 좋지만 새 개발자가 현행을 빠르게 찾기 어렵고, 문서 상단은 I237까지라고 쓰면서 저장소의 최근 코드는 I264를 참조한다.

또한 다음 충돌이 있다.

- AGENTS: PostgreSQL 단일 / DESIGN: local H2, live PostgreSQL
- AGENTS: Slack webhook 환경변수 / DESIGN·코드: 그룹별 DB 저장
- AGENTS: 회원가입 구현 금지 / DESIGN·코드: 설정으로 여닫는 가입 플로우
- AGENTS: 12개 가중치 요약 / DESIGN·README·코드: 14개 채점 항목
- AGENTS: 생존 확인 배치 크롤링 허용 / 최신 DESIGN: 생존 확인 자체 폐지

`DESIGN.md`가 우선이라는 규칙 덕분에 해석은 가능하지만, AI 코딩 도구가 오래된 AGENTS 요약을 그대로 따르면 다시 충돌한다.

권고:

- `CURRENT_ARCHITECTURE.md`: 현행만 10~20페이지 이내로 유지
- `DECISIONS.md` 또는 `adr/`: I번호의 역사 보존
- `AGENTS.md`: 현행 절대 규칙만 자동 검증 가능한 문장으로 갱신
- README: 사용자 기능과 실행법만 유지

## 9. 지도 변경 검토

### 결론

**현재는 카카오맵을 유지하는 것을 권고한다.**  
네이버 지도는 “두 사용자가 실제로 더 편하다고 반복해서 느끼는가”가 확인됐을 때만 변경 후보로 삼고, **Google Maps로는 변경하지 않는 것이 맞다.**

### 9.1 왜 카카오 유지가 합리적인가

Halley의 지도는 단순 배경 지도가 아니다.

- 브라우저 지도와 custom overlay
- 로드뷰
- 주소 검색·좌표 변환
- 지하철역, 학교, 마트, 병원 등 POI 채점
- 자동차 길찾기
- 임장 경로 polyline

코드상 카카오 참조는 프로덕션 Java 17개 파일, 테스트 16개 파일, 프런트 23개 지점에 걸쳐 있다. 외부 연동은 port로 일부 격리돼 있지만 `KakaoLocalPort`, `KakaoDirectionsPort`처럼 이름과 계약 자체가 공급자 전용이고, 프런트 지도·로드뷰는 SDK 객체를 직접 사용한다. 따라서 지도 타일만 바꾸는 작업이 아니라 **채점 입력과 임장 기능을 함께 재검증하는 마이그레이션**이다.

또한 카카오 Local API의 카테고리 그룹 기반 반경 검색은 현재 scorer 입력과 직접 맞는다. 시각적 취향만으로 이를 교체하면 사용자 가치보다 회귀 위험이 크다.

### 9.2 네이버 지도는 언제 고려할 만한가

다음 조건이 모두 맞으면 네이버를 검토할 수 있다.

- 혜미님과 사용자가 같은 지역·단지에서 카카오보다 네이버 지도/거리뷰를 지속적으로 더 정확하고 편하다고 평가한다.
- 월 과금과 호출량을 받아들일 수 있다.
- 카카오 POI 결과를 네이버 지도 위에 표시할 때의 양사 이용약관을 서면 기준으로 재확인한다.
- 주소 검색, POI, 길찾기, 거리뷰 각각에 대한 대체/유지 방침을 먼저 `DESIGN.md`에 확정한다.
- 동일한 대표 매물 10~20건으로 지오코딩 성공률, 단지 위치 오차, POI 누락, 임장 경로를 나란히 비교한다.

가장 현실적인 실험은 코드 전체 교체가 아니라 **별도 브랜치의 화면 지도 PoC**다. 단, 카카오 Local 결과를 다른 지도 위에 표시하는 혼합 구성이 약관상 허용되는지는 구현 전에 반드시 확인해야 한다. 허용되지 않으면 Naver Local Search/Geocoding까지 같이 바꿔야 하므로 비용이 크게 증가한다.

### 9.3 Google Maps를 권하지 않는 이유

- 국내 아파트 탐색에서 요구되는 상세 지도, 주소·POI 정합성, 국내 길찾기 제약이 현재 용도와 맞지 않는다.
- 종량 과금과 공급자 정책 변경 위험을 감수할 제품상 이득이 작다.
- 현재 프로젝트의 확정 스택과 “Google Maps 제안 금지” 규칙에도 어긋난다.
- 카카오 POI와 혼합할 경우 데이터 표시 약관 검토가 추가된다.

따라서 지도 선택의 순서는 **카카오 유지 > 사용성 실측 후 네이버 전체 스택 검토 > Google 제외**가 적절하다.

### 9.4 변경을 결정한다면 먼저 할 구조 작업

지도 공급자를 바꾸기 전에 다음 중립 계약을 만들면 이후 비용이 줄어든다.

- `MapGeocodingPort`
- `PoiSearchPort`
- `DrivingDirectionsPort`
- 프런트 `map-adapter.js`의 `createMap`, `addPin`, `drawRoute`, `openStreetView`

이는 지금 당장 멀티 벤더를 지원하자는 뜻이 아니다. 실제 변경을 승인한 경우에만 기존 카카오 구현을 이 계약 뒤로 옮기고, 같은 contract test를 네이버 구현에 적용하자는 뜻이다.

공식 확인 출발점:

- [Kakao Maps JavaScript API 가이드](https://apis.map.kakao.com/web/guide/)
- [Kakao Local API 개발 가이드](https://developers.kakao.com/docs/latest/ko/local/dev-guide)
- [NAVER Maps JavaScript API 문서](https://navermaps.github.io/maps.js.ncp/docs/)
- [Google Maps Platform 지원 범위](https://developers.google.com/maps/coverage)

요금, quota, 저장·표시 제한은 변경될 수 있으므로 실제 의사결정 시점에 공식 콘솔과 최신 약관을 다시 확인해야 한다.

## 10. 추천 개선 순서

### 1주차: 신뢰 경계 복구

1. 모든 매물 하위 API의 그룹 인가 누락 수정 및 교차 그룹 통합 테스트
2. CSRF 보호 활성화
3. 회원가입 운영 기본값 `false`, 운영 secret 누락 시 기동 실패
4. LLM 통근 fallback이 `COMMUTE` 외의 결정론적 채점으로 확장되지 않는지 회귀 테스트

### 2주차: 녹색 빌드

1. 익명화된 parser fixture 추가 및 loader 통일
2. `./gradlew test`가 AOT 없이 기본 성공하도록 빌드 정리
3. PostgreSQL 통합 테스트 한 경로 추가
4. CI에서 test와 schema sync 자동 실행

### 3~4주차: 변경 비용 낮추기

1. `ScoringService` 책임 분리
2. `PropertyController` 기능별 분리
3. `app.js`를 ES module로 기능별 분리
4. 서사형 주석을 ADR로 이동하고 중복·자명한 주석 정리

### 그 이후

1. 대표 매물 기반 카카오/네이버 사용성 PoC
2. 스키마 migration 이력 도입
3. 외부 API 성공률·latency·quota와 비동기 작업 상태 관측 강화

## 11. 최종 의견

이 프로젝트의 가장 좋은 점은 **“집을 고르는 판단을 설명 가능하게 만들려는 태도”**다. 가격, 통근, 실거래, 대출, 두 사람의 주관 평가를 한곳에 모으는 제품 방향은 명확하고, 실제 장애에서 배운 내용도 풍부하다.

가장 큰 문제는 Claude가 코드를 못 짰다는 것이 아니다. 오히려 너무 많은 기능과 설명을 빠르게 쌓아 **사람이 경계와 일관성을 재검증하는 단계가 밀렸다**는 데 있다. 접근 통제처럼 한 줄 빠지면 전체 전제가 깨지는 부분, “LLM은 채점하지 않는다”처럼 간접 의존까지 봐야 하는 규칙, fixture처럼 저장소 재현성이 필요한 부분은 모델의 자기검증만 믿으면 안 된다.

새 기능이나 지도 교체보다 먼저 P0 항목을 닫고 기본 테스트를 녹색으로 만드는 것이 좋다. 그 작업이 끝나면 Halley는 두 사람이 실제 집을 고르는 동안 믿고 사용할 수 있는, 꽤 탄탄하고 개성 있는 서비스가 될 가능성이 높다.
