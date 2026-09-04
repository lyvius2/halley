# 그룹 경계 — 매물 인가가 새는 자리

> **상태: 미해결 (2026-09-05 확인).** `docs/ARCHITECTURE_SERVICE_AUDIT_2026-09-03.md` P0 의 후속입니다.
> 감사 문서는 목록만 적었고, 이 문서는 **지금 코드에 남아 있는지 하나씩 확인한 결과**와
> 고치는 방법의 비교입니다.

## 1. 무엇이 문제인가

매물은 **그룹**에 속합니다. `PropertyAccessGuard` 는 "이 매물이 내 그룹 것인가" 를 확인하는
자리이고, 스스로를 <b>단 하나의 길목</b>이라고 선언합니다.

> 매물을 읽는 자리가 스무 곳이 넘습니다. 각자 그룹을 확인하게 두면 **한 곳만 빠져도 남의
> 그룹 자료가 샙니다** — 그리고 빠졌다는 사실은 아무 데도 드러나지 않습니다.
> — `PropertyAccessGuard` javadoc (설계 I87)

**그 예언이 이미 실현돼 있습니다.** 여섯 개 경로가 길목을 안 지납니다.

## 2. 어디가 새는가 (2026-09-05 코드 확인)

`/api/properties/{id}` 아래 25개 경로를 전부 훑어 서비스 안까지 따라갔습니다.
**대부분은 막혀 있습니다** — 댓글·사진·중개인·실거래·대출·뉴스·전망·조회·수정·삭제는
각자의 서비스가 `propertyAccessGuard.require()` 를 부릅니다.

새는 것은 여섯입니다.

| 경로 | 닿는 곳 | 무슨 일이 되는가 |
|---|---|---|
| `POST /{id}/rescore` | `ScoringService.rescore` | 남의 매물 정보·점수가 응답에 실려 나온다 |
| `POST /{id}/scores/recompute` | `ScoringService.rescore` | 위와 같다 |
| `PUT /{id}/scores` | `ScoringService.saveManualScores` | **남의 매물 점수를 고친다** |
| `GET /{id}/land-use` | `LandUseService` | 남의 매물 토지이용계획을 본다 |
| `POST /{id}/land-use` | `LandUseService` | 남의 매물 토지이용계획을 다시 받아 저장한다 |
| `GET /{id}/llm-recommendation` | `LlmRecommendationService` | 남의 매물 AI 평가와 사유를 본다 |

`LandUseService` 와 `LlmRecommendationService` 는 `PropertyAccessGuard` 를 <b>주입조차 받지
않습니다</b>(호출 0건). `ScoringService` 는 받아 쓰는데 `getScored()` 에서만 쓰고
`rescore()`·`saveManualScores()` 에서는 안 씁니다.

```java
// PropertyController:346, 367 — 지난다
propertyAccessGuard.require(id);

// PropertyController:321 — 안 지난다
@PostMapping("/{id}/rescore")
public ScoredPropertyResponse rescore(@PathVariable Long id) {
    return scoringService.rescore(id);
}

// ScoringService:223 — 여기서도 안 본다
public ScoredPropertyResponse rescore(Long propertyId) {
    final Property property = propertyRepository.findById(propertyId)
            .orElseThrow(NotFoundListingsException::new);
```

매물 id 는 순차 증가하는 `Long` 이라 번호를 찾는 데 품이 들지 않습니다.

## 3. 얼마나 급한가 — 있는 그대로

지금은 2인 전용이고 그룹이 사실상 하나라 **오늘 새고 있는 자료는 없습니다.** 문제가 되는
때는 셋입니다.

- 계정이나 세션이 넘어갔을 때
- 그룹이 늘었을 때 — 그때는 이 코드가 이미 굳어 있습니다
- 새 엔드포인트가 생길 때 — **막힌 것과 안 막힌 것이 섞여 있어** 어느 쪽을 본떠야 할지
  알 수 없습니다

감사가 P0 로 올린 이유도 "지금 새고 있다" 가 아니라 <b>"새는 구조인데 아무도 모른다"</b>
쪽입니다.

## 4. 왜 그냥 guard 를 한 줄씩 더하고 끝내지 않는가

더해도 됩니다. 여섯 곳뿐입니다. 다만 그것으로는 **같은 일이 다시 일어나는 것을 못 막습니다** —
지금 새는 것도 처음에는 "한 줄 넣으면 되는 일" 이었습니다.

### 함정: 사용자 요청과 배경 작업이 같은 메서드를 쓴다

`rescore(Long)` 을 부르는 곳은 여섯입니다.

- 사용자: `PropertyController` 299 · 323 · 335
- 배경: `PropertyEnrichmentService:268` · `LlmRecommendationService:457`

배경 작업에는 **로그인 사용자가 없습니다.** guard 를 무조건 태우면 보정과 AI 가 전부
막힙니다 — 등록해도 점수가 안 채워지는 [I284] 증상이 그대로 돌아옵니다. guard 의 javadoc 도
같은 것을 경고합니다.

> 배경 작업은 이미 인가된 매물 번호로 도는 것이라 여기를 거치지 않습니다 — 거치게 하면
> 로그인 사용자가 없어 전부 막힙니다.

## 5. 방법 비교

### ① AOP 로 거른다

| 방식 | 문제 |
|---|---|
| 메서드 이름·시그니처로 포인트컷 | 배경 호출도 함께 걸린다 — 4장의 함정 |
| `@RequiresPropertyAccess` 애너테이션 | 배경 호출은 피하지만 **"붙이는 걸 잊으면 뚫린다"** — 지금의 "부르는 걸 잊었다" 와 같은 실패 모드다 |
| 로그인 여부로 분기 | "로그인 없음 = 통과" 가 되어 <b>안전 실패가 아니라 위험 실패</b>다. 컨텍스트 판정이 어긋나면 조용히 열린다 |

여기에 더해 Spring AOP 는 프록시 기반이라 **같은 빈 안의 self-call 은 안 걸립니다.**
`ScoringService` 에는 이미 `rescore(Long) → rescore(Property)` 내부 호출이 있어, 나중에 내부
경로가 하나 늘면 조용히 우회됩니다 — 지금 고치려는 것과 같은 종류의 구멍입니다.

### ② 타입으로 막는다 — 권고

guard 는 이미 `Property` 를 돌려줍니다(`require(id)` → `Property`). 사용자용 메서드가
**id 가 아니라 `Property` 를 받게** 바꿉니다.

```java
// 사용자 진입점 — Property 를 받는다. 이것을 얻는 길은 guard 뿐이다
public ScoredPropertyResponse rescore(Property property) { ... }

// 배경 작업 — 이미 인가된 번호로 돈다
public ScoredPropertyResponse rescoreBackground(Long propertyId) { ... }
```

컨트롤러는 `scoringService.rescore(propertyAccessGuard.require(id))` 가 됩니다.

값어치는 <b>"확인을 잊을 수" 가 없어진다</b>는 것입니다. 사용자 경로에서 `Property` 를
손에 넣으려면 guard 를 지나는 수밖에 없으니, 잊으면 <b>런타임에 새는 게 아니라 컴파일이
안 됩니다.</b> 배경용은 이름이 달라 헷갈리지도 않습니다.

대가는 시그니처 변경이 호출부로 퍼지는 것인데, 호출부가 여섯이라 감당할 만합니다.

> **그물을 잘 짜는 것보다 그물이 필요 없게 만드는 편이 낫습니다.** AOP 는 "잊어도 잡아주길"
> 기대하는 방식인데, 이 코드베이스는 배경 작업 때문에 그 그물을 정확히 짜기 어렵습니다.

## 6. 할 일

1. `LandUseService` · `LlmRecommendationService` 에 guard 를 들인다 — 지금은 주입조차 안 받는다.
2. `ScoringService.rescore` · `saveManualScores` 를 사용자용(`Property` 인자)과
   배경용(`Long` 인자)으로 가른다.
3. 컨트롤러의 사용자 경로가 `propertyAccessGuard.require(id)` 를 지나게 한다.
4. **표로 만든 통합 테스트**를 둔다 — 여섯 경로 각각에 대해 "다른 그룹 id → 404,
   DB·캐시 변경 없음". 404 인 것도 확인한다: 403 은 <b>그 번호의 매물이 존재한다</b>는
   사실을 알려 준다(설계 I87).
5. 새 엔드포인트가 길목을 안 지나면 드러나게 한다 — `/{id}` 를 받는 컨트롤러 메서드가
   guard 를 지나는지 훑는 구조 시험을 검토한다(`TemplateCallsExistTest` 와 같은 결).

## 7. 함께 볼 것

- `docs/ARCHITECTURE_SERVICE_AUDIT_2026-09-03.md` §4 (P0) · §10 (1주차)
- `docs/DESIGN.md` I87 — guard 를 단 하나의 길목으로 둔 결정
- `docs/DESIGN.md` I284 — 배경 작업이 막히면 나는 증상
