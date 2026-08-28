package banghak.home.halley.application.service;

import banghak.home.halley.adapter.outbound.persistence.PropertyRepository;
import banghak.home.halley.application.port.out.external.HousingPricePort;
import banghak.home.halley.application.port.out.external.KakaoLocalPort;
import banghak.home.halley.domain.geo.GeoSearchResult;
import banghak.home.halley.domain.geo.PoiResult;
import banghak.home.halley.domain.property.OfficialPrice;
import banghak.home.halley.domain.property.Property;
import banghak.home.halley.domain.property.SchoolSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 매물 등록 직후 외부 API로 빈 칸을 채우는 보정 작업 (설계 I53 · I54).
 *
 * <p>등록 트랜잭션 커밋 뒤 비동기로 돈다. 외부 API가 죽어 있어도 매물 등록 자체는 이미 끝나 있고,
 * 여기서 실패해도 값이 비는 것 외에 부작용이 없다 — 그래서 예외를 삼키고 로그만 남긴다.
 */
@Slf4j
@Service
public class PropertyEnrichmentService {

    /** 카카오 학교 카테고리. 반경 2km 안에서 가장 가까운 초등학교를 고른다. */
    private static final String SCHOOL_CATEGORY = "SC4";
    private static final int SCHOOL_RADIUS_M = 2000;
    /** 도보 환산: 분당 67m (설계 3.1의 POI 도보시간 환산식과 동일). */
    private static final int WALK_METERS_PER_MINUTE = 67;
    /** 공동주택 공시가격은 같은 필지에 동·호가 모두 나온다. 전용면적이 이 비율 안이면 같은 타입으로 본다. */
    private static final double AREA_TOLERANCE = 0.05;
    /** `102동` · `27` 어느 표기든 앞의 숫자를 동 번호로 본다. */
    private static final Pattern DONG_NUMBER = Pattern.compile("(\\d+)");

    private final PropertyRepository propertyRepository;
    private final KakaoLocalPort kakaoLocalPort;
    private final HousingPricePort housingPricePort;
    private final GeoService geoService;
    private final ReferenceTransactionService referenceTransactionService;

    public PropertyEnrichmentService(PropertyRepository propertyRepository,
                                     KakaoLocalPort kakaoLocalPort,
                                     HousingPricePort housingPricePort,
                                     GeoService geoService,
                                     ReferenceTransactionService referenceTransactionService) {
        this.propertyRepository = propertyRepository;
        this.kakaoLocalPort = kakaoLocalPort;
        this.housingPricePort = housingPricePort;
        this.geoService = geoService;
        this.referenceTransactionService = referenceTransactionService;
    }

    /**
     * 보정은 한 번의 조회 · 한 번의 저장으로 끝낸다. 항목마다 update를 날리면 뒤 항목이 앞 항목을 덮어쓴다.
     */
    public void enrich(Long propertyId) {
        final Optional<Property> found = propertyRepository.findById(propertyId);
        if (found.isEmpty()) {
            return;
        }
        final Property property = found.get();
        Property enriched = fillSchool(property);
        enriched = fillOfficialPrice(enriched);
        if (enriched != property) {
            propertyRepository.update(enriched);
        }
        fetchReferenceTrades(propertyId);
    }

    /** 붙여넣기 원문에 배정 초등학교가 없으면 카카오로 가장 가까운 초등학교를 찾아 채운다 (설계 I53). */
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

    /**
     * 공시가격 (설계 I54). 지번주소를 카카오로 다시 조회해 PNU(필지고유번호)를 얻고, 그 필지의 공동주택
     * 공시가격 중 <b>전용면적이 가장 비슷한</b> 건을 고른다. 같은 단지라도 타입마다 공시가격이 달라서
     * 면적을 맞추지 않으면 엉뚱한 값이 붙는다. 공동주택 결과가 없으면 개별주택(단독·다가구)으로 한 번 더 본다.
     */
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

    /**
     * 매물과 같은 타입의 건을 고른다. 어댑터가 이미 한 연도만 가져오므로 여기서는 <b>동·전용면적</b>으로 좁힌다.
     *
     * <p>순서는 전용면적(±5%) → 같은 동 → 중앙값이다. 같은 면적이라도 층·향에 따라 공시가격이 조금씩 달라서
     * (실측 은마 84.43㎡: 6.56억 ~ 6.62억) 첫 건을 집기보다 중앙값이 단지 대표값에 가깝다.
     * 면적을 모르면(수기 등록 등) 전체 중앙값을 쓴다.
     */
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

    /** 공시가격의 동명은 `27`처럼 숫자만 오고 매물의 동/호는 `102동`이라 숫자로 맞춘다. */
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

    /** 국토교통부 실거래가를 미리 받아 둔다 — 상세 모달이 버튼 없이 바로 보여줄 수 있어야 한다. */
    private void fetchReferenceTrades(Long propertyId) {
        try {
            referenceTransactionService.getReferences(propertyId, null, null);
        } catch (RuntimeException e) {
            log.warn("Reference trade prefetch failed. propertyId={}, cause={}", propertyId, e.getMessage());
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
                p.id(), p.name(), p.dongHo(), p.dealType(), p.priceDeposit(), p.priceMonthly(), p.maintenanceFee(),
                p.addressRoad(), p.addressJibun(), p.lat(), p.lng(), p.areaSupplyM2(), p.areaExclusiveM2(),
                p.floorRaw(), p.floorNo(), p.floorTotal(), p.floorBand(), p.roomBath(), p.direction(),
                p.approvalYear(), p.moveInType(), p.moveInDate(), p.parkingPerHousehold(), p.totalHouseholds(),
                p.heatingType(), p.buildingCount(), p.kbPrice(),
                p.brokerageFee(), p.brokerageRate(), p.acquisitionTax(), p.propertyTax(), p.comprehensiveTax(),
                schoolName, schoolWalkMinutes, schoolSource, pnu, officialPrice, officialPriceYear,
                p.sourceType(), p.sourceUrl(), p.naverArticleNo(), p.rawPasteText(), p.parserVersion(),
                p.parseConfidence(), p.isDraft(), p.listingStatus(), p.active(), p.lastCheckedAt(),
                p.checkFailStreak(), p.soldDetectedAt(), p.createdBy(), p.createdAt());
    }
}
