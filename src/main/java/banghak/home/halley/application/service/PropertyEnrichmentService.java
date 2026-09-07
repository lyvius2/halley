package banghak.home.halley.application.service;

import banghak.home.halley.adapter.outbound.persistence.PropertyRepository;
import banghak.home.halley.application.event.PropertyEnrichedEvent;
import banghak.home.halley.application.port.out.cache.CachePort;
import banghak.home.halley.application.port.out.external.HousingPricePort;
import banghak.home.halley.application.port.out.external.KakaoLocalPort;
import banghak.home.halley.config.VirtualThreadGate;
import banghak.home.halley.domain.geo.GeoSearchResult;
import banghak.home.halley.domain.geo.PoiResult;
import banghak.home.halley.domain.property.OfficialPrice;
import banghak.home.halley.domain.property.Property;
import banghak.home.halley.domain.property.SchoolSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 매물 등록 직후 외부 API로 빈 칸을 채우는 보정 작업. */
@Slf4j
@Service
public class PropertyEnrichmentService {

 /** 카카오 학교 카테고리. 반경 2km 안에서 가장 가까운 초등학교를 고른다. */
    private static final String SCHOOL_CATEGORY = "SC4";
    private static final int SCHOOL_RADIUS_M = 2000;
 /** 도보 환산: 분당 67m. */
    private static final int WALK_METERS_PER_MINUTE = 67;
 /** 공동주택 공시가격은 같은 필지에 동·호가 모두 나온다. 전용면적이 이 비율 안이면 같은 타입으로 본다. */
    private static final double AREA_TOLERANCE = 0.05;
 /** 102동 · 27 어느 표기든 앞의 숫자를 동 번호로 본다. */
    private static final Pattern DONG_NUMBER = Pattern.compile("(\\d+)");

    private final PropertyRepository propertyRepository;
    private final KakaoLocalPort kakaoLocalPort;
    private final HousingPricePort housingPricePort;
    private final GeoService geoService;
    private final ReferenceTransactionService referenceTransactionService;
    private final LlmRecommendationService llmRecommendationService;
    private final LandUseService landUseService;
    private final ScoringService scoringService;
    private final VirtualThreadGate gate;
    private final ApplicationEventPublisher eventPublisher;
    private final CachePort cache;

    public PropertyEnrichmentService(PropertyRepository propertyRepository,
                                     KakaoLocalPort kakaoLocalPort,
                                     HousingPricePort housingPricePort,
                                     GeoService geoService,
                                     ReferenceTransactionService referenceTransactionService,
                                     LlmRecommendationService llmRecommendationService,
                                     LandUseService landUseService,
                                     ScoringService scoringService,
                                     VirtualThreadGate gate,
                                     ApplicationEventPublisher eventPublisher,
                                     CachePort cache) {
        this.propertyRepository = propertyRepository;
        this.kakaoLocalPort = kakaoLocalPort;
        this.housingPricePort = housingPricePort;
        this.geoService = geoService;
        this.referenceTransactionService = referenceTransactionService;
        this.llmRecommendationService = llmRecommendationService;
        this.landUseService = landUseService;
        this.scoringService = scoringService;
        this.gate = gate;
        this.eventPublisher = eventPublisher;
        this.cache = cache;
    }

 /** 등록 요청이 기다리는 앞 단계. */
 /** 보정 표시가 남아 있을 최대 시간. */
    private static final java.time.Duration ENRICHING_TTL = java.time.Duration.ofMinutes(5);

    public void enrichCore(Long propertyId) {
        final Optional<Property> found = propertyRepository.findById(propertyId);
        if (found.isEmpty()) {
            return;
        }
        final Property property = found.get();
        log.info("Enrichment(core) started. propertyId={}", propertyId);
        final long startedAt = System.currentTimeMillis();
        llmRecommendationService.markPending(propertyId);

        final List<Property> filled = gate.runAll(List.of(
                () -> step(propertyId, "school", () -> fillSchool(property)),
                () -> step(propertyId, "land-use", () -> {
                    fetchLandUse(propertyId);
                    return null;
                }),
                () -> step(propertyId, "score", () -> {
                    rescore(propertyId);
                    return null;
                })));

        final Property school = filled.get(0) == null ? property : filled.get(0);
        if (changed(property, school)) {
            propertyRepository.update(school);
            step(propertyId, "score-after-school", () -> {
                rescore(propertyId);
                return null;
            });
        }
        log.info("Enrichment(core) finished. propertyId={}, elapsedMs={}",
                propertyId, System.currentTimeMillis() - startedAt);
    }

 /** 응답을 돌려준 뒤 배경에서 도는 단계. */
    public void enrichRest(Long propertyId) {
        log.info("Enrichment(rest) started. propertyId={}", propertyId);
        final long startedAt = System.currentTimeMillis();
        gate.runAll(List.of(
                () -> step(propertyId, "reference-trades", () -> {
                    fetchReferenceTrades(propertyId);
                    return null;
                }),
                () -> step(propertyId, "official-price-then-llm", () -> {
                    fillOfficialPriceAndSave(propertyId);
                    fetchLlmRecommendation(propertyId);
                    return null;
                })));
        llmRecommendationService.clearPendingIfUnresolved(propertyId);
        log.info("Enrichment(rest) finished. propertyId={}, elapsedMs={}",
                propertyId, System.currentTimeMillis() - startedAt);
        eventPublisher.publishEvent(new PropertyEnrichedEvent(propertyId));
    }

 /** 앞 단계를 기다렸다가 나머지를 배경으로 넘긴다. */
 /** 등록 응답을 붙잡지 않고 배경에서 보정한다. */
    public void enrichAsync(Long propertyId) {
        cache.put(CachePort.ENRICHING, String.valueOf(propertyId), "1", ENRICHING_TTL);
        Thread.ofVirtual().name("enrich-" + propertyId).start(() -> {
            try {
                enrich(propertyId);
            } catch (RuntimeException e) {
                log.error("Enrichment failed. propertyId={}, cause={}", propertyId, e.toString(), e);
            } finally {
                cache.evict(CachePort.ENRICHING, String.valueOf(propertyId));
            }
        });
    }

    public void enrich(Long propertyId) {
        enrichCore(propertyId);
        Thread.ofVirtual().name("enrich-rest-" + propertyId).start(() -> {
            try {
                enrichRest(propertyId);
            } catch (RuntimeException e) {
                log.error("Enrichment(rest) failed. propertyId={}, cause={}", propertyId, e.toString(), e);
            }
        });
    }

 /** 공시가격만 따로 저장한다. 앞 단계에서 이미 학교를 저장했으므로 다시 읽어 덮어쓰기를 피한다. */
    private void fillOfficialPriceAndSave(Long propertyId) {
        final Optional<Property> found = propertyRepository.findById(propertyId);
        if (found.isEmpty()) {
            return;
        }
        final Property property = found.get();
        final Property priced = fillOfficialPrice(property);
        if (changed(property, priced)) {
            propertyRepository.update(priced);
        }
    }

 /** 바뀐 게 없으면 저장하지 않는다. 보정이 아무것도 못 채운 경우가 흔하다. */
    private boolean changed(Property before, Property after) {
        return !java.util.Objects.equals(before.schoolName(), after.schoolName())
                || !java.util.Objects.equals(before.schoolWalkMinutes(), after.schoolWalkMinutes())
                || !java.util.Objects.equals(before.schoolSource(), after.schoolSource())
                || !java.util.Objects.equals(before.pnu(), after.pnu())
                || !java.util.Objects.equals(before.officialPrice(), after.officialPrice())
                || !java.util.Objects.equals(before.officialPriceYear(), after.officialPriceYear());
    }

 /** 한 단계를 재고 로그를 남긴다. 단계가 터져도 다음 단계는 돌아야 한다. * 실거래가 조회가 막혔을 때 AI 추천도까지 통째로 날아간 적이 있다. */
    private <T> T step(Long propertyId, String name, java.util.function.Supplier<T> body) {
        final long from = System.currentTimeMillis();
        try {
            return body.get();
        } catch (RuntimeException e) {
            log.warn("Enrichment step failed. propertyId={}, step={}, cause={}", propertyId, name, e.toString());
            return null;
        } finally {
            log.info("Enrichment step done. propertyId={}, step={}, elapsedMs={}",
                    propertyId, name, System.currentTimeMillis() - from);
        }
    }

 /** 자동 채점 항목이 채워진 뒤 다시 채점한다. */
    private void rescore(Long propertyId) {
        try {
            scoringService.rescore(propertyId);
        } catch (RuntimeException e) {
            log.warn("Rescore after enrichment failed. propertyId={}, cause={}",
                    propertyId, e.toString());
        }
    }

 /** 붙여넣기 원문에 배정 초등학교가 없으면 카카오로 가장 가까운 초등학교를 찾아 채운다. */
    private Property fillSchool(Property property) {
        if (property.schoolName() != null && !property.schoolName().isBlank()) {
            return property;
        }
        if (property.lat() == null || property.lng() == null) {
            log.info("Skipping school lookup - property has no coordinates. propertyId={}", property.id());
            return property;
        }
        try {
            final Optional<PoiResult> nearest = kakaoLocalPort.searchCategory(
                            SCHOOL_CATEGORY,
                            property.lng().doubleValue(),
                            property.lat().doubleValue(),
                            SCHOOL_RADIUS_M).stream()
                    .filter(poi -> poi.name() != null && poi.name().contains("초등학교"))
                    .filter(poi -> poi.distanceM() != null)
                    .min(Comparator.comparingInt(PoiResult::distanceM));
            if (nearest.isEmpty()) {
                log.info("No elementary school found nearby. propertyId={}", property.id());
                return property;
            }
            final PoiResult school = nearest.get();
            final int walkMinutes = Math.max(1, Math.round(school.distanceM() / (float) WALK_METERS_PER_MINUTE));
            log.info("School filled from Kakao. propertyId={}, school={}, distanceM={}, walkMinutes={}",
                    property.id(), school.name(), school.distanceM(), walkMinutes);
            return withSchool(property, school.name(), walkMinutes);
        } catch (RuntimeException e) {
            log.warn("School lookup failed. propertyId={}, cause={}", property.id(), e.getMessage());
            return property;
        }
    }

 /** 공시가격. 지번주소를 카카오로 다시 조회해 PNU(필지고유번호)를 얻고, 그 필지의 공동주택 */
    private Property fillOfficialPrice(Property property) {
        if (property.officialPrice() != null) {
            return property;
        }
        final String address = property.addressJibun() != null && !property.addressJibun().isBlank()
                ? property.addressJibun() : property.addressRoad();
        if (address == null || address.isBlank()) {
            log.info("Skipping official price lookup - property has no address. propertyId={}", property.id());
            return property;
        }
        try {
            final Optional<String> pnu = geoService.geocode(address).map(GeoSearchResult::pnu)
                    .filter(value -> value != null && !value.isBlank());
            if (pnu.isEmpty()) {
                log.info("Skipping official price lookup - PNU not resolved. propertyId={}, address={}",
                        property.id(), address);
                return property;
            }
            List<OfficialPrice> prices = housingPricePort.fetchApartmentPrices(pnu.get());
            if (prices.isEmpty()) {
                prices = housingPricePort.fetchDetachedHousePrices(pnu.get());
            }
            final Optional<OfficialPrice> picked = pick(prices, property.areaExclusiveM2(), property.dongHo());
            if (picked.isEmpty()) {
                log.info("No official price found. propertyId={}, pnu={}", property.id(), pnu.get());
                return withPnu(property, pnu.get());
            }
            log.info("Official price filled from VWorld. propertyId={}, pnu={}, price={}, year={}, areaM2={}",
                    property.id(), pnu.get(), picked.get().price(), picked.get().year(), picked.get().areaM2());
            return withOfficialPrice(property, pnu.get(), picked.get());
        } catch (RuntimeException e) {
            log.warn("Official price lookup failed. propertyId={}, cause={}", property.id(), e.getMessage());
            return property;
        }
    }

 /** 매물과 같은 타입의 건을 고른다. 어댑터가 이미 한 연도만 가져오므로 여기서는 동·전용면적으로 좁힌다. */
    private Optional<OfficialPrice> pick(List<OfficialPrice> prices, BigDecimal exclusiveAreaM2, String dongHo) {
        if (prices.isEmpty()) {
            return Optional.empty();
        }
        List<OfficialPrice> candidates = prices;
        if (exclusiveAreaM2 != null && exclusiveAreaM2.signum() > 0) {
            final double target = exclusiveAreaM2.doubleValue();
            final List<OfficialPrice> sameArea = prices.stream()
                    .filter(p -> p.areaM2() != null
                            && Math.abs(p.areaM2().doubleValue() - target) / target <= AREA_TOLERANCE)
                    .toList();
            candidates = sameArea.isEmpty()
                    ? nearestArea(prices, target)
                    : sameArea;
        }
        final List<OfficialPrice> sameDong = sameDong(candidates, dongHo);
        return Optional.of(median(sameDong.isEmpty() ? candidates : sameDong));
    }

 /** ±5% 안에 없으면 면적 차가 가장 작은 값들만 남긴다. */
    private List<OfficialPrice> nearestArea(List<OfficialPrice> prices, double target) {
        return prices.stream()
                .filter(p -> p.areaM2() != null)
                .min(Comparator.comparingDouble(p -> Math.abs(p.areaM2().doubleValue() - target)))
                .map(List::of)
                .orElse(prices);
    }

 /** 공시가격의 동명은 27처럼 숫자만 오고 매물의 동/호는 102동이라 숫자로 맞춘다. */
    private List<OfficialPrice> sameDong(List<OfficialPrice> prices, String dongHo) {
        final String dong = dongNumber(dongHo);
        if (dong == null) {
            return List.of();
        }
        return prices.stream()
                .filter(p -> dong.equals(dongNumber(p.dongName())))
                .toList();
    }

    private String dongNumber(String value) {
        if (value == null) {
            return null;
        }
        final Matcher matcher = DONG_NUMBER.matcher(value);
        return matcher.find() ? String.valueOf(Integer.parseInt(matcher.group(1))) : null;
    }

    private OfficialPrice median(List<OfficialPrice> prices) {
        final List<OfficialPrice> sorted = prices.stream()
                .sorted(Comparator.comparing(OfficialPrice::price))
                .toList();
        return sorted.get(sorted.size() / 2);
    }

 /** 국토교통부 실거래가를 미리 받아 둔다. 상세 모달이 버튼 없이 바로 보여줄 수 있어야 한다. */
    private void fetchReferenceTrades(Long propertyId) {
        try {
            referenceTransactionService.prefetch(propertyId);
        } catch (RuntimeException e) {
            log.warn("Reference trade prefetch failed. propertyId={}, cause={}", propertyId, e.getMessage());
        }
    }

 /** 토지이용계획. 토지거래허가구역·정비구역을 매물 상세에 붙인다. */
    private void fetchLandUse(Long propertyId) {
        try {
            landUseService.ensureLandUse(propertyId);
        } catch (RuntimeException e) {
            log.warn("Land use lookup failed. propertyId={}, cause={}", propertyId, e.getMessage());
        }
    }

 /** AI 추천도. 보정의 마지막에 둔다. 공시가격·초등학교가 채워진 뒤라야 */
    private void fetchLlmRecommendation(Long propertyId) {
        try {
            llmRecommendationService.ensureRecommendation(propertyId);
        } catch (RuntimeException e) {
            log.warn("LLM recommendation failed. propertyId={}, cause={}", propertyId, e.getMessage());
        }
    }

    private Property withSchool(Property p, String schoolName, Integer walkMinutes) {
        return copy(p, schoolName, walkMinutes, SchoolSource.KAKAO, p.pnu(), p.officialPrice(), p.officialPriceYear());
    }

    private Property withPnu(Property p, String pnu) {
        return copy(p, p.schoolName(), p.schoolWalkMinutes(), p.schoolSource(),
                pnu, p.officialPrice(), p.officialPriceYear());
    }

    private Property withOfficialPrice(Property p, String pnu, OfficialPrice price) {
        return copy(p, p.schoolName(), p.schoolWalkMinutes(), p.schoolSource(),
                pnu, price.price(), price.year());
    }

    private Property copy(Property p, String schoolName, Integer schoolWalkMinutes, SchoolSource schoolSource,
                          String pnu, Long officialPrice, Integer officialPriceYear) {
        return new Property(
                p.id(), p.name(), p.dongHo(), p.dealType(), p.priceDeposit(), p.maintenanceFee(),
                p.addressRoad(), p.addressJibun(), p.lat(), p.lng(), p.areaSupplyM2(), p.areaExclusiveM2(),
                p.floorRaw(), p.floorNo(), p.floorTotal(), p.floorBand(), p.roomBath(), p.direction(),
                p.approvalYear(), p.moveInType(), p.moveInDate(), p.parkingPerHousehold(), p.totalHouseholds(),
                p.heatingType(), p.buildingCount(), p.kbPrice(),
                p.brokerageFee(), p.brokerageRate(), p.acquisitionTax(), p.propertyTax(), p.comprehensiveTax(),
                schoolName, schoolWalkMinutes, schoolSource, pnu, officialPrice, officialPriceYear,
                p.sourceType(), p.sourceUrl(), p.naverArticleNo(), p.rawPasteText(), p.parserVersion(),
                p.parseConfidence(), p.isDraft(), p.listingStatus(), p.active(), p.lastCheckedAt(),
                p.checkFailStreak(), p.soldDetectedAt(),
                p.groupId(), p.createdByNickname(), p.createdBy(), p.createdAt());
    }
}
