package banghak.home.halley.application.service;

import banghak.home.halley.adapter.inbound.web.dto.LandUseResponse;
import banghak.home.halley.adapter.outbound.persistence.LandUseRepository;
import banghak.home.halley.adapter.outbound.persistence.PropertyRepository;
import banghak.home.halley.application.port.out.cache.PropertyDetailCache;
import banghak.home.halley.application.port.out.external.LandUsePort;
import banghak.home.halley.domain.geo.GeoSearchResult;
import banghak.home.halley.domain.landuse.LandUse;
import banghak.home.halley.domain.property.Property;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * 토지이용계획 (설계 I69).
 *
 * <p>매물 필지에 걸린 지역·지구를 받아 저장합니다. <b>토지거래허가구역</b>(실거주 의무 — 갭투자 불가)과
 * <b>정비구역</b>(재건축)은 매수 판단을 가르는 정보라 화면에서 강조합니다.
 *
 * <p>규제지역(투기과열지구·조정대상지역)은 <b>여기 없습니다.</b> 그건 「주택법」상 지정이라
 * 토지이용계획에 등재되지 않습니다 — 실측으로 확인했습니다(설계 I69).
 */
@Slf4j
@Service
public class LandUseService {

    private final LandUsePort landUsePort;
    private final LandUseRepository landUseRepository;
    private final PropertyRepository propertyRepository;
    private final GeoService geoService;
    private final PropertyDetailCache detailCache;
    private final ObjectMapper objectMapper;

    public LandUseService(LandUsePort landUsePort,
                          LandUseRepository landUseRepository,
                          PropertyRepository propertyRepository,
                          GeoService geoService,
                          PropertyDetailCache detailCache,
                          ObjectMapper objectMapper) {
        this.landUsePort = landUsePort;
        this.landUseRepository = landUseRepository;
        this.propertyRepository = propertyRepository;
        this.geoService = geoService;
        this.detailCache = detailCache;
        this.objectMapper = objectMapper;
    }

    /**
     * 저장된 값만 읽는다 — 외부를 부르지 않는다.
     *
     * <p>캐시를 먼저 본다 (설계 I158). 토지이용계획은 거의 바뀌지 않는데 상세 모달을
     * 열 때마다 DB를 왕복했다.
     */
    public List<LandUseResponse> find(Long propertyId) {
        final Optional<String> cached = detailCache.get(PropertyDetailCache.LAND_USE, propertyId);
        if (cached.isPresent()) {
            try {
                return objectMapper.readValue(cached.get(), new TypeReference<List<LandUseResponse>>() {
                });
            } catch (RuntimeException e) {
                // 담아 둔 모양이 바뀌었을 수 있다. 버리고 DB 에서 다시 읽는다
                log.warn("Land-use cache unreadable - falling back to DB. propertyId={}, cause={}",
                        propertyId, e.getMessage());
                detailCache.evict(PropertyDetailCache.LAND_USE, propertyId);
            }
        }
        final List<LandUseResponse> fresh = landUseRepository.findByPropertyId(propertyId).stream()
                .map(LandUseResponse::from)
                .toList();
        detailCache.put(PropertyDetailCache.LAND_USE, propertyId, objectMapper.writeValueAsString(fresh));
        return fresh;
    }

    /**
     * 필요하면 조회해 저장한다. 이미 있으면 그대로 둔다 — 토지이용계획은 거의 바뀌지 않습니다.
     * 실패해도 예외를 던지지 않습니다.
     */
    @Transactional
    public List<LandUseResponse> ensureLandUse(Long propertyId) {
        return refresh(propertyId, false);
    }

    /** 사용자가 명시적으로 다시 받으려 할 때. */
    @Transactional
    public List<LandUseResponse> refresh(Long propertyId) {
        return refresh(propertyId, true);
    }

    private List<LandUseResponse> refresh(Long propertyId, boolean force) {
        // 다시 받기 전에 버린다 — 남겨 두면 방금 받은 값 대신 옛것을 돌려준다 (설계 I158)
        detailCache.evict(PropertyDetailCache.LAND_USE, propertyId);
        if (!force && !landUseRepository.findByPropertyId(propertyId).isEmpty()) {
            return find(propertyId);
        }
        if (!landUsePort.isEnabled()) {
            return find(propertyId);
        }
        final Optional<Property> found = propertyRepository.findById(propertyId);
        if (found.isEmpty()) {
            return List.of();
        }
        final Optional<String> pnu = resolvePnu(found.get());
        if (pnu.isEmpty()) {
            log.info("Skipping land use lookup - PNU not resolved. propertyId={}", propertyId);
            return find(propertyId);
        }
        final List<LandUse> fetched = landUsePort.fetch(pnu.get());
        if (fetched.isEmpty()) {
            return find(propertyId);
        }
        landUseRepository.replaceAll(propertyId, fetched);
        log.info("Land use stored. propertyId={}, pnu={}, items={}", propertyId, pnu.get(), fetched.size());
        return find(propertyId);
    }

    /** 저장된 PNU를 먼저 쓰고, 없으면 주소로 조립한다 (설계 I54). */
    private Optional<String> resolvePnu(Property property) {
        if (property.pnu() != null && property.pnu().length() == 19) {
            return Optional.of(property.pnu());
        }
        final String address = property.addressJibun() != null && !property.addressJibun().isBlank()
                ? property.addressJibun() : property.addressRoad();
        if (address == null || address.isBlank()) {
            return Optional.empty();
        }
        return geoService.geocode(address)
                .map(GeoSearchResult::pnu)
                .filter(value -> value != null && value.length() == 19);
    }
}
