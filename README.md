# Halley

![Java](https://img.shields.io/badge/Java-25-ED8B00?logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1.x-6DB33F?logo=springboot&logoColor=white)
![Gradle](https://img.shields.io/badge/Gradle-build-02303A?logo=gradle&logoColor=white)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-database-4169E1?logo=postgresql&logoColor=white)
![Redis](https://img.shields.io/badge/Redis-cache%20%C2%B7%20session-FF4438?logo=redis&logoColor=white)
![Alpine.js](https://img.shields.io/badge/Alpine.js-frontend-8BC0D0?logo=alpinedotjs&logoColor=black)
![Kakao Map](https://img.shields.io/badge/Kakao%20Map-SDK-FFCD00?logo=kakao&logoColor=black)
![DeepSeek](https://img.shields.io/badge/Built%20with-DeepSeek%20V4%20Flash-4D6BFE?logo=deepseek&logoColor=white)
![Claude Code](https://img.shields.io/badge/Verified%20with-Claude%20Code-D97757?logo=claude&logoColor=white)

부동산 매물을 비교·평가하기 위한 2인 전용 폐쇄형 웹 애플리케이션입니다. 네이버 부동산 매물 정보를 붙여넣기로 등록하고, 직주근접·가격·역세권 등 12개 기준으로 자동/수동 채점해 우선순위를 정합니다.

## 주요 기능

- **매물 등록**: 네이버 부동산 매물 상세 페이지 텍스트를 붙여넣으면 40여 개 필드가 자동 파싱됩니다(PC·모바일 공통)
- **자동 채점**: 가격, 층수, 역세권, 학군, 편의시설, 녹지, 건물동수 등 12개 기준을 우선순위 기반 가중치로 종합 평가
- **매매/전세 분리 순위표**: 거래유형별로 별도 리스트 제공
- **지도 연동**: 카카오맵 기반 매물 위치 표시 + 로드뷰 모달
- **임장 동선 최적화**: 하루 방문할 매물(최대 12건)을 고르면 자가용/대중교통 기준 최적 방문 순서를 계산
- **매물 생존 확인**: 매일 아침 등록된 매물이 판매완료됐는지 자동 점검 후 Slack 알림
- **대출 한도 계산**: LTV·DSR 기반 자체 계산 + 국토부 실거래가 참고 표기

## 스택

| 영역 | 기술 |
|---|---|
| 언어/프레임워크 | Java 25, Spring Boot 4.1.x |
| 빌드 | Gradle |
| 화면 | Mustache(App Shell) + Alpine.js |
| DB | PostgreSQL, Redis(세션·캐시) |
| 지도 | 카카오맵 JS SDK, 카카오 로컬/Directions/Roadview API |
| 대중교통 경로 | ODsay API |
| 알림 | Slack Incoming Webhook |
| 배포 | `https://cena.furaiki-lifelog.com`, Let's Encrypt |

## 시작하기

### 요구사항

- JDK 25
- Docker (PostgreSQL, Redis 로컬 실행용)

### 로컬 실행

```bash
git clone <repo-url>
cd halley

# 인프라 기동
docker compose up -d

# 애플리케이션 실행
./gradlew bootRun
```

첫 실행 시 사용자 계정이 없으면 콘솔 로그에 임시 Admin 계정 정보가 출력됩니다. 로그인 후 비밀번호를 즉시 변경해야 합니다(강제 모달).

### 환경변수

| 변수 | 설명 |
|---|---|
| `SLACK_WEBHOOK_URL` | Slack Incoming Webhook URL |
| `KAKAO_JS_KEY` | 카카오맵 JavaScript 키 |
| `KAKAO_REST_KEY` | 카카오 로컬/Directions/Roadview REST API 키 |
| `ODSAY_API_KEY` | ODsay 대중교통 길찾기 API 키 |
| `MINISTRY_API_KEY` | 공공데이터포털 국토부 실거래가 서비스 키 (Encoding·Decoding 어느 형태든 가능) |
| `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` | PostgreSQL 접속 정보 |
| `REDIS_HOST` / `REDIS_PORT` | Redis 접속 정보 |

카카오 개발자 콘솔에서 로컬(`http://localhost:8080`)과 운영(`https://cena.furaiki-lifelog.com`) 도메인을 모두 플랫폼에 등록해야 지도가 렌더됩니다.

### 테스트

```bash
./gradlew test
```

## 용어

**도보 N분 (설계 I4 확정)**: 직선거리 × 우회계수 1.3 ÷ 보행속도 67m/분으로 환산합니다. 예: 역까지 직선 500m → `500 × 1.3 ÷ 67 ≈ 9.7분`. 언덕·지형은 반영하지 않으며, 사용자가 수동 보정할 수 있습니다.

## 문서

- [`docs/DESIGN.md`](./docs/DESIGN.md) — 전체 설계서: 아키텍처, ERD, 화면 정의, API 명세, 채점 산식, 확정된 의사결정 이력
- [`docs/INTERFACE_MANUAL.md`](./docs/INTERFACE_MANUAL.md) — 외부 API 연동 매뉴얼: 카카오·ODsay·Slack 키 발급처, 설정 키, 호출 흐름(Mermaid)
- [`AGENTS.md`](./AGENTS.md) — AI 코딩 에이전트용 작업 지침

## 상태

개인 프로젝트 · 비공개 저장소 · 외부 사용자 없음
