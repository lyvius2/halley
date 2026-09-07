-- 신혼 생활 시작 예산 계획 PoC 혼수 품목 seed (35개)
--
-- H2 로컬 초기화용. PostgreSQL 운영 DB는 docs/DML-budget-items.sql을 적용한다.
-- 금액은 원 단위이며, 원본 정적 PoC의 만 원 단위 값을 10,000배 했다.
-- 상품 가격은 실시간 판매가나 확정 견적이 아닌 계획용 예상값이다.
--
-- 컬럼: seed_key, category, item_name, required, recommended, default_budget_amount_won,
--        candidate_name, candidate_url, alternative_name, alternative_url, note

MERGE INTO household_budget_item_catalog (
    seed_key, category, item_name, required, recommended, default_budget_amount_won,
    candidate_name, candidate_url, alternative_name, alternative_url, note
) KEY (seed_key) VALUES
  ('seed-00', '가전', 'TV', TRUE, FALSE, 2000000, 'LG OLED65B5FNA · 65인치 OLED', 'https://prod.danawa.com/info/?pcode=80821166', 'LG OLED65C5SNA · 상위 화질', 'https://plan.danawa.com/info/?nPlanSeq=11279', NULL),
  ('seed-01', '가전', '냉장고', TRUE, FALSE, 1800000, 'LG T875MEE012 · 870L / 4도어', 'https://prod.danawa.com/info/?pcode=64380341', '냉장고장 실측 후 소형 모델 비교', NULL, '구형 모델 재고·판매가 확인 필요'),
  ('seed-02', '가전', '세탁기', TRUE, FALSE, 1100000, 'LG F24WDWP · 드럼 24kg', 'https://prod.danawa.com/info/?pcode=18544589', '같은 용량의 별도형 세트 견적 비교', NULL, '건조기와 독립된 제품'),
  ('seed-03', '가전', '건조기', TRUE, FALSE, 1200000, 'LG RH19WTWN · 19kg', 'https://prod.danawa.com/info/?pcode=17095328', 'LG RD20WNA 등 20kg급', 'https://plan.danawa.com/info/?nPlanSeq=10522', '세탁기와 독립된 제품 · 직렬 설치 시 호환 키트 확인'),
  ('seed-04', '가전', '에어컨', TRUE, FALSE, 2300000, '삼성 AF17C5734GZRS · 17+6평 2in1', 'https://prod.danawa.com/info/?pcode=19971614', '기존 에어컨 활용', NULL, '기본 설치 여유 포함 · 추가 배관 별도'),
  ('seed-05', '가전', '인덕션', TRUE, FALSE, 700000, '삼성 NZ63B4026AK · 3구', 'https://prod.danawa.com/info/?pcode=16722227', '기존 인덕션·가스레인지 활용', NULL, NULL),
  ('seed-06', '가전', '전자레인지', TRUE, FALSE, 150000, '삼성 MS23K3523AW · 23L', 'https://prod.danawa.com/info/?pcode=4803093', '복합오븐 구매 시 중복 확인', NULL, NULL),
  ('seed-07', '가전', '전기밥솥', TRUE, FALSE, 250000, '쿠쿠 CRP-R0625FBB · 6인용', 'https://m.danawa.com/product/product.html?code=64376999', '쿠쿠 CRP-FHR067FG · IH', 'https://prod.danawa.com/info/?pcode=6220048', NULL),
  ('seed-08', '가전', '청소기', TRUE, FALSE, 400000, 'LG A9 Air AS9000HR', 'https://prod.danawa.com/info/?pcode=34738391', 'LG AU9272WD · 먼지 비움', 'https://prod.danawa.com/info/?pcode=19346648', NULL),
  ('seed-09', '가전', '드라이어', TRUE, FALSE, 50000, '필립스 BHD360/20', 'https://prod.danawa.com/info/?pcode=13959770', '기존 보유품 활용', NULL, NULL),
  ('seed-10', '가구', '침대 프레임', TRUE, FALSE, 300000, '이케아 타르바 + 루뢰위 · 180×200cm', 'https://www.ikea.com/kr/ko/p/tarva-bed-frame-pine-luroey-s89198578/', '무인양품 고무나무 K · 54.9만 원', 'https://mujikorea.co.kr/plan/view/316', '확인 표시가 29.9만 원 · 소나무 무가공 원목'),
  ('seed-11', '가구', '매트리스', TRUE, FALSE, 600000, '이케아 발레보그 + 니세홀름 · 180×200cm', 'https://www.ikea.com/kr/ko/p/valevag-mattress-and-mattress-pad-firm-light-blue-nisseholm-white-s19572363/', '동일 크기 이케아 스프링 매트리스', 'https://www.ikea.com/kr/ko/cat/spring-mattresses-24828/', '직접 누워보고 결정 · 프레임과 치수 일치'),
  ('seed-12', '가구', '소파', TRUE, FALSE, 540000, '이케아 살트셰바덴 · 베이지 3인용', 'https://www.ikea.com/kr/ko/p/saltsjoebaden-3-seat-sofa-vittangi-light-beige-white-s29621424/', '무인양품 목제 3인용 · 본체 99.9만 원', 'https://mujikorea.co.kr/plan/view/316', '확인 표시가 53.9만 원 · 대안은 커버 구성 확인'),
  ('seed-13', '가구', '식탁', TRUE, FALSE, 250000, '이케아 리사보 · 140×78cm', 'https://www.ikea.com/kr/en/p/lisabo-table-ash-veneer-80365717/', '무인양품 고무나무 · 120×70cm / 34.9만 원', 'https://mujikorea.co.kr/products/view/99938', '확인 표시가 24.9만 원 · 밝은 무늬목'),
  ('seed-14', '가구', '식탁 의자', TRUE, FALSE, 320000, '이케아 리사보 · 4개', 'https://www.ikea.com/kr/ko/p/lisabo-chair-ash-80457236/', '무인양품 라운드 체어 · 4개 79.6만 원', 'https://mujikorea.co.kr/products/view/96090', '확인 표시가 4개 31.96만 원'),
  ('seed-15', '가구', '서랍장', TRUE, FALSE, 430000, '이케아 톤스타드 · 참나무 4칸', 'https://www.ikea.com/kr/ko/p/tonstad-chest-of-4-drawers-oak-veneer-10614616/', '무인양품 떡갈나무 체스트 · 가격 확인 필요', 'https://mujikorea.co.kr/products/view/100029', '잠정 후보 · 무인양품 가격 확인 후 10% 기준 적용'),
  ('seed-16', '가구', '옷장', TRUE, FALSE, 1400000, '이케아 팍스·그리모 · 폭 3m', 'https://www.ikea.com/kr/ko/p/pax-grimo-wardrobe-combination-white-grey-green-s99579285/', '붙박이장 충분하면 제외', NULL, '내부 구성품 여유 포함'),
  ('seed-17', '식기', '그릇·접시·면기', TRUE, FALSE, 150000, '이케아 베르데라 18종 + 한식용 그릇', 'https://www.ikea.com/kr/ko/p/vaerdera-18-piece-service-white-40277355/', '이케아 365+ 개별 구매', 'https://www.ikea.com/kr/ko/p/ikea-365-bowl-rounded-sides-white-60282997/', NULL),
  ('seed-18', '식기', '수저·커트러리', TRUE, FALSE, 50000, '무인양품 기본 스테인리스 · 2~4인분', 'https://mujikorea.co.kr/products/view/100580?sc=11606', '이케아 기본 커트러리', NULL, '링크는 대표 스푼 예시 · 젓가락 등 개별 구성'),
  ('seed-19', '식기', '물컵·머그', TRUE, FALSE, 50000, '이케아 기본 제품 · 각각 2~4개', NULL, '무인양품 기본 제품', NULL, '세부 상품 미정'),
  ('seed-20', '식기', '냄비', TRUE, FALSE, 100000, '이케아 365+ · 소형·중형 각 1개', 'https://www.ikea.com/kr/ko/p/ikea-365-saucepan-with-lid-stainless-steel-50484236/', '이케아 안논스', 'https://www.ikea.com/kr/ko/p/annons-pot-with-lid-glass-stainless-steel-50298475/', NULL),
  ('seed-21', '식기', '프라이팬', TRUE, FALSE, 50000, '이케아 365+ 논스틱 · 24·28cm', 'https://www.ikea.com/kr/ko/p/ikea-365-frying-pan-stainless-steel-non-stick-coating-30587909/', '28cm 1개로 시작', NULL, '링크에서 크기 선택'),
  ('seed-22', '가전', '식기세척기', FALSE, TRUE, 1200000, 'LG DUE5STE · 14인용', 'https://prod.danawa.com/info/?pcode=74808587', NULL, NULL, '빌트인 공간·걸레받이 확인'),
  ('seed-23', '가전', '로봇청소기', FALSE, TRUE, 900000, '로보락 Qrevo Edge C', 'https://m.danawa.com/product/product.html?code=78781088', '로보락 Qrevo C · 실속형', 'https://prod.danawa.com/info/?pcode=88135769', NULL),
  ('seed-24', '가전', '의류관리기', FALSE, FALSE, 1500000, 'LG 스타일러 SC5MSR40', 'https://nosearch.com/product/living/clothing_purifier/detail/SC5MSR40', NULL, NULL, NULL),
  ('seed-25', '가전', '김치냉장고', FALSE, FALSE, 2100000, 'LG Z334 시리즈 · 300L대', 'https://lgecds.com/category/김치냉장고/167/', NULL, NULL, NULL),
  ('seed-26', '가전', '공기청정기', FALSE, FALSE, 250000, '삼성 AX033B310GWD · 10평급 1대', 'https://prod.danawa.com/info/?pcode=16766240', NULL, NULL, NULL),
  ('seed-27', '가전', '에어프라이어', FALSE, FALSE, 150000, '필립스 NA230/00', 'https://prod.danawa.com/info/?pcode=71504639', NULL, NULL, NULL),
  ('seed-28', '가구', '작업용 책상', FALSE, TRUE, 250000, '이케아 리사보 · 140×78cm', 'https://www.ikea.com/kr/en/p/lisabo-table-ash-veneer-80365717/', '무인양품 고무나무 · 34.9만 원', 'https://mujikorea.co.kr/products/view/99938', NULL),
  ('seed-29', '가구', '작업용 의자', FALSE, TRUE, 300000, '이케아 마르쿠스급 · 모델 최종 확인', NULL, '착석 후 결정', NULL, NULL),
  ('seed-30', '가구', '협탁', FALSE, FALSE, 160000, '이케아 PS 2026 목제 · 2개', 'https://www.ikea.com/kr/ko/p/ikea-ps-2026-bedside-table-pine-with-flip-down-door-70621765/', '이케아 크나레비크 · 개당 1.5만 원', 'https://www.ikea.com/kr/ko/p/knarrevik-bedside-table-black-80576319/', NULL),
  ('seed-31', '가구', '책장·선반', FALSE, FALSE, 130000, '이케아 이바르 · 89×30×179cm', 'https://www.ikea.com/kr/ko/p/ivar-shelving-unit-pine-s99582481/', '이케아 빌리 시리즈', 'https://www.ikea.com/kr/en/cat/billy-series-28102/', NULL),
  ('seed-32', '가구', '거실장', FALSE, FALSE, 350000, '밝은 나무색 제품 · 미정', NULL, '실측 후 선택', NULL, NULL),
  ('seed-33', '가구', '화장대·거울·의자', FALSE, FALSE, 250000, '목제 테이블 겸용 구성 · 미정', NULL, '기존 서랍장 활용', NULL, NULL),
  ('seed-34', '가구', '전신거울', FALSE, FALSE, 50000, '제품 미정', NULL, '옷장 거울 활용', NULL, NULL)
;

-- H2 MERGE의 KEY(seed_key)로 여러 번 실행해도 seed가 중복되지 않는다.
