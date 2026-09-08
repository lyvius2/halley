# 금융결제원 오픈뱅킹 잔액 연동 설계

## 목적과 범위

신혼 예산 계획의 그룹 자산 중 **사용자가 동의해 연결한 예금·적금 등 계좌성 금융자산**을
자동으로 불러온다. 각 사용자가 자기 계좌만 연결하며, 같은 그룹의 상대방은 합산 금액과 이미
공유된 예산 자산 항목만 본다.

초기 범위는 잔액 조회다. 부동산·자동차, 증권 종목별 평가액, 대출 잔액, 연결하지 않은 계좌는
이 API로 채우지 않으며 기존 수기 자산 입력을 유지한다.

## 적합성 판단

적합하다. 잔액조회 API는 사용자가 오픈뱅킹 계좌등록 과정에서 연결한 계좌의
`fintech_use_num`과 `access_token`으로 호출하며, `balance_amt`, `available_amt`, 개설기관,
상품명, 계좌종류를 반환한다. 따라서 예산의 `FINANCIAL` 자산 후보를 만들고 사용자가 투입
가능 금액을 정하는 흐름에 맞는다.

단, “전 금융자산 자동 목록”으로 해석하면 부적합하다. 연결 계좌가 전제이고, 보유 주식·펀드의
시가나 부동산·자동차 가치까지 완전하게 제공하지 않는다. 계좌 목록 확보에는 OAuth 사용자 인증과
금융결제원 계좌등록 화면을 사용하고, 각 계좌 잔액은 별도로 갱신한다.

## 사용자 흐름

1. 사용자가 `내 금융자산 연결`을 누른다.
2. Halley가 금융결제원 제공 OAuth Authorization Code Grant 인증·계좌등록 화면으로 이동시킨다.
   이 화면은 Halley UI가 아니며, Halley는 계좌번호·비밀번호·본인인증 수단을 직접 입력받거나 처리하지 않는다.
3. callback에서 `code`와 `state`를 검증하고 토큰을 교환한다.
4. 계좌등록 결과의 핀테크이용번호를 저장한다.
5. 사용자가 `잔액 새로고침`을 누르면 계좌별 잔액을 조회한다.
6. 조회 결과는 `금융결제원 연동` 출처의 자산 후보로 표시한다. 사용자는 포함 여부와 투입 가능
   금액을 확정한다. 자동 조회가 수기 금액이나 제외 상태를 덮어쓰지 않는다.
7. 연결 해제 시 토큰·핀테크이용번호·동기화 이력의 민감 식별자를 삭제하고, 사용자가 확정한
   일반 자산 항목은 유지할지 함께 선택한다.

## API와 인증

- OAuth: 금융결제원 오픈뱅킹의 Authorization Code Grant 방식. 이용기관 앱의 callback URL,
  client ID·secret, 사용자 동의와 계좌등록이 필요하다.
- 잔액: `GET /v2.0/account/balance/fin_num`, scope `inquiry`.
  `Authorization: Bearer <access_token>`, `fintech_use_num`, `bank_tran_id`, `tran_dtime`을 보낸다.
- 응답에서 `balance_amt`는 계좌 잔액, `available_amt`는 출금 가능 금액이다. 예산의 기본
  평가금액은 `balance_amt`로 만들되, 투입 가능 금액은 0으로 시작해 사용자가 명시적으로 정한다.
- `bank_tran_id`는 요청마다 새로 만들며 이용기관 코드·난수 규칙은 금융결제원 운영 명세를 따른다.

## 데이터 모델

`household_budget_asset`에는 이미 `source_type`이 있으므로 `KFTC_OPEN_BANKING`을 추가한다.
연결 자격 증명과 계좌 식별자는 자산 테이블에 넣지 않고 별도 테이블로 분리한다.

```text
open_banking_connection
- id, user_id (unique), provider, encrypted_access_token, encrypted_refresh_token
- token_expires_at, consented_at, disconnected_at, created_at, updated_at

open_banking_account
- id, connection_id, fintech_use_num (encrypted or masked), bank_code, bank_name
- product_name, account_type, last_balance_amt, last_available_amt, last_synced_at
- linked_budget_asset_id, active, created_at, updated_at

open_banking_sync_log
- id, connection_id, requested_at, completed_at, status, failure_code, account_count
```

계좌번호·토큰·Authorization 헤더·원본 OAuth 응답은 로그, Slack, LLM 프롬프트, 브라우저 저장소에
남기지 않는다. 토큰은 PostgreSQL에 암호문으로만 저장하고, 암호화 키는 환경변수로 주입한다.
키 회전과 재동의 만료를 지원하며 Redis에는 영속 토큰을 저장하지 않는다.

## Halley 아키텍처

- inbound: `OpenBankingConnectionController`와 callback controller
- application: `OpenBankingConnectionService`, `OpenBankingAssetSyncService`
- outbound port: OAuth 교환·잔액 조회를 분리한 port
- outbound adapter: OpenFeign `@FeignClient`와 API별 `FallbackFactory`; OAuth, 잔액 API는 서로 다른
  connect/read timeout과 circuit breaker를 둔다.
- API key, client secret, callback URL, 이용기관 코드는 환경변수와 `application.yaml` placeholder로만
  주입한다.

예정 API는 다음과 같다.

```text
GET    /api/open-banking/connection
POST   /api/open-banking/connection/authorize
GET    /api/open-banking/oauth/callback
POST   /api/open-banking/accounts/sync
DELETE /api/open-banking/connection
```

모든 endpoint는 로그인 사용자 자신의 연결만 다룬다. 그룹 관리자를 포함해 다른 사용자의 토큰·계좌
식별자·상세 잔액을 조회하거나 갱신할 수 없다.

## 구현 전 확인 사항

- 금융결제원 이용기관 가입·서비스 신청·운영 승인 및 callback URL 등록 가능 여부
- 테스트베드와 운영 endpoint·client credential·이용 제한 차이
- `inquiry` scope, 토큰 만료/갱신, 계좌등록 결과 수신 형식의 최신 운영 명세
- 개인정보 처리방침·동의 문구·보유 기간·연결 해제·삭제 요청 절차
- 계좌 목록 API의 실제 사용 권한과 참여 금융기관 범위

이 확인이 끝나기 전에는 실제 OAuth endpoint나 금융결제원 호출 코드를 추가하지 않는다.

## 1단계 구현: 연동 준비 상태 점검

애플리케이션은 실제 금융결제원 API를 호출하지 않고 아래 환경 변수와 이용기관 확인 상태만 점검한다.
값 자체는 로그에 남기지 않으며, 누락된 변수명만 기록한다.

```text
KFTC_OPEN_BANKING_ENABLED=true
KFTC_OPEN_BANKING_ENVIRONMENT=test
KFTC_API_KEY=...
KFTC_CLIENT_ID=...
KFTC_CLIENT_SECRET=...
KFTC_CLIENT_USE_CODE=...
KFTC_CALLBACK_URL=https://.../api/open-banking/oauth/callback
KFTC_OAUTH_SERVICE_CONFIRMED=true
KFTC_BALANCE_INQUIRY_SERVICE_CONFIRMED=true
```

`KFTC_OAUTH_SERVICE_CONFIRMED`와 `KFTC_BALANCE_INQUIRY_SERVICE_CONFIRMED`는 개발자 사이트에서
OAuth·잔액조회 서비스 신청, API Key 등록, Callback URL 등록을 확인한 뒤에만 `true`로 설정한다.
이 값은 금융결제원 승인 상태를 API로 조회하는 기능이 아니라 운영자가 확인을 완료했다는 표시다.
