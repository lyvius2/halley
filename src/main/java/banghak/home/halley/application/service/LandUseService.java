package banghak.home.halley.application.service;

import banghak.home.halley.adapter.inbound.web.dto.LandUseResponse;
import banghak.home.halley.adapter.outbound.persistence.LandUseRepository;
import banghak.home.halley.adapter.outbound.persistence.PropertyRepository;
import banghak.home.halley.application.port.out.cache.CachePort;
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

@Slf4j
@Service
public class LandUseService {

    /** 중개사·토지이용계획은 거의 안 바뀐다.  */
    private static final java.time.Duration DETAIL_TTL = java.time.Duration.ofHours(24);

    private final LandUsePort landUsePort;
    private final LandUseRepository landUseRepository;
    private final PropertyRepository propertyRepository;
    private final GeoService geoService;
    private final CachePort cache;
    private final ObjectMapper objectMapper;
    /** 사용자 요청은 여기를 지난다. 배경 보정(ensureLandUse)은 안 지난다  */
    private final PropertyAccessGuard propertyAccessGuard;

    public LandUseService(LandUsePort landUsePort,
                          LandUseRepository landUseRepository,
                          PropertyRepository propertyRepository,
                          GeoService geoService,
                          CachePort cache,
                          ObjectMapper objectMapper,
                          PropertyAccessGuard propertyAccessGuard) {
        this.landUsePort = landUsePort;
        this.landUseRepository = landUseRepository;
        this.propertyRepository = propertyRepository;
        this.geoService = geoService;
        this.cache = cache;
        this.objectMapper = objectMapper;
        this.propertyAccessGuard = propertyAccessGuard;
    }

    public List<LandUseResponse> find(Long propertyId) {
        propertyAccessGuard.require(propertyId);
        return load(propertyId);
    }

    /** 그룹 확인 없이 읽는다 — 배경 보정과 위 {@link #find} 가 함께 쓴다.  */
    private List<LandUseResponse> load(Long propertyId) {
        final Optional<String> cached = cache.get(CachePort.LAND_USE, String.valueOf(propertyId));
        if (cached.isPresent()) {
            try {
                return objectMapper.readValue(cached.get(), new TypeReference<List<LandUseResponse>>() {
                });
            } catch (RuntimeException e) {
                // 담아 둔 모양이 바뀌었을 수 있다. 버리고 DB 에서 다시 읽는다
                log.warn("Land-use cache unreadable - falling back to DB. propertyId={}, cause={}",
                        propertyId, e.getMessage());
                cache.evict(CachePort.LAND_USE, String.valueOf(propertyId));
            }
        }
        final List<LandUseResponse> fresh = landUseRepository.findByPropertyId(propertyId).stream()
                .map(LandUseResponse::from)
                .toList();
        cache.put(CachePort.LAND_USE, String.valueOf(propertyId), objectMapper.writeValueAsString(fresh), DETAIL_TTL);
        return fresh;
    }

    @Transactional
    public List<LandUseResponse> ensureLandUse(Long propertyId) {
        return refresh(propertyId, false);
    }

    /** 사용자가 명시적으로 다시 받으려 할 때.  */
    @Transactional
    public List<LandUseResponse> refresh(Long propertyId) {
        propertyAccessGuard.require(propertyId);
        return refresh(propertyId, true);
    }

    private List<LandUseResponse> refresh(Long propertyId, boolean force) {
        // 다시 받기 전에 버린다 — 남겨 두면 방금 받은 값 대신 옛것을 돌려준다
        cache.evict(CachePort.LAND_USE, String.valueOf(propertyId));
        if (!force && !landUseRepository.findByPropertyId(propertyId).isEmpty()) {
            return load(propertyId);
        }
        if (!landUsePort.isEnabled()) {
            return load(propertyId);
        }
        final Optional<Property> found = propertyRepository.findById(propertyId);
        if (found.isEmpty()) {
            return List.of();
        }
        final Optional<String> pnu = resolvePnu(found.get());
        if (pnu.isEmpty()) {
            log.info("Skipping land use lookup - PNU not resolved. propertyId={}", propertyId);
            return load(propertyId);
        }
        final List<LandUse> fetched = landUsePort.fetch(pnu.get());
        if (fetched.isEmpty()) {
            return load(propertyId);
        }
        landUseRepository.replaceAll(propertyId, fetched);
        log.info("Land use stored. propertyId={}, pnu={}, items={}", propertyId, pnu.get(), fetched.size());
        return find(propertyId);
    }

    /** 저장된 PNU를 먼저 쓰고, 없으면 주소로 조립한다.  */
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
