# 신혼 생활 시작 예산 계획 PoC 설계서

## 1. 목적

신혼부부가 주택을 매매·임차하고 혼수·가구·생활용품을 준비하는 데 필요한 초기 자금과
월별 주거비를 한 화면에서 계산한다.

이 기능은 기존 Halley의 인증·그룹 권한·Mustache Shell·Alpine.js·jOOQ·PostgreSQL 구조를
재사용한다. 별도의 프론트엔드 프레임워크나 별도 서버를 도입하지 않는다.

## 2. 현재 구조와 통합 원칙

- Java 25 / Spring Boot 4.1.1 / Spring MVC
- Mustache는 앱 Shell, Alpine.js는 상태와 클라이언트 렌더링 담당
- 운영 DB는 PostgreSQL, 로컬·테스트는 H2
- 영속화는 jOOQ Repository가 담당하며 ORM은 사용하지 않음
- Redis는 세션·캐시·rate limit 용도이며 예산 원본을 저장하지 않음
- 그룹 격리는 `group_id`와 서비스의 접근 검증을 사용
- SPA 화면 라우트는 `app.js`의 `ROUTES`에 둠

새 화면은 헤더의 `임장` 오른쪽에 `예산`을 추가하고 `/budget` 라우트로 제공한다. 화면은
기존 `index.mustache`에 Alpine 뷰로 추가하며 독립 HTML 페이지로 유지하지 않는다.

## 3. 원본 혼수 데이터의 처리

`src/main/resources/templates/halley-household-plan.html`은 독립 정적 PoC이며 35개 품목과
후보 상품·대안 상품의 이름·URL·예상 금액을 포함한다.

통합 시 다음 필드를 추출해 seed migration으로 옮긴다.

- `seed_key`
- 카테고리(가전·가구·식기)
- 품목명
- 필수 여부
- 추천 여부
- 후보 상품명·URL
- 대안 상품명·URL
- 예상 금액(만 원 단위)
- 비고

데이터 이관이 끝난 뒤 원본 `halley-household-plan.html`은 폐기한다. 이후 상품 정보는
예산 화면과 DB에서 관리한다.

## 4. 예산 계획 데이터 모델

### 4.1 `household_budget_plan`

그룹이 작성하는 하나의 예산 계획이다.

```text
id
group_id
created_by
plan_name
scenario                 -- MINIMUM | RECOMMENDED | COMFORTABLE | CUSTOM
selected_property_id     -- nullable, property.id
housing_type             -- SALE | JEONSE | OTHER_RENT
region
house_name
purchase_price
exclusive_area_m2
contract_cash
balance_cash
acquisition_tax
brokerage_fee
registration_fee
moving_cost
cleaning_cost
other_initial_cost
parent_support
other_funds
monthly_management_fee
monthly_other_housing_cost
created_at
updated_at
```

기존 매물을 선택하면 이름·주소·거래유형·가격·전용면적을 채우고, 사용자가 직접 수정할
수 있다. 매물을 선택하지 않고 직접 입력하는 것도 허용한다.

### 4.2 `household_budget_asset`

그룹 구성원별 보유 자산이다.

```text
id
plan_id
user_id
asset_type               -- FINANCIAL | REAL_ESTATE | VEHICLE | OTHER
asset_name
estimated_value
excluded                 -- 투입 제외 여부
investable_amount        -- 실제 투입 예정 금액
source_type              -- MANUAL | FUTURE_API
note
created_at
updated_at
```

`user_id`는 반드시 해당 계획의 `group_id`에 속한 사용자여야 한다. 자산 평가액과 실제
투입액을 별도로 두어 자동차·기존 주택처럼 일부 또는 전부를 제외할 수 있게 한다.

### 4.3 `household_budget_item`

계획에 포함된 혼수 품목의 사용자별 선택 상태와 금액이다.

```text
id
plan_id
seed_key
category
item_name
required
recommended
selected
owned
budget_amount_won
candidate_name
candidate_url
candidate_price_won
alternative_name
alternative_url
alternative_price_won
candidate_fetch_status
alternative_fetch_status
note
created_at
updated_at
```

금액은 화면에서 원화로 입력·표시하고, seed의 만 원 단위 값은 migration 시 원 단위로
변환한다. 후보와 대안은 각각 독립적으로 수정·조회한다.

### 4.4 `household_budget_financing`

```text
id
plan_id
loan_amount
loan_ratio
interest_rate
term_months
repayment_type         -- AMORTIZED | PRINCIPAL_EQUAL | INTEREST_ONLY
monthly_payment
is_estimate
created_at
updated_at
```

## 5. 상품 URL 정보 읽기

### 5.1 사용자 흐름

1. 후보 상품 URL 또는 대안 상품 URL을 입력한다.
2. `상품 정보 읽기` 버튼을 누른다.
3. 서버가 URL을 검증하고 허용된 외부 페이지에서 상품 정보를 요청한다.
4. 읽은 상품명·가격을 입력 폼의 임시 값으로 보여준다.
5. 사용자가 확인·수정한 뒤 `저장`한다.
6. 읽기에 실패하면 실패 사유를 표시하고 상품명·예상 금액을 수기로 입력하게 한다.

자동 추출 결과는 확정 판매가가 아니다. 저장 전 사용자 확인을 반드시 거치며, URL을
읽었다고 해서 자동으로 예산 항목을 확정하지 않는다.

### 5.2 추출 우선순위

페이지 응답에서 다음 순서로 읽는다.

1. JSON-LD의 `Product.name`, `Offer.price`
2. Open Graph `og:title`
3. 페이지의 `<title>`
4. 화면에 표시된 가격 패턴(원화·만원)

상품명만 읽히고 가격이 없으면 상품명만 채우고 가격은 수기 입력으로 남긴다. 가격이 여러
개면 대표 판매가로 단정하지 않고 실패 또는 `가격 확인 필요` 상태로 처리한다.

### 5.3 API 제안

```text
POST /api/budget-plan/{id}/items/{itemId}/candidate/preview
POST /api/budget-plan/{id}/items/{itemId}/alternative/preview
```

요청:

```json
{ "url": "https://example.com/product/123" }
```

응답 예시:

```json
{
  "status": "FOUND",
  "url": "https://example.com/product/123",
  "name": "상품명",
  "priceWon": 1290000,
  "priceSource": "JSON_LD",
  "message": null
}
```

실패 응답도 예외로 화면을 중단하지 않고 수기 입력으로 전환할 수 있게 한다.

```json
{
  "status": "FAILED",
  "url": "https://example.com/product/123",
  "name": null,
  "priceWon": null,
  "priceSource": null,
  "message": "상품명 또는 가격을 확인하지 못했습니다"
}
```

### 5.4 외부 페이지 접근 안전 규칙

상품 URL 읽기는 일반적인 임의 URL 프록시가 되어서는 안 된다.

- PoC에서는 허용 도메인 목록을 설정값으로 관리한다.
- `http`, `https`만 허용한다.
- localhost, 사설 IP, 링크 로컬 주소, 내부 DNS로 해석되는 주소는 차단한다(SSRF 방지).
- 리다이렉트마다 동일한 검증을 다시 수행한다.
- 응답 크기·연결 시간·리다이렉트 횟수를 제한한다.
- HTML과 JSON-LD만 읽고 로그인·결제·개인정보 페이지는 처리하지 않는다.
- 원문 HTML을 DB에 저장하지 않는다.
- User-Agent와 서비스 이용약관을 준수하며, 사이트별 차단 우회나 크롤링을 구현하지 않는다.
- 자동 추출이 불안정한 도메인은 허용 목록에서 제외하고 수기 입력을 사용한다.

상품 URL 읽기는 기존 매물 데이터 수집과 별개다. 네이버 매물 크롤링 금지 규칙을 우회하는
용도로 사용하지 않으며, 네이버 매물 등록은 계속 붙여넣기 파싱만 사용한다.

## 6. 계산 규칙

### 6.1 주택 초기 필요자금

```text
자기자금 주택대금 = 주택가격 - 대출금

주택 초기 필요자금
= 자기자금 주택대금
 + 취득세
 + 중개보수
 + 법무사·등기 비용
 + 기타 주택 초기 비용
```

계약금과 잔금은 자기자금 주택대금의 분할 입력으로 사용하고, 합계가 맞지 않으면 경고한다.
전세·임차는 취득세·등기 비용을 기본 0으로 두되 사용자가 직접 입력할 수 있다.

### 6.2 혼수 예산

```text
혼수 상품 합계
= selected=true AND owned=false인 품목의 예산 합계

혼수 총 예산
= 상품 합계 + 배송·조립·설치 예비비
```

### 6.3 통합 요약

```text
투입 가능 자산 = 그룹 자산의 investable_amount 합계

신혼 생활 시작 총 필요자금
= 주택 초기 필요자금
 + 혼수 총 예산
 + 이사·입주청소 비용
 + 기타 초기 정착 비용

확보 자금
= 투입 가능 자산 + 대출금 + 부모 지원금 + 기타 자금

추가 필요자금
= 총 필요자금 - 확보 자금
```

추가 필요자금이 양수이면 부족, 음수이면 잔여로 표시한다. 대출금과 월 원리금은 금융기관
확정 심사가 아닌 입력 기반 참고용 추정치임을 상시 표시한다.

## 7. API 목록

```text
GET    /api/budget-plans
POST   /api/budget-plans
GET    /api/budget-plans/{id}
PUT    /api/budget-plans/{id}
DELETE /api/budget-plans/{id}

GET    /api/budget-plans/{id}/assets
POST   /api/budget-plans/{id}/assets
PUT    /api/budget-plans/{id}/assets/{assetId}
DELETE /api/budget-plans/{id}/assets/{assetId}

GET    /api/budget-plans/{id}/items
PUT    /api/budget-plans/{id}/items/{itemId}
POST   /api/budget-plans/{id}/items/{itemId}/candidate/preview
POST   /api/budget-plans/{id}/items/{itemId}/alternative/preview

PUT    /api/budget-plans/{id}/financing
GET    /api/budget-plans/property-candidates
```

모든 계획 API는 인증이 필요하며, 서비스 계층에서 그룹 소속을 재검증한다. Controller가
Repository를 직접 호출하지 않는다.

## 8. 화면 구성

- `예산` 메인 화면
- 시나리오 선택: 최소 시작·현실적인 추천·여유 있는 시작·사용자 정의
- 주택 정보 입력 카드
- 등록 매물 선택 modal
- 그룹 구성원별 자산 목록·추가·수정·투입 제외
- 혼수 항목 검색·카테고리 필터·보유품 제외
- 후보 상품·대안 상품 URL 및 자동 읽기 버튼
- 자동 읽기 실패 시 수기 상품명·예상 금액 입력
- 통합 예산 요약 카드

데스크톱에서는 입력과 요약을 2열로 배치하고 모바일에서는 카드가 세로로 쌓인다. 기존
Halley의 아이보리·검정·청록·금색 토큰과 반응형 규칙을 재사용한다.

## 9. 구현 순서

1. 이 설계에 대한 결정 이력을 `docs/DESIGN.md`에 추가
2. 35개 혼수 품목을 seed 데이터로 추출
3. `DDL.sql`·`DDL-repair.sql`에 테이블과 인덱스 추가
4. 예산·자산·혼수·금융 도메인 record 작성
5. 원리금·자기자금·부족자금 계산 서비스와 단위 테스트 작성
6. jOOQ 테이블 정의와 Repository 작성
7. 그룹 권한 검증을 포함한 Budget Service 작성
8. 상품 URL 검증·정보 추출 서비스와 실패 fallback 작성
9. Controller·DTO·API 테스트 작성
10. `예산` Alpine 화면·매물 선택 modal·자산/혼수 편집 UI 구현
11. 기존 CSS 기반 반응형 스타일과 접근성 속성 추가
12. 원본 HTML 삭제
13. README 또는 사용 문서 갱신
14. Java 테스트·API 테스트·`jsTest`·주요 시나리오 검증

## 10. PoC와 후속 범위

### PoC에 포함

- 사용자가 직접 입력하는 주택·대출·자산·비용
- 기존 등록 매물 선택
- 35개 혼수 seed 품목
- 필수·추천·전체 시나리오
- 품목 선택·보유품 제외·금액 수정
- 후보·대안 URL 저장과 제한된 상품 정보 preview
- URL 읽기 실패 시 수기 입력
- 그룹별 저장·조회·권한 검증
- 모바일·데스크톱 반응형 화면

### 후속 개발

- 국토교통부 실거래가 연동
- KB 시세 연동
- 금융상품·금리 데이터 연동
- 취득세·중개보수 법정 기준 자동 갱신
- 자산 API·금융기관 연동
- 사이트별 공식 상품 API 연동
- 여러 저장 시나리오 비교
- 가격 변동·재고·판매 상태 갱신

## 11. 검증 시나리오

- 가격·대출금 입력 시 자기자금이 정확히 계산되는가
- 취득세·중개보수·등기비가 합계에 포함되는가
- 필수·추천·전체 시나리오가 즉시 전환되는가
- 보유 에어컨·인덕션·옷장을 제외하면 합계가 줄어드는가
- 자산의 투입 제외·부분 투입이 확보 자금에 반영되는가
- 품목 가격 수정이 전체 필요자금에 반영되는가
- 후보 URL에서 상품명·가격을 읽어 임시 입력하는가
- URL 읽기 실패 후 수기 입력으로 저장할 수 있는가
- 허용되지 않은 도메인·내부 주소가 차단되는가
- 새로고침 후 계획·자산·품목 상태가 유지되는가
- 다른 그룹 사용자가 계획을 조회할 수 없는가
- 모바일 화면에서 입력·URL·요약이 가로로 넘치지 않는가
- 기존 매물·임장·채점 기능에 회귀가 없는가
