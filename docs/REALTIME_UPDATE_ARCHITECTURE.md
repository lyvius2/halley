# 실시간 화면 갱신 개선안

> 작성일: 2026-09-03  
> 상태: 구현 전 제안  
> 목적: 폴링 과정에서 발생하는 목록·지도 깜빡임을 제거하고 백그라운드 작업 결과를 안정적으로 전달한다.

## 1. 결론

현재 깜빡임의 주원인은 폴링이라는 전송 방식 자체가 아니라, **작은 상태 변경을 감지한 뒤 목록·핀·지도 전체를 다시 읽고 그리는 갱신 방식**이다.

Halley의 실시간 이벤트는 대부분 서버에서 브라우저로 흐르는 단방향 알림이다. 따라서 목표 전송 방식은 WebSocket보다 **Server-Sent Events(SSE)**가 적합하다. 다만 SSE를 도입해도 이벤트 수신 후 `loadProperties()`를 그대로 호출하면 깜빡임은 유지된다. 구현 순서는 반드시 다음과 같아야 한다.

1. 부분 상태 갱신과 지도 marker diff 적용
2. 백그라운드 갱신의 loading UI 분리
3. 중복·경합하는 polling 정리
4. SSE 연결 하나로 완료 이벤트 통합
5. 재연결 및 polling fallback 검증

## 2. 현재 동작

### 전체 채점 감시

`app.js`의 `startScoreWatch()`는 3초마다 `/api/properties/score-versions`를 호출한다. 하나의 매물이라도 version이 바뀌면 `loadProperties()`를 실행한다.

`loadProperties()`는 다음 작업을 묶어서 수행한다.

1. 목록 페이지를 0으로 되돌린다.
2. 첫 페이지의 `properties` 배열 전체를 새 배열로 교체한다.
3. 전체 핀을 다시 조회한다.
4. 지도를 다시 렌더링한다.

### 지도 렌더링

`renderMarkers()`는 기존 marker를 모두 `setMap(null)`로 제거한 뒤 전체 marker를 다시 만든다. 이후 전체 좌표로 bounds를 다시 설정한다.

점수 하나가 바뀌어도 지도 marker 전체가 사라졌다 다시 생기며 viewport까지 움직일 수 있다.

### 기능별 polling

현재 다음 polling이 별도로 존재한다.

| 기능 | 주기 | 주요 동작 |
|---|---:|---|
| 전체 score version | 3초 | 변경 시 목록·핀·지도 전체 갱신 |
| 상세 채점 | 3초 | 상세 매물 전체 조회 |
| 실거래 조회 | 3초 | reference 상태·결과 조회 |
| LLM 추천 | 2초 | 추천 결과 조회, 완료 시 목록 전체 갱신 |
| 가격 전망 | 5초 | 완료 전에도 매번 목록·핀·지도 전체 갱신 |

비동기 `setInterval` callback에 공통 in-flight 보호가 없어, 응답이 polling 주기보다 길어지면 동일 요청이 겹칠 가능성도 있다.

## 3. 깜빡임 원인

### 3.1 배열 전체 교체

```javascript
this.properties = body.items || [];
```

`x-for`에 id key가 있더라도 모든 row 객체가 새 객체로 바뀌므로 Alpine이 카드 내부 binding을 광범위하게 다시 평가한다. 열린 상세 객체도 새 목록 객체로 다시 교체된다.

### 3.2 백그라운드 갱신에도 foreground loading 표시

`loadProperties()`는 `withLoading('properties', ...)`를 사용한다. 요청이 250ms 이상 걸리면 목록 위 progress strip이 나타났다가 사라진다. 이 요소의 높이만큼 목록이 이동해 사용자는 이를 깜빡임으로 느낀다.

사용자가 직접 새로고침을 요청한 경우와 서버 이벤트 때문에 조용히 동기화하는 경우를 구분해야 한다.

### 3.3 지도 전체 제거·재생성

```javascript
Object.values(this.markers).forEach(marker => marker.setMap(null));
this.markers = {};
```

점수 변경은 위치 변경이 아닌데도 모든 overlay를 제거한다. bounds 재설정까지 이어져 지도 사용 맥락을 잃는다.

### 3.4 가격 전망 polling의 무조건 전체 갱신

가격 전망 polling은 version을 비교하지 않고 5초마다 `loadProperties()`를 호출한다. 1분 동안 분석하면 약 12번 전체 목록과 지도를 다시 그릴 수 있다.

## 4. 목표 구조

```text
백그라운드 작업 완료
        │
        ▼
애플리케이션 도메인 이벤트
        │
        ▼
그룹별 SSE event stream
        │
        ├─ PROPERTY_SCORE_CHANGED(propertyId, version)
        ├─ LLM_RECOMMENDATION_COMPLETED(propertyId)
        ├─ REFERENCE_LOOKUP_COMPLETED(propertyId)
        ├─ FORECAST_COMPLETED(propertyId)
        └─ PROPERTY_CHANGED(propertyId, changeType)
        │
        ▼
브라우저의 단일 event dispatcher
        │
        ├─ 해당 카드만 조회·교체
        ├─ 열려 있는 해당 모달만 갱신
        └─ 위치 관련 변경일 때만 marker 갱신
```

## 5. SSE를 선택하는 이유

### SSE가 현재 요구사항에 맞는 점

- 완료 이벤트는 서버에서 브라우저로만 전달된다.
- 일반 HTTP GET과 기존 session cookie 인증을 사용할 수 있다.
- 브라우저 `EventSource`가 연결 복구를 기본 지원한다.
- Spring MVC의 `SseEmitter`로 구현할 수 있다.
- 별도 양방향 메시지 protocol이나 STOMP가 필요 없다.
- 2인용 서비스의 낮은 이벤트 빈도에 충분하다.

### WebSocket이 필요한 시점

다음 요구사항이 생기면 WebSocket을 다시 검토한다.

- 동시 편집 내용의 양방향 전송
- typing/presence 표시
- 실시간 공동 임장 세션
- 편집 lock 협상
- 높은 빈도의 양방향 메시지

현재 범위에서는 WebSocket의 handshake, 메시지 protocol, 인증·재연결 관리 비용을 감수할 이유가 작다.

## 6. 구현 계획

신규 API와 전송 방식을 추가하므로 구현 전에 `docs/DESIGN.md`에 기존 I72·I85의 polling 결정을 뒤집는 새 결정을 기록해야 한다.

### 단계 1. polling 상태 갱신을 부분화한다

SSE 도입 전 먼저 수행한다. 이 단계만으로도 대부분의 깜빡임을 제거할 수 있다.

#### 목록

- foreground 조회와 background 동기화를 분리한다.
- background 동기화에는 progress strip을 표시하지 않는다.
- 변경된 id의 매물만 조회하거나 현재 페이지 응답에서 해당 row만 병합한다.
- 변경되지 않은 row 객체는 그대로 유지한다.
- polling 때문에 `propertyPage`를 0으로 되돌리지 않는다.
- 사용자가 이미 불러온 추가 페이지를 버리지 않는다.

권장 함수 경계:

```javascript
loadPropertyPage({ page, showLoading })
refreshProperty(propertyId)
mergeProperty(updated)
```

#### 상세와 모달

- 이벤트의 `propertyId`가 현재 열린 매물과 같을 때만 상세를 갱신한다.
- score 변경은 score modal만, reference 완료는 reference 영역만 갱신한다.
- 사용자가 입력 중인 form state를 서버 응답으로 덮어쓰지 않는다.

#### 지도

- `markers`를 property id keyed registry로 유지한다.
- 새 id만 추가하고 사라진 id만 제거한다.
- 좌표·가격·면적·거래유형·방문 상태가 바뀐 marker만 교체한다.
- score와 forecast만 바뀐 경우 marker를 건드리지 않는다.
- 최초 지도 진입 또는 사용자의 명시적 “전체 보기”에만 bounds를 맞춘다.

권장 함수 경계:

```javascript
syncMarkers(previousPins, nextPins)
addMarker(pin)
updateMarker(previous, next)
removeMarker(propertyId)
```

### 단계 2. polling 중첩을 막는다

- 각 polling callback에 in-flight flag 또는 `AbortController`를 적용한다.
- `setInterval(async ...)` 대신 완료 후 다음 실행을 예약하는 recursive `setTimeout`을 고려한다.
- 화면·모달이 닫히면 요청도 abort한다.
- `document.hidden`이면 polling을 중지하고 복귀 시 한 번 동기화한다.

### 단계 3. 서버 SSE publisher를 추가한다

권장 endpoint:

```http
GET /api/events
Accept: text/event-stream
```

신규 endpoint이므로 이름과 인증 정책을 먼저 설계 문서에 확정한다.

이벤트 예시:

```text
event: property-score-changed
id: 1842
data: {"propertyId":42,"version":7}
```

최소 payload:

```java
public record PropertyUpdateEvent(
        String type,
        Long propertyId,
        Long version
) {
}
```

주의사항:

- 연결은 인증 사용자와 group id에 귀속한다.
- 다른 그룹의 property id나 이벤트를 절대 보내지 않는다.
- admin의 전체 그룹 구독 여부는 별도 정책으로 확정한다.
- emitter 완료·timeout·오류 시 registry에서 반드시 제거한다.
- 프록시 idle timeout보다 짧은 heartbeat를 보낸다.
- 이벤트 전달 실패가 원래 트랜잭션을 rollback하면 안 된다.
- 커밋된 결과만 알리도록 `AFTER_COMMIT` 경계를 사용한다.

### 단계 4. 기존 완료 지점에 이벤트를 연결한다

다음 작업이 성공적으로 저장된 뒤 이벤트를 발행한다.

- 채점 저장 완료
- LLM 추천 저장 완료
- reference 조회 완료 또는 최종 미산출 확정
- forecast 저장 완료 또는 실패 확정
- 매물 추가·수정·삭제·archive
- 두 사용자 중 다른 사용자의 comfort/comment 변경

가능하면 기존 application event를 재사용하고 SSE adapter가 이를 구독하게 한다. 도메인·서비스가 `SseEmitter`에 직접 의존하지 않게 한다.

### 단계 5. 브라우저 event dispatcher를 추가한다

`app.js`에 더 쌓지 말고 ES module 분리 작업과 함께 진행한다.

```text
static/js/realtime/event-stream.js
static/js/realtime/event-dispatcher.js
static/js/features/properties.js
static/js/features/map-kakao.js
```

브라우저 처리 원칙:

- 연결 성공 여부를 일반 사용자에게 계속 표시하지 않는다.
- 연결이 잠깐 끊겨도 현재 데이터를 지우지 않는다.
- 재연결되면 전체 목록을 한 번 조용히 동기화한다.
- 같은 property/version 이벤트는 중복 적용하지 않는다.
- event handler에서 전체 `loadProperties()`를 호출하지 않는다.

### 단계 6. polling을 fallback으로 축소한다

SSE가 끊긴 동안에만 낮은 빈도의 version polling을 사용할 수 있다.

- SSE 연결 중: 전체 version polling 중지
- SSE 오류 지속: 15~30초 version polling
- SSE 재연결: polling 중지 후 한 번 silent reconciliation

LLM, reference, forecast별 polling은 SSE가 안정화되면 제거한다.

## 7. 테스트 계획

모든 프로덕션 변경에는 JUnit5 테스트를 추가하고 `// given` / `// when` / `// then`, `@DisplayName` 규칙을 따른다.

### 서버

- 인증하지 않은 SSE 연결은 401
- member는 자기 그룹 이벤트만 수신
- 다른 그룹 이벤트 미수신
- admin 정책에 따른 수신 범위
- emitter timeout/error/completion 뒤 registry 제거
- 같은 사용자의 재연결 처리
- 저장 rollback 시 이벤트 미발행
- 저장 commit 후 이벤트 발행
- heartbeat가 연결을 유지
- 느리거나 끊어진 client가 작업 완료 thread를 막지 않음

### 프런트

현재 JavaScript 단위 테스트 도구가 없으므로 최소한 순수 함수로 분리해 브라우저 또는 가벼운 JS test runner 도입 여부를 설계에서 결정한다.

반드시 검증할 동작:

- score 변경 시 대상 카드만 바뀜
- score 변경 시 지도 marker가 재생성되지 않음
- 위치 변경 시 해당 marker만 변경
- background refresh에서 progress strip이 나타나지 않음
- 스크롤 위치와 추가 로딩 페이지가 유지됨
- 열린 다른 매물 modal이 덮어써지지 않음
- 중복 이벤트가 중복 요청을 만들지 않음
- SSE 재연결 뒤 누락된 상태가 한 번 동기화됨

### 수동 브라우저 검증

두 개의 로그인 세션을 나란히 열고 다음을 확인한다.

1. A가 comfort 점수를 변경하면 B의 해당 카드만 갱신된다.
2. B의 스크롤과 지도 중심은 움직이지 않는다.
3. 등록 직후 score, LLM, reference, forecast가 순차 완료돼도 목록이 깜빡이지 않는다.
4. 네트워크를 offline/online으로 전환해도 현재 화면은 유지되고 복구 후 최신 상태가 반영된다.
5. reverse proxy 환경에서 장시간 유휴 연결 후에도 재연결된다.

## 8. 완료 조건

- 백그라운드 결과 반영 중 목록 progress strip이 나타나지 않는다.
- 단순 score/forecast 변경으로 지도 marker 전체가 제거되지 않는다.
- 단순 score/forecast 변경으로 지도 bounds가 바뀌지 않는다.
- 이미 불러온 페이지와 스크롤 위치가 유지된다.
- 기능별 2~5초 polling이 제거되거나 SSE 장애 fallback으로만 남는다.
- 다른 그룹 이벤트가 전송되지 않는 통합 테스트가 통과한다.
- SSE가 끊겨도 기능이 멈추거나 오래된 상태로 고정되지 않는다.
- `./gradlew test`가 통과한다.

## 9. 구현 시 피해야 할 것

- SSE 이벤트를 받은 뒤 무조건 `loadProperties()` 호출
- score 이벤트만으로 전체 pin 재조회
- 매 갱신마다 marker 전체 제거 및 bounds 재설정
- domain/service 계층에서 `SseEmitter` 직접 참조
- group id 검증 없는 전역 broadcast
- 재연결 때 현재 UI state 초기화
- 기존 polling을 둔 채 SSE를 추가해 요청 경로를 중복 운영
- 실시간 기능을 이유로 React/Vue 또는 메시지 브로커 추가

## 10. 집에서 Codex에 줄 시작 프롬프트

다음 프롬프트를 이 저장소에서 사용한다.

```text
docs/REALTIME_UPDATE_ARCHITECTURE.md를 전부 읽고 구현에 착수해 주세요.
AGENTS.md와 docs/DESIGN.md가 우선 규칙입니다.

먼저 docs/DESIGN.md에 기존 I72·I85의 polling 결정을 뒤집는 새로운 결정 항목을 작성하고,
1단계인 부분 상태 갱신과 지도 marker diff부터 구현하세요.
SSE 전환까지 한 번에 크게 바꾸지 말고 단계별로 테스트 가능한 커밋 단위로 진행하세요.

핵심 완료 조건:
- background refresh에서 목록 loading strip이 깜빡이지 않을 것
- score/forecast 변경으로 marker 전체를 재생성하지 않을 것
- 스크롤과 이미 불러온 페이지를 유지할 것
- 사용자 요청과 다른 기능은 변경하지 않을 것
- 모든 프로덕션 변경에 JUnit5 테스트를 추가할 것

1단계를 구현하고 테스트한 뒤 변경 내용과 남은 단계를 보고하고 멈추세요.
```

## 11. 관련 파일

- `src/main/resources/static/js/app.js`
- `src/main/resources/templates/index.mustache`
- `src/main/resources/static/css/app.css`
- `src/main/java/banghak/home/halley/adapter/inbound/web/PropertyController.java`
- `src/main/java/banghak/home/halley/application/service/ScoringService.java`
- `src/main/java/banghak/home/halley/application/event/`
- `docs/DESIGN.md`의 I72, I85, I220, I259~I262

