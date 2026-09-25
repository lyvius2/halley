# Halley

<img src="src/main/resources/static/image/logo-240.png" alt="Halley" width="180">

![Java](https://img.shields.io/badge/Java-25-ED8B00?logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1.x-6DB33F?logo=springboot&logoColor=white)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-4169E1?logo=postgresql&logoColor=white)
![Redis](https://img.shields.io/badge/Redis-FF4438?logo=redis&logoColor=white)
![Alpine.js](https://img.shields.io/badge/Alpine.js-8BC0D0?logo=alpinedotjs&logoColor=black)
![License](https://img.shields.io/badge/License-MIT-green.svg)

Halley는 함께 살 집을 찾는 두 사람이 매물을 모으고, 같은 기준으로 비교하고, 임장 결과를 공유하는 폐쇄형 웹앱입니다.
네이버 부동산에서 복사한 매물 상세 텍스트를 붙여넣으면 정보를 읽고, 채점·대출·실거래 참고 정보와 임장 동선을 한 화면에 제공합니다.

> 현재 저장소는 기능을 고정하는 프리징 단계입니다. 새로운 기능보다 보안·버그 수정과 운영 안정성을 우선합니다.

## 빠른 시작

### 필요한 환경

- JDK 25
- Docker 및 Docker Compose (PostgreSQL·Redis를 로컬에서 사용할 때)
- 아이폰 사진(HEIC)을 처리하려면 libheif 1.16 이상과 HEIC 디코더

~~~bash
# macOS
brew install libheif

# Amazon Linux 2023
sudo dnf install libheif

# Debian / Ubuntu
sudo apt-get install libheif-dev
~~~

### 로컬 실행

로컬 프로파일은 H2와 인메모리 캐시를 사용하므로 PostgreSQL·Redis 없이 시작할 수 있습니다.

~~~bash
git clone <repository-url>
cd Halley
./gradlew bootRun --args='--spring.profiles.active=local'
~~~

첫 실행 때 관리자 계정과 임시 비밀번호가 콘솔에 표시됩니다. 로그인한 뒤 비밀번호와 프로필을 먼저 확인해 주세요.

PostgreSQL과 Redis를 함께 사용하려면 다음 순서로 실행합니다.

~~~bash
docker compose up -d
./gradlew bootRun --args='--spring.profiles.active=local'
~~~

### 운영 실행

운영 DB는 애플리케이션이 자동으로 만들지 않습니다. 배포 전에 스키마를 적용해 주세요.

~~~bash
psql "$DB_URL" -f docs/DDL.sql
./gradlew bootRun --args='--spring.profiles.active=live'
~~~

이미 운영 중인 DB에는 docs/DDL-repair.sql을 사용합니다. APP_IMAGES_DIR은 반드시 절대 경로로 지정하십시오.

## 사용 흐름

1. 관리자가 계정을 만들고 그룹에 사용자를 초대합니다.
2. 네이버 부동산 매물 상세 텍스트를 복사해 매물 등록에 붙여넣습니다. 네이버 페이지를 직접 크롤링하지 않습니다.
3. 파싱 결과를 확인하고 저장합니다.
4. 자동 채점이 완료되면 매물을 비교합니다. 매매와 전세는 서로 다른 목록으로 관리합니다.
5. 구성원은 각자 임장 의견과 공간의 쾌적함 점수를 입력합니다.
6. 대출 추정·시장 참고 정보·임장 플래너를 사용합니다.

## 주요 기능

| 기능 | 설명 |
|---|---|
| 매물 등록 | 붙여넣은 텍스트에서 정보를 파싱하고 필드별 신뢰도를 표시합니다. |
| 결정론적 채점 | 가격·직주근접·역세권·교육·편의시설·녹지·연식·층·주차·세대수·입주 시기를 규칙으로 계산합니다. |
| 구성원 평가 | 쾌적함 점수를 구성원별로 저장하고 평균을 총점에 반영합니다. |
| 대출 추정 | LTV·스트레스 DSR·규제지역·보유 주택 수를 반영한 참고용 한도를 계산합니다. |
| 시장 참고 정보 | 국토교통부 실거래, V-World 공시가격·토지이용계획, 규제지역을 표시합니다. 실거래가는 채점에 사용하지 않습니다. |
| 가격 전망 | 코드가 계산한 지표를 바탕으로 LLM이 방향과 근거를 설명합니다. |
| 임장 플래너 | 카카오맵·ODsay·카카오 Directions로 방문 순서를 계산합니다. |
| 그룹 협업 | 그룹별 매물·코멘트·알림을 격리하고 Slack으로 주요 변경을 알립니다. |
| 사진 | 로컬 볼륨에 사진을 저장합니다. HEIC는 서버에서 JPG로 변환해 전시합니다. |

## 구조

~~~text
src/main/java/banghak/home/halley
├── adapter/inbound/web       HTTP Controller와 DTO
├── adapter/outbound          PostgreSQL·Redis·외부 API 어댑터
├── application/service       유스케이스와 그룹 권한 검증
├── domain                    채점·대출·파싱·전망 규칙
├── ingest/parser             붙여넣기 텍스트 파서
└── batch                     상태·금리·규제지역 갱신 작업

src/main/resources
├── templates/index.mustache  Alpine.js 앱 셸
├── static/js/app.js          화면 동작
├── static/css/app.css        공통·반응형 스타일
├── schema.sql                H2 로컬 스키마
└── application-*.yaml       프로파일별 설정
~~~

Mustache 하나를 앱 셸로 사용하고 Alpine.js가 상태를 렌더링합니다. React나 Vue 같은 별도 프론트엔드 빌드 단계는 없습니다.
데이터베이스는 jOOQ 리포지토리에서 접근하며 Redis에는 세션·캐시·rate limit만 저장합니다.

## 채점 원칙

- 가격 채점은 호가 기준입니다. KB시세와 국토부 실거래가는 가격 점수에 넣지 않습니다.
- 매매와 전세는 별도 순위표로 유지합니다.
- 기준 점수는 코드로 설명할 수 있는 결정론적 규칙입니다.
- ODsay 장애 때만 LLM이 이동 시간을 추정하며, LLM이 점수 자체를 정하지는 않습니다.
- 자동 산출 실패는 MISSING으로 표시하고 수동 입력을 허용합니다.

자세한 산식은 docs/DESIGN.md와 docs/SCORING.md를 참고하십시오.

## 외부 연동과 환경변수

| 변수 | 용도 |
|---|---|
| DB_URL, DB_USERNAME, DB_PASSWORD | PostgreSQL 접속 |
| REDIS_HOST, REDIS_PORT | Redis 접속 |
| APP_IMAGES_DIR | 업로드 이미지 절대 경로 |
| KAKAO_JS_KEY, KAKAO_REST_KEY | 지도·주소·POI·경로 |
| ODSAY_API_KEY | 대중교통 경로 |
| MINISTRY_API_KEY | 국토교통부 실거래 |
| HOUSING_PRICE_API_KEY | V-World |
| LAW_OC | 법제처 규제지역 고시 |
| FSS_API_KEY, ECOS_KEY | 금리·스트레스 금리 |
| ANTHROPIC_API_KEY | Claude 추천·전망·대중교통 fallback |
| NAVER_CLIENT_ID, NAVER_CLIENT_SECRET | 관련 뉴스 검색 |

키가 없는 외부 기능은 빈 결과로 표시되고 매물 등록은 계속할 수 있습니다. 호출 규격과 키 발급처는 docs/INTERFACE_MANUAL.md에 정리되어 있습니다.
Slack Webhook URL은 그룹별 DB 값이며, 실제 알림을 보내려면 SLACK_ENABLED=true가 필요합니다.

## API 개요

전체 명세는 docs/DESIGN.md를 기준으로 합니다.

| 영역 | 주요 경로 |
|---|---|
| 인증 | /api/auth/login, /api/auth/logout, /api/auth/session |
| 그룹 | /api/groups/me, /api/groups/join, /api/groups/me/invites |
| 매물 | /api/properties, /api/properties/parse-preview, /api/properties/{id} |
| 채점·분석 | /api/properties/{id}/scores, /rescore, /llm-recommendation, /forecast |
| 대출·참고 | /api/properties/{id}/loan-estimate, /reference-transactions, /land-use |
| 임장 | /api/itinerary/optimize, /api/itinerary/plans |
| 관리자 | /api/admin/settings, /api/admin/regulations, /api/admin/notifications |

## 테스트

~~~bash
./gradlew test       # Java 단위·통합 테스트
./gradlew jsTest     # 실제 app.js를 실행하는 Node 테스트
./gradlew build      # 테스트를 포함한 배포 빌드
~~~

화면 동작을 바꾸면 src/test/js/에서 실제 app.js를 사용하는 테스트도 함께 수정합니다. 로컬 테스트는 H2를 사용하고, 운영 배포 전에는 PostgreSQL에 DDL을 적용해 마이그레이션을 확인해야 합니다.

## 배치 작업

| 작업 | 역할 |
|---|---|
| ListingCheckJob | 매물 판매 상태 확인 |
| RegulationNoticeJob | 법제처 규제지역 갱신 |
| MarketRateJob | 금융상품 금리 갱신 |
| StressRateJob | ECOS 기반 스트레스 금리 갱신 |
| NotificationRetryJob | 실패한 Slack 알림 재전송 |

## 보안과 운영 주의사항

- 그룹 경계를 서비스 계층에서 다시 확인하며, 다른 그룹의 매물은 404로 응답합니다.
- 네이버 매물 서버를 크롤링하지 않습니다. 등록은 복사·붙여넣기 파싱만 사용합니다.
- 원문 매물 텍스트는 외부 LLM으로 보내지 않습니다.
- 대출·세금·실거래 정보는 참고용입니다. 실제 의사결정 전에는 금융기관·관할 기관에 확인하십시오.
- 운영 로그·업로드 디렉터리·API 키를 저장소에 커밋하지 마십시오.

## 문서

- docs/DESIGN.md: 전체 설계와 확정 결정
- docs/INTERFACE_MANUAL.md: 외부 API 호출 방법
- docs/SCHEMA.md: 데이터베이스 구조
- docs/SCORING.md: 채점 산식
- docs/MORTGAGE_ENGINE.md: 대출 계산 규칙
- docs/COMMENT_GUIDELINES.md: 주석 작성 규칙
- AGENTS.md: 개발·테스트 규칙

## 프리징 상태

Halley는 2인 전용 비공개 서비스로 운영합니다. 현재 기능과 외부 연동 계약을 고정하며, 이후 변경은 보안 취약점·데이터 손상·사용을 막는 버그에 한해 검토합니다.
새로운 기능은 별도 브랜치와 설계 문서에서 먼저 합의해야 합니다.

## 라이선스

MIT © 2026 walter.hwang

코드는 MIT 라이선스를 따르지만, 외부 서비스의 이용약관과 데이터 재사용 조건은 각 제공자의 정책을 따릅니다.
