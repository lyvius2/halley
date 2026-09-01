package banghak.home.halley.application.service;

import banghak.home.halley.adapter.inbound.web.dto.PropertyRequest;
import banghak.home.halley.adapter.outbound.persistence.LandUseRepository;
import banghak.home.halley.adapter.outbound.persistence.UserGroupRepository;
import banghak.home.halley.adapter.outbound.persistence.UserRepository;
import banghak.home.halley.application.port.out.cache.CachePort;
import banghak.home.halley.domain.landuse.LandUse;
import banghak.home.halley.domain.landuse.LandUseConflict;
import banghak.home.halley.domain.property.DealType;
import banghak.home.halley.support.GroupTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("local")
@DisplayName("토지이용계획 캐시 (설계 I158)")
class LandUseCacheTest {

    @Autowired
    private LandUseService landUseService;

    @Autowired
    private LandUseRepository landUseRepository;

    @Autowired
    private CachePort cache;

    @Autowired
    private PropertyService propertyService;

    @Autowired
    private UserGroupRepository userGroupRepository;

    @Autowired
    private UserRepository userRepository;

    private Long propertyId;

    @BeforeEach
    void setUp() {
        GroupTestSupport.loginAsGroupMember(userGroupRepository, userRepository);
        propertyId = propertyService.create(new PropertyRequest(
                "캐시단지", null, DealType.SALE, 1_000_000_000L, null,
                "서울시 도로명주소", null, new BigDecimal("37.5"), new BigDecimal("127.0"),
                null, null, null, 5, null, null, null, null, 2018, null, null,
                null, null, null, 3, null, null, null, null, null, null, null, null, null,
                null, null, null)).id();
        cache.evict(CachePort.LAND_USE, String.valueOf(propertyId));
    }

    @AfterEach
    void tearDown() {
        cache.evict(CachePort.LAND_USE, String.valueOf(propertyId));
        GroupTestSupport.logout();
    }

    @Test
    @DisplayName("두 번째 조회는 캐시가 받는다")
    void secondReadHitsCache() {
        landUseRepository.replaceAll(propertyId, List.of(new LandUse(null, propertyId, "UQA111",
                "제3종일반주거지역", LandUseConflict.INCLUDED, "필지", Instant.now())));

        assertThat(landUseService.find(propertyId)).hasSize(1);
        assertThat(cache.get(CachePort.LAND_USE, String.valueOf(propertyId))).isPresent();

        // DB 에서 지워도 캐시가 살아 있으면 그대로 나온다 — 캐시가 받고 있다는 증거다
        landUseRepository.deleteByPropertyId(propertyId);
        assertThat(landUseService.find(propertyId)).hasSize(1);
    }

    /**
     * <b>다시 받을 때 캐시를 안 버리면 방금 받은 값 대신 옛것이 나온다.</b>
     * 사용자가 '지금 조회'를 눌렀는데 화면이 안 바뀌는 증상이 된다.
     */
    @Test
    @DisplayName("다시 조회하면 캐시를 버린다 — 안 그러면 옛것이 계속 나온다")
    void refreshEvictsCache() {
        landUseRepository.replaceAll(propertyId, List.of(new LandUse(null, propertyId, "UQA111",
                "제3종일반주거지역", LandUseConflict.INCLUDED, "필지", Instant.now())));
        landUseService.find(propertyId);
        assertThat(cache.get(CachePort.LAND_USE, String.valueOf(propertyId))).isPresent();

        // 저장된 것이 바뀐 상황
        landUseRepository.deleteByPropertyId(propertyId);
        landUseService.refresh(propertyId);

        assertThat(landUseService.find(propertyId)).isEmpty();
    }
}
