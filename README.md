# Halley

![Java](https://img.shields.io/badge/Java-25-ED8B00?logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1.x-6DB33F?logo=springboot&logoColor=white)
![jOOQ](https://img.shields.io/badge/jOOQ-3.21-FF6D00?logo=databricks&logoColor=white)
![Gradle](https://img.shields.io/badge/Gradle-build-02303A?logo=gradle&logoColor=white)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-database-4169E1?logo=postgresql&logoColor=white)
![Redis](https://img.shields.io/badge/Redis-cache%20%C2%B7%20session-FF4438?logo=redis&logoColor=white)
![Alpine.js](https://img.shields.io/badge/Alpine.js-frontend-8BC0D0?logo=alpinedotjs&logoColor=black)
![Kakao Map](https://img.shields.io/badge/Kakao%20Map-SDK-FFCD00?logo=kakao&logoColor=black)
![Claude](https://img.shields.io/badge/AI-Claude-D97757?logo=claude&logoColor=white)

**같은 집을 함께 찾는 사람들을 위한 매물 비교·평가 도구입니다.**

네이버 부동산 매물을 붙여넣으면 40여 개 필드를 파싱하고, 14개 기준으로 채점해 순위를 매깁니다.
그룹에 속한 사람들의 **현금을 합산해** 대출 한도와 예산을 계산하고, 함께 코멘트를 남기며 임장 동선을 짭니다.

---

## 목차

1. [프로젝트 개요](#프로젝트-개요)
2. [주요 기능](#주요-기능)
3. [채점 기준](#채점-기준)
4. [아키텍처](#아키텍처)
5. [매물 등록 흐름](#매물-등록-흐름)
6. [기술 스택](#기술-스택)
7. [외부 연동](#외부-연동)
8. [시작하기](#시작하기)
9. [환경변수](#환경변수)
10. [API](#api)
11. [배치 작업](#배치-작업)
12. [운영 메모](#운영-메모)
13. [용어](#용어)
14. [문서](#문서)
15. [상태](#상태)

---

## 프로젝트 개요

집을 살 때 사람은 **여러 매물을 동시에 저울질**합니다. 그런데 비교할 정보가 흩어져 있습니다 —
호가는 네이버에, 실거래는 국토부에, 공시가격은 V-World에, 규제지역은 법제처 고시에,
대출 한도는 은행 창구에 있습니다.

Halley는 그것을 **한 화면에 모아** 같은 기준으로 견줍니다. 그리고 혼자가 아니라
**그룹**이 함께 봅니다 — 배우자·가족이 각자 계정으로 들어와 같은 매물 목록을 보고,
각자 임장 인상을 매기고, 현금을 합산해 예산을 계산합니다.

> **폐쇄형입니다.** 회원가입은 프로퍼티로 열고 닫으며, 매물은 **그룹 밖으로 새지 않습니다.**
> 다른 그룹의 매물에 접근하면 403이 아니라 **404**를 돌려줍니다 — 403은 "있지만 못 본다"를
> 알려 주는 셈이라 존재 자체가 새어 나갑니다.

---

## 주요 기능

| 기능 | 설명 |
|---|---|
| **붙여넣기 등록** | 네이버 부동산 매물 상세 텍스트를 붙여넣으면 40여 개 필드가 자동 파싱됩니다 (PC·모바일 공통). 파싱 신뢰도를 필드별로 남겨 무엇을 못 읽었는지 보여 줍니다 |
| **자동 채점** | 14개 기준을 우선순위 가중치로 종합 평가합니다. 산출 근거를 항목마다 문장으로 남깁니다 |
| **AI 추천도** | 매물 제원·주변 시설·구성원 직장·쾌적함 평가·코멘트를 넣어 Claude에게 묻습니다. 사람의 판단이 바뀌면 자동으로 다시 묻습니다 |
| **그룹** | 1인 1그룹. 초대 코드(8자리·24시간)로 합류하고, 빈 그룹은 자동 삭제됩니다. 매물·채점·코멘트가 모두 그룹 단위로 격리됩니다 |
| **대출 한도** | LTV·**스트레스 DSR** 기반 자체 계산. 규제지역·주택 보유 수·금리유형·기존 부채 종류를 반영합니다 |
| **실거래가** | 국토부 실거래를 12개월치 조회해 같은 단지·면적대 중앙값을 보여 줍니다 |
| **공시가격·토지이용계획** | V-World에서 공시가격과 토지거래허가구역·정비구역을 받아 붙입니다 |
| **규제지역 자동 적재** | 법제처 고시 PDF를 파싱해 투기과열지구·조정대상지역을 DB에 채웁니다 |
| **가격 전망** | 실거래 추세·전세가율·장기 추세·전고점 대비·금리 국면·용적률 여유를 코드가 계산하고, **방향은 Claude가 판단**합니다. 매물 카드에 화살표 하나(▲▼▶)로만 뜹니다 |
| **임장 플래너** | 하루 방문할 매물(최대 12건)을 고르면 자가용/대중교통 기준 최적 방문 순서를 계산합니다 |
| **그룹 알림** | 매물 등록·삭제, 코멘트, 쾌적함 평가를 그룹 Webhook으로 보냅니다 |
| **지도·로드뷰** | 카카오맵 마커와 로드뷰 모달 |

---

## 채점 기준

14개 항목을 **우선순위 가중치**로 합산합니다. 가중치는 관리자가 드래그로 바꿉니다.

| 코드 | 항목 | 방식 | 재료 |
|---|---|---|---|
| `PRICE` | 가격 | 자동 | 호가 · 그룹 현금 합계 · 대출 한도 |
| `COMMUTE` | 직주근접 | 자동 | ODsay 대중교통 경로 (구성원 전원 평균) |
| `STATION` | 역세권 | 자동 | 카카오 POI 최근접 지하철역 |
| `EDUCATION` | 교육여건 | 혼합 | 배정 초등학교 · 주변 학교 POI |
| `AMENITY` | 편의시설 | 자동 | 마트·병원·은행 POI |
| `GREEN` | 녹색환경 | 혼합 | 공원·하천 POI |
| `AGE` | 건물 연식 | 자동 | 사용승인연도 |
| `FLOOR` | 층 | 자동 | 해당층 / 총층 |
| `PARKING` | 주차 | 자동 | 세대당 주차대수 |
| `HOUSEHOLDS` | 세대수 | 자동 | 총세대수 |
| `MOVE_IN` | 입주시기 | 자동 | 즉시 · 협의 · 날짜 |
| `COMFORT` | 공간의 쾌적함 | **수동** | 구성원이 각자 1~5점. 총점에는 **평균 × 20** |
| `LLM_RECOMMENDATION` | AI 추천도 | 자동 | Claude |
| `COMPARATIVE_ADVANTAGE` | 비교 우위 추천 | 자동 | 목록 전체를 한 번에 비교 |

> **이미 자동 채점된 항목은 수동으로 덮어쓸 수 없습니다.** 화면이 칸을 추정값으로 채워 두기
> 때문에, 그것을 그대로 저장하면 자동 채점이 통째로 수동으로 굳고 산출 근거가 사라집니다.
> 다만 **산출에 실패해 값이 없으면** 사람이 채울 수 있습니다.

---

## 아키텍처

```
┌──────────────────────────────────────────────────────────────────────┐
│                         Browser (App Shell)                          │
│   Mustache 한 장 + Alpine.js — 빌드 단계 없음                          │
│   폴링: 채점 판 번호 3초 · AI 추천도 2초                                │
└───────────────────────────────┬──────────────────────────────────────┘
                                │ REST (JSON)
┌───────────────────────────────▼──────────────────────────────────────┐
│  adapter/inbound/web        컨트롤러 · DTO                            │
├──────────────────────────────────────────────────────────────────────┤
│  application/service        서비스 · port/out (외부·캐시만)            │
│    PropertyAccessGuard  ← 그룹 격리의 유일한 길목                       │
├──────────────────────────────────────────────────────────────────────┤
│  domain                     채점 산식 · 대출 계산 · 순수 로직           │
│    scoring/criterion/*Scorer   loan/LoanCalculator                   │
├──────────────────────────────────────────────────────────────────────┤
│  adapter/outbound                                                    │
│    persistence  jOOQ (코드젠 없이 손으로 쓴 테이블 정의)                 │
│    cache        Redis(live) / InMemory(local)                        │
│    external     OpenFeign + Resilience4j (FallbackFactory 필수)       │
└───────┬──────────────────────────────────────────────────────────────┘
        │
        ├─ 카카오 (지도 · 지오코딩 · POI · 자가용 경로)
        ├─ ODsay (대중교통)
        ├─ 국토부 (실거래가)
        ├─ V-World (공시가격 · 토지이용계획 · 행정구역)
        ├─ 법제처 (규제지역 고시)
        ├─ 금감원 (대출 상품 금리)
        ├─ 한국은행 ECOS (가계대출 금리 시계열)
        ├─ Claude (AI 추천도)
        └─ Slack (그룹별 Webhook)
```

**포트는 캐시·세션·외부 API에만 둡니다.** DB 접근은 리포지토리를 직접 씁니다 —
바꿀 계획이 없는 것에 추상화를 씌우면 읽기만 어려워집니다.

---

## 매물 등록 흐름

외부 API가 수십 번 붙는 작업이라 **요청이 기다리는 부분과 배경으로 미루는 부분**을 나눕니다.

```
사용자가 매물 등록
        │
        ▼
  DB 저장 (커밋)
        │
        ▼
┌───────────────────────────────────────────┐
│ 앞 단계 — 요청이 기다린다 (수 초)            │
│   초등학교 ∥ 토지이용계획 ∥ 채점             │  ← 가상 스레드로 동시에
│   화면: "저장 중입니다… 학교·규제·점수 확인"   │
└───────────────────┬───────────────────────┘
                    │ 응답 (실제 점수가 실려 온다)
                    ▼
┌───────────────────────────────────────────┐
│ 뒤 단계 — 배경 (수십 초)                    │
│   실거래가 ∥ (공시가격 → AI 추천도)          │
│   화면: 진행 막대 + 폴링으로 자동 반영        │
└───────────────────────────────────────────┘
```

> **AI 추천도는 공시가격 뒤에 옵니다.** 프롬프트에 `공시가격(원)` 줄이 들어가기 때문입니다.
> 나란히 돌리면 첫 판단이 '정보 없음'으로 굳고, 그 뒤로 다시 물을 계기가 없습니다.

> **등록 트랜잭션 안에서는 돌지 않습니다.** 외부 API를 부르는 동안 DB 커넥션을 붙잡으면
> 동시 등록 몇 건에 풀이 마르고, 카카오 장애가 매물 등록 자체를 되돌립니다.

동시 실행 수는 세마포어로 묶습니다(`ENRICHMENT_MAX_CONCURRENCY`, 기본 400) —
스레드가 아니라 **그 끝에 붙은 공공 API**를 지키는 값입니다.

---

## 기술 스택

| 구분 | 기술 |
|---|---|
| 언어 / 프레임워크 | Java 25, Spring Boot 4.1.x |
| 빌드 | Gradle |
| 영속화 | jOOQ 3.21 — **코드젠 없이** 테이블 정의를 손으로 씁니다 |
| DB | PostgreSQL(live) / H2 인메모리(local) |
| 캐시 · 세션 | Redis(live) / 인메모리(local) |
| 화면 | Mustache App Shell + Alpine.js — **빌드 단계 없음** |
| 외부 호출 | OpenFeign + Resilience4j |
| 지도 | 카카오맵 JS SDK |
| 비동기 | 가상 스레드 (`Thread.ofVirtual()`) + 세마포어 |
| 배포 | `https://halley.furaiki-lifelog.com`, Let's Encrypt |

---

## 외부 연동

| 연동 | 용도 | 인증 | 없으면 |
|---|---|---|---|
| 카카오맵 JS | 지도 · 마커 · 로드뷰 | JS 키 (클라이언트) | 지도가 안 뜬다 |
| 카카오 로컬 REST | 지오코딩 · POI | REST 키 | 주소 검색·POI 채점 불가 |
| 카카오 Directions | 자가용 경로 | REST 키 (공유) | 임장 자가용 모드 불가 |
| ODsay | 대중교통 경로 | 쿼리 파라미터 | **직주근접 미산출** |
| 국토부 실거래가 | 참고 실거래 | 서비스 키 | 실거래 카드가 빈다 |
| V-World | 공시가격 · 토지이용계획 · 행정구역 | 인증키 | 공시가격·규제 정보가 빈다 |
| 법제처 | 규제지역 고시 | `OC` | **규제지역을 사람이 넣어야 한다** |
| 금감원 | 대출 상품 금리 | `auth` | 기본 금리 4%로 계산 |
| 국토부 전월세 실거래 | 전세가율 | 서비스 키(공유) | 전세가율 지표가 빠진다 |
| 국토부 건축물대장 | 현재 용적률 → 재건축 여력 | 서비스 키(공유) | 용적률 여유 지표가 빠진다 |
| 한국은행 ECOS | 가계대출 금리 5년 | 인증키(**경로**) | 스트레스 금리가 고정값으로 남는다 |
| 네이버 검색(뉴스) | 관련 기사 링크 — **점수 미반영** | Client ID/Secret | 전망 모달의 기사 목록이 빈다 |
| Claude | AI 추천도 · **가격 전망 판단** | `x-api-key` | 그 두 항목만 미산출 |
| Slack Webhook | 그룹 알림 | URL 자체 | 알림이 안 간다 |

> **키가 없으면 그 기능만 비고 나머지는 그대로 돕니다.** 외부 연동 실패가 본 기능을
> 막지 않는 것이 원칙입니다. 다만 **비었다는 사실은 로그와 화면에 드러냅니다** —
> 조용히 넘어가면 "왜 값이 없는지" 알 수 없습니다.

자세한 호출 규격·응답 구조·함정은 [`docs/INTERFACE_MANUAL.md`](./docs/INTERFACE_MANUAL.md)에 있습니다.

---

## 시작하기

### 사전 요구사항

- JDK 25
- Docker (PostgreSQL · Redis 로컬 실행용 — `local` 프로파일만 쓴다면 불필요)

### 로컬 실행

`local` 프로파일은 **H2 인메모리 + 인메모리 캐시**라 Docker 없이 바로 뜹니다.

```bash
git clone <repo-url>
cd halley

./gradlew bootRun --args='--spring.profiles.active=local'
```

첫 실행 시 계정이 없으면 **콘솔에 임시 Admin 계정**이 출력됩니다.

```
==========================================================
  username : admin
  password : SBwkpr67AKkUUEox
  Please change the password after first login.
==========================================================
```

로그인하면 비밀번호 변경과 프로필 확인을 **강제로** 거칩니다.

### 운영 실행

```bash
DB_URL=jdbc:postgresql://... DB_USERNAME=... DB_PASSWORD=... \
REDIS_HOST=... \
./gradlew bootRun --args='--spring.profiles.active=live'
```

> **운영 DB 스키마는 자동 생성되지 않습니다** (`spring.sql.init.mode: never`).
> [`docs/DDL.sql`](./docs/DDL.sql)로 처음 만들고, 이미 돌던 DB가 뒤처졌으면
> [`docs/DDL-repair.sql`](./docs/DDL-repair.sql)을 쓰십시오 — 전부 `IF NOT EXISTS`라
> 현재 상태와 무관하게 안전하고 여러 번 돌려도 같은 결과입니다.

### 테스트

```bash
./gradlew test
```

---

## 환경변수

### 필수 (운영)

| 변수 | 설명 |
|---|---|
| `DB_URL` · `DB_USERNAME` · `DB_PASSWORD` | PostgreSQL 접속 정보 |
| `REDIS_HOST` · `REDIS_PORT` | Redis 접속 정보 |

### 외부 연동 키

| 변수 | 발급처 |
|---|---|
| `KAKAO_JS_KEY` | 카카오 개발자 — JavaScript 키 |
| `KAKAO_REST_KEY` | 카카오 개발자 — REST 키 (로컬·Directions 공용) |
| `ODSAY_API_KEY` | ODsay LAB |
| `MINISTRY_API_KEY` | 공공데이터포털 — 국토부 실거래가 |
| `HOUSING_PRICE_API_KEY` | V-World (공시가격·토지이용계획·행정구역 공용) |
| `LAW_OC` | 법제처 국가법령정보 공동활용 |
| `FSS_API_KEY` | 금융감독원 금융상품통합비교공시 |
| `ECOS_KEY` | 한국은행 경제통계시스템 |
| `ANTHROPIC_API_KEY` | Anthropic Console |
| `NAVER_CLIENT_ID` · `NAVER_CLIENT_SECRET` | 네이버 개발자센터 — 검색 API |

> **Slack Webhook URL은 환경변수가 아닙니다.** 그룹마다 다르므로 DB
> (`user_group.slack_webhook_url`)에 저장하고 **그룹 정보 화면**에서 관리합니다.

### 동작 조절

| 변수 | 기본값 | 설명 |
|---|---|---|
| `MEMBERSHIP_SIGN_UP_OPEN` | `true` | 회원가입 화면 노출 여부 |
| `MINISTRY_LOOKBACK_MONTHS` | `12` | 실거래를 몇 개월 거슬러 볼지. **이 값이 그대로 호출 횟수입니다** |
| `ENRICHMENT_MAX_CONCURRENCY` | `400` | 보정 동시 실행 상한. 스레드가 아니라 외부 API를 지키는 값 |
| `DB_POOL_MAX` | `5` | Hikari 최대 커넥션. **DB 한도에 맞춰 줄이는 방향입니다** |
| `DB_POOL_MIN_IDLE` | `1` | |
| `DB_POOL_TIMEOUT_MS` | `3000` | 커넥션 대기 상한. 오래 매달리면 화면이 멈춘 것으로 보입니다 |
| `LOAN_STRESS_FLOOR` · `LOAN_STRESS_CAP` | `0.015` · `0.030` | 스트레스 금리 하한·상한 (고시가 바뀌면 여기만) |
| `ECOS_STAT_CODE` | `121Y006` | 예금은행 대출금리 |
| `ECOS_HOUSEHOLD_ITEM` | `BECBLA03` | 가계대출 항목 코드 |
| `LLM_ENABLED` · `LLM_PROVIDER` · `LLM_CLAUDE_MODEL` | `true` · `claude` · `claude-opus-5` | |
| `SLACK_ENABLED` | **`false`** | 알림 전체 스위치. **켜야 아무것도 나갑니다** |
| `SLACK_NOTIFY_PROPERTY_CREATED` | `false` | 매물 등록 알림만 따로 |

> 카카오 개발자 콘솔에 로컬(`http://localhost:8080`)과 운영 도메인을 **모두** 등록해야
> 지도가 렌더됩니다.

### Slack 알림 붙이기

**웹훅 URL은 환경변수가 아니라 그룹마다 DB에 있습니다**(`user_group.slack_webhook_url`).
그룹이 각자 다른 채널을 쓰기 때문입니다 — 한 곳에 몰면 **우리 매물이 남의 채널에 뜹니다.**

#### 1. Slack에서 웹훅 만들기

1. <https://api.slack.com/apps> → **Create New App**
2. **Or start your own way** 아래의 **Blank app** → *Continue*
   *(위쪽 `AI agent`·`Starter app`은 템플릿입니다 — 웹훅만 쓸 것이라 필요 없습니다)*
3. 앱 이름과 워크스페이스를 고릅니다
4. 왼쪽 메뉴 **Incoming Webhooks** → 스위치를 **On**
5. 맨 아래 **Add New Webhook to Workspace** → 알림을 받을 **채널 선택** → *Allow*
6. 만들어진 URL을 복사합니다

> **Slack 화면은 종종 바뀝니다.** 이 문서는 2026-09-02 기준입니다 —
> 예전에는 2번이 `From scratch`였습니다. 이름이 달라 보이면
> <b>"빈 앱으로 시작"에 해당하는 것</b>을 고르면 됩니다.

생김새는 이렇습니다 (실제 값이 아니라 **모양만** 적습니다 — 진짜를 문서에 두면
GitHub 비밀 검사가 푸시를 막습니다):

```
https://hooks.slack.com/services/<팀ID>/<채널ID>/<토큰>
```

> **이 URL 자체가 인증입니다.** 아는 사람은 누구나 그 채널에 글을 쓸 수 있습니다 —
> 공개 저장소·이슈·스크린샷에 올리지 마십시오. 새면 Slack 앱 화면에서 지우고 다시 만듭니다.

#### 2. 앱에 넣기

**헤더의 `{그룹명}의` → 그룹 정보 → Slack Webhook URL** 칸에 붙여넣고 **저장**.
바로 옆 **테스트** 버튼으로 실제로 닿는지 확인합니다 — 채널에 한 줄이 뜨면 된 것입니다.

#### 3. 서버 스위치 켜기

```bash
SLACK_ENABLED=true                    # ← 이게 false 면 아무것도 안 나갑니다 (기본값)
SLACK_NOTIFY_PROPERTY_CREATED=true    # 매물 등록 알림도 받으려면
```

> **`SLACK_ENABLED`의 기본값은 `false`입니다.** 웹훅을 넣고 저장해도 이걸 안 켜면
> 조용합니다. **테스트 버튼은 이 스위치와 무관하게** 보내므로, "테스트는 되는데
> 실제 알림이 안 온다"면 여기부터 보십시오.

#### 무엇이 언제 가나

| 사건 | 스위치 | 보내는 곳 |
|---|---|---|
| 매물 등록 | `SLACK_NOTIFY_PROPERTY_CREATED` | `PropertyCreatedListener` |
| 매물 삭제 | 없음 (항상) | `PropertyCreatedListener` |
| 코멘트 등록 | 없음 (항상) | `PropertyInsightListener` |
| 공간의 쾌적함 평가 | 없음 (항상) | `PropertyInsightListener` |

메시지는 **평문 한 줄**입니다(`{"text": "..."}`). 블록 킷을 쓰지 않습니다 —
읽는 사람이 몇 명뿐이라 꾸밈보다 **한눈에 읽히는 것**이 낫습니다.

```
:house: 새 매물이 등록되었습니다 — 상계주공7단지 714동
:speech_balloon: 월터님이 상계주공7단지 714동에 의견을 남겼습니다
```

#### 안 오면 볼 것

| 증상 | 원인 |
|---|---|
| 테스트도 안 됨 | URL 오타 · Slack 앱에서 웹훅을 지웠음 |
| 테스트는 되는데 알림이 없음 | **`SLACK_ENABLED=false`** |
| 등록만 안 옴 | `SLACK_NOTIFY_PROPERTY_CREATED=false` |
| 가끔 빠짐 | 전송 실패는 `notification_log`에 남고 **5분마다 재시도**합니다(`NotificationRetryJob`). 관리자 → 설정 → 알림 이력에서 상태를 봅니다 |

> **알림 실패가 본 기능을 막지 않습니다.** 매물 등록·코멘트는 Slack이 죽어도 그대로 됩니다.

---

## API

REST 85개. 주요한 것만 적습니다 — 전체 명세는 [`docs/DESIGN.md`](./docs/DESIGN.md)에 있습니다.

### 인증 · 계정

| 메서드 | 경로 | 설명 |
|---|---|---|
| `POST` | `/api/auth/login` | 로그인. 남은 세션 시간을 함께 준다 |
| `POST` | `/api/auth/logout` | |
| `GET` | `/api/auth/session` | 세션 확인 (비밀번호 변경·프로필 확인 필요 여부 포함) |
| `POST` | `/api/auth/password` | 비밀번호 변경 |
| `POST` | `/api/users/sign-up` | 회원가입 (`MEMBERSHIP_SIGN_UP_OPEN`) |
| `PUT` | `/api/users/me/profile` | 프로필 (직장 좌표 · 보유 현금 · 연소득) |
| `PUT` | `/api/users/me/debts` | 기존 부채 (종류별) |
| `POST` | `/api/users/me/withdraw` | 탈퇴 |

### 그룹

| 메서드 | 경로 | 설명 |
|---|---|---|
| `GET` | `/api/groups/me` | 내 그룹 |
| `GET` | `/api/groups/me/detail` | 그룹 정보 화면 — 현금 합계 · 매물 수 · 구성원 |
| `PUT` | `/api/groups/me` | 그룹명 변경 (그룹의 누구나) |
| `PUT` | `/api/groups/me/webhook` | Slack Webhook |
| `POST` | `/api/groups/me/webhook/test` | 테스트 발송 |
| `POST` | `/api/groups/me/invites` | 초대 코드 발급 (8자리 · 24시간) |
| `POST` | `/api/groups/join` | 초대 코드로 합류 |

### 매물

| 메서드 | 경로 | 설명 |
|---|---|---|
| `GET` | `/api/properties` | 목록 (채점 포함, 거래유형 필터) |
| `POST` | `/api/properties` | 등록 — 앞 단계 보정까지 마치고 응답 |
| `GET` `PUT` `DELETE` | `/api/properties/{id}` | 단건 · 수정 · 삭제 |
| `POST` | `/api/properties/parse-preview` | 붙여넣기 파싱 미리보기 |
| `PATCH` | `/api/properties/{id}/status` | 판매 상태 |
| `GET` | `/api/properties/score-versions` | 채점 판 번호 — 화면 폴링용 |

### 채점 · 분석

| 메서드 | 경로 | 설명 |
|---|---|---|
| `PUT` | `/api/properties/{id}/scores` | 수동 점수 저장 |
| `POST` | `/api/properties/{id}/scores/recompute` | **미산출 항목 재산출** |
| `POST` | `/api/properties/{id}/rescore` | 재채점 |
| `GET` | `/api/properties/{id}/llm-recommendation` | AI 추천도 (진행 중 표시 포함) |
| `GET` | `/api/properties/{id}/forecast` | 가격 전망 — 결과가 없어도 200 (진행 중인지 알려야 한다) |
| `POST` | `/api/properties/{id}/forecast/refresh` | 전망 다시 분석 (1~2분) |
| `GET` | `/api/properties/{id}/news` | 관련 기사 — **점수·프롬프트 미반영** |
| `GET` `POST` | `/api/properties/comparative-analysis` | 비교 우위 |
| `GET` `PUT` | `/api/criteria/weights` | 가중치 |

### 매물 부가 정보

| 메서드 | 경로 | 설명 |
|---|---|---|
| `POST` | `/api/properties/{id}/loan-estimate` | 대출 한도 (LTV · 스트레스 DSR) |
| `GET` | `/api/properties/{id}/reference-transactions` | 국토부 실거래 |
| `GET` `POST` | `/api/properties/{id}/land-use` | 토지이용계획 |
| `GET` `POST` `PUT` `DELETE` | `/api/properties/{id}/comments` | 코멘트 |
| `GET` `POST` `DELETE` | `/api/properties/{id}/images` | 이미지 |
| `GET` `PUT` | `/api/properties/{id}/agents` | 중개사 |

### 임장

| 메서드 | 경로 | 설명 |
|---|---|---|
| `POST` | `/api/itinerary/optimize` | 최적 방문 순서 |
| `POST` `GET` | `/api/itinerary/plans` · `/{id}` | 계획 저장 · 조회 |
| `POST` | `/api/itinerary/plans/{id}/recompute` | 다시 계산 |
| `GET` `PUT` | `/api/itinerary/start-location` | 출발지 |

### 관리자

| 메서드 | 경로 | 설명 |
|---|---|---|
| `GET` `POST` | `/api/admin/groups` | 그룹 목록 · 생성 |
| `GET` `PUT` | `/api/admin/settings` | 시스템 설정 |
| `GET` `PUT` | `/api/admin/regulations` · `/params` | 규제 파라미터 |
| `GET` `POST` `DELETE` | `/api/admin/regulated-areas` | 규제지역 |
| `POST` | `/api/admin/stress-rate/refresh` | **스트레스 금리 재산출** (ECOS) |
| `GET` | `/api/admin/notifications` | 알림 발송 이력 |

---

## 배치 작업

| 작업 | 주기 | 하는 일 |
|---|---|---|
| `ListingCheckJob` | 매일 | 매물이 판매완료됐는지 확인하고 그룹에 알린다 |
| `RegulationNoticeJob` | 매일 04:00 | 법제처 고시가 바뀌었으면 규제지역을 다시 적재한다 |
| `MarketRateJob` | 매일 04:30 | 금감원 공시 금리를 갱신한다 |
| `StressRateJob` | 매월 1일 04:45 + 기동 시 | ECOS로 스트레스 금리를 다시 산출한다 |
| `NotificationRetryJob` | 5분 | 실패한 Slack 알림을 재시도한다 |

> 시간을 벌려 둔 이유는 **기동 직후 외부 호출이 몰리지 않게** 하기 위해서입니다.

---

## 운영 메모

### 프로파일

| | `local` | `live` |
|---|---|---|
| DB | H2 인메모리 | PostgreSQL |
| 캐시·세션 | 인메모리 | Redis |
| 스키마 | `schema.sql` 자동 적용 | **수동** (`docs/DDL.sql`) |

> **H2 URL에서 `DB_CLOSE_DELAY=-1`을 빼지 마십시오.** 인메모리 DB는 마지막 커넥션이
> 닫히는 순간 **스키마째 사라집니다** — 기동 직후엔 멀쩡하다 한참 뒤에 갑자기
> 모든 질의가 터집니다.

### local과 live가 다른 지점

같은 컬럼이 dialect마다 다른 타입으로 옵니다 — live는 `jsonb`, local은 `json`.
**타입을 좁히면 live에서만 터집니다** (`JSONB cannot be cast to JSON`).
`parse_confidence` · `path_summary` · `payload` · `assumptions` 넷이 그렇습니다.

### 커넥션 풀

무료 등급 PostgreSQL은 `max_connections`가 20~30 언저리입니다.
**느리다고 풀을 키우면 더 느려집니다** — DB가 감당할 동시 실행 수는 정해져 있고,
그보다 많은 커넥션은 DB 안에서 줄을 섭니다. 기본값을 5로 둔 이유입니다.

---

## 용어

**도보 N분** — 직선거리 × 우회계수 1.3 ÷ 보행속도 67m/분.
예: 역까지 직선 500m → `500 × 1.3 ÷ 67 ≈ 9.7분`. 언덕·지형은 반영하지 않으며 수동 보정할 수 있습니다.

**스트레스 DSR** — 미래 금리 상승을 가정해 한도를 좁히는 규제.
`실효 스트레스 = 기준 스트레스 금리 × 단계 적용률 × 금리유형 가중치`.
**한도 산정과 실제 상환액에 다른 금리를 씁니다** — 섞으면 둘 다 틀립니다.

**MCI / MCG** — 주택담보대출에 붙이는 보증보험. 가입하면 소액임차보증금(방공제)만큼
한도가 늘어납니다. 5,500만원을 좌우하는 항목이라 기본으로 켜 둡니다.

**임장** — 매물을 직접 보러 가는 것. 쾌적함 점수가 있으면 다녀온 것으로 봅니다.

---

## 문서

| 문서 | 내용 |
|---|---|
| [`docs/DESIGN.md`](./docs/DESIGN.md) | 전체 설계서 — 아키텍처 · ERD · 화면 정의 · API 명세 · 채점 산식 · **확정된 의사결정 이력(I1~)** |
| [`docs/INTERFACE_MANUAL.md`](./docs/INTERFACE_MANUAL.md) | 외부 API 매뉴얼 — 키 발급처 · 호출 규격 · 응답 구조 · 실측으로 드러난 함정 |
| [`docs/DDL.sql`](./docs/DDL.sql) | PostgreSQL 스키마 (초기 생성 + 마이그레이션 이력) |
| [`docs/DDL-repair.sql`](./docs/DDL-repair.sql) | 멱등 복구 스크립트 — 운영 DB가 뒤처졌을 때 |
| [`docs/ADJUST_CACHE.md`](./docs/ADJUST_CACHE.md) | 캐시·성능 검토 (실측 기반) |
| [`docs/SCORING.md`](./docs/SCORING.md) | 추천 점수 — 항목별 가중치 · 산출 재료 · 다시 채점하는 계기 |
| [`docs/PRICE_FORECAST.md`](./docs/PRICE_FORECAST.md) | 가격 전망 설계 — 지표 산식 · 코드/LLM 역할 분담 · 안전장치 |
| [`docs/MORTGAGE_ENGINE.md`](./docs/MORTGAGE_ENGINE.md) | 대출 계산 엔진 — LTV · 스트레스 DSR · 담보가치 |
| [`docs/DDL-forecast-reset.sql`](./docs/DDL-forecast-reset.sql) | 전망 재시작용 정리 (429·400 시절 값 걷어내기) |
| [`AGENTS.md`](./AGENTS.md) | AI 코딩 에이전트용 작업 지침 |

> **설계 결정은 번호로 관리합니다.** 코드 주석의 `(설계 I117)` 같은 표기는
> `docs/DESIGN.md` 16장의 해당 항목을 가리킵니다. **왜 그렇게 했는지**가 거기 있습니다.

---

## 상태

개인 프로젝트 · 비공개 저장소 · 소수 사용자 · 상업적 이용 없음.

대출 한도·실거래가·규제지역은 **자체 계산과 공공 데이터**이며 실제 은행 심사 결과와
다를 수 있습니다. 투자 판단의 근거로 삼지 마십시오.

**가격 전망은 특히 그렇습니다.** 공개된 지표 몇 개로 낸 것이고 틀릴 수 있습니다.
재료가 모자라면 방향을 내지 않고 `판단 보류`로 남깁니다 — 넷 중 하나를 억지로 고르지 않습니다.
