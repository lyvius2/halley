# 프리징 전 보안 점검 (2026-09-18)

> **대상**: `main` @ `cb6535e` · 프로젝트 전체.
> **상태**: 지적 사항은 `feature/secure` 브랜치에서 처리한다. 처리 결과는 §4 에 적는다.
> `docs/ARCHITECTURE_SERVICE_AUDIT_2026-09-03.md` §4·§5 의 후속이다 — 그때 지적된 것이
> 보름 뒤에도 **하나도 고쳐지지 않은 채** 남아 있었다.

## 1. 결론

저장소 위생과 입력 경로는 견고하다. 결함은 전부 **인증·인가 경계**와 **운영 기본값**에 몰려 있다.
프리징 전에 P0 세 건은 반드시, P1 은 가능한 만큼 처리한다.

## 2. 수정할 것

| 우선 | 결함 | 위치 | 수정 |
|---|---|---|---|
| **P0** | 그룹 인가 우회 6곳 — 남의 매물 점수 조회·**수정** 가능 | `ScoringService.rescore/saveManualScores`, `LandUseService`, `LlmRecommendationService` (guard 호출 0건) | `docs/GROUP_ACCESS_BOUNDARY.md` §6. 사용자용 메서드가 `Property` 를 받게 |
| **P0** | CSRF 꺼짐 — 세션 쿠키 인증인데 상태 변경 API 다수 | `SecurityConfig:33` | `CookieCsrfTokenRepository` + 프론트가 `X-XSRF-TOKEN` 헤더 전송 |
| **P0** | 운영 기본값이 열려 있음 | 가입 `MEMBERSHIP_SIGN_UP_OPEN:true` · live DB `halley/halley` · `AdminBootstrap:43` 초기 비밀번호 INFO 로그 | 가입 기본 `false`; DB 자격 기본값 제거(없으면 기동 실패); 초기 비밀번호는 로그에 남기지 않음 |
| **P1** | 세션 쿠키 `Secure`·`SameSite` 미설정, 프록시 헤더 미설정 | yaml·Java 어디에도 없음 | `server.servlet.session.cookie.secure/same-site`, `server.forward-headers-strategy` |
| **P1** | 로그인 시도 제한 없음 | `/api/auth/login` `permitAll`, 제한 로직 없음 | 계정+IP 실패 횟수 제한 → 429 |
| **P1** | Bean Validation 없음 | starter 없음, `@Valid` 0건 | starter 추가, 로그인·가입·비밀번호·매물 DTO 제약 |
| **P2** | `/api/users/nickname-check` 비인증 공개 → 계정 열거 | `SecurityConfig` | 가입이 닫히면 함께 닫히게 |

## 3. 이상 없음으로 확인한 것

| 항목 | 근거 |
|---|---|
| 비밀 관리 | `.env` gitignore·미추적·**이력에도 없음**. 코드/문서에 실제 키 없음 |
| 업로드 | 파일명 UUID, 내용을 **JPEG 로 재인코딩**(Thumbnailator)해 원본 바이트 미저장, 20MB/25MB 제한 |
| 이미지 서빙 | 파일명 `[A-Za-z0-9_.-]{1,120}` 허용목록 + `normalize` + `startsWith(dir)`. 그룹 인가 뒤 404 |
| SQL | jOOQ 원시 SQL(`DSL.sql`/`field("`) 없음 |
| XSS | `x-html` · `{{{` · `innerHTML=` 없음 |
| 액추에이터 | 클래스패스에 없음 |
| 예외 노출 | `BusinessException` 메시지만. 그 외는 Boot 기본(스택·메시지 미포함) |
| 비밀번호 | BCrypt |
| 의존성 | Spring Boot 4.1.1, Alpine 3.14.9 |

> **`anyRequest().permitAll()` 을 짚어 둔다.** 지금은 `/api/**`·`/uploads/**` 만 막고 나머지는
> 공개다. 액추에이터가 없어 문제가 안 될 뿐이다 — 나중에 새 경로를 붙이면 **기본이 "공개"** 다.

## 4. 처리 결과

| 항목 | 결과 |
|---|---|
| P0 그룹 인가 우회 6곳 | **완료** (설계 I294). 사용자용 메서드가 `Property` 를 받아 길목을 지나지 않고는 부를 수 없다. `GroupBoundaryApiTest` 6건 — 다른 그룹 → 404, DB 변경 없음 |
| P0 CSRF 꺼짐 | **완료** (설계 I295). `XSRF-TOKEN` 쿠키 ↔ `X-XSRF-TOKEN` 헤더. 화면의 모든 상태 변경 요청(직접 `fetch` 세 곳 포함)이 `withCsrf()` 를 지난다. `CsrfProtectionTest` 4건 — 헤더 불일치/누락 → 403, 일치 → 통과, 셸이 쿠키 발급 |
| P0 운영 기본값 | **완료** (설계 I296). 가입 기본 `false`; live DB 자격·admin 초기 비밀번호는 환경변수 없으면 기동 실패; 정해 준 비밀번호는 로그에 안 남김. `SignUpDefaultClosedTest` · `AdminBootstrapTest` |
| P1 세션 쿠키·프록시 | **완료** (설계 I297). 기본 `same-site=lax`·`http-only`, live `secure=true`·`forward-headers-strategy=native`. XSRF 쿠키도 SameSite. `SessionCookieConfigTest` · `CsrfProtectionTest` |
| P1 로그인 시도 제한 | **완료** (설계 I298). 계정|주소 5회/15분 → 429, 맞는 비밀번호도 막힘, 성공 시 초기화. `LoginRateLimitTest` |
| P1 Bean Validation | **완료** (설계 I299). 로그인·가입·비밀번호·회원 생성 DTO 제약 + `@Valid`, 매물명 길이. 400 `VALIDATION_FAILED`, 보낸 값은 안 되돌림. `RequestValidationTest` |
| P2 nickname-check | **완료** (설계 I300). 가입 닫힘이면 익명 403. 고치다 보니 열림일 때도 401 이던 잠재 버그를 발견해 함께 고침. `SignUpDefaultClosedTest` · `SignUpOpenTest` |
| (덤) Gradle 힙 | 데몬이 AOT 단계에서 죽어 시험이 안 돌던 것. `gradle.properties` 에 2g (설계 I301) |
