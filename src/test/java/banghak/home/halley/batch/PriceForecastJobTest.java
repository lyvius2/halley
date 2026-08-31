package banghak.home.halley.batch;

import banghak.home.halley.adapter.inbound.web.dto.PropertyRequest;
import banghak.home.halley.adapter.outbound.persistence.UserGroupRepository;
import banghak.home.halley.adapter.outbound.persistence.UserRepository;
import banghak.home.halley.application.service.PriceForecastService;
import banghak.home.halley.application.service.PropertyService;
import banghak.home.halley.domain.property.DealType;
import banghak.home.halley.domain.property.ListingStatus;
import banghak.home.halley.support.GroupTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("local")
@Transactional
@DisplayName("월간 전망 재계산 (설계 I143)")
class PriceForecastJobTest {

    @MockitoBean
    private PriceForecastService priceForecastService;

    @Autowired
    private PriceForecastJob job;

    @Autowired
    private PropertyService propertyService;

    @Autowired
    private UserGroupRepository userGroupRepository;

    @Autowired
    private UserRepository userRepository;

    private final Set<Long> refreshed = ConcurrentHashMap.newKeySet();

    @BeforeEach
    void setUp() {
        refreshed.clear();
        GroupTestSupport.loginAsGroupMember(userGroupRepository, userRepository);
        when(priceForecastService.refresh(anyLong())).thenAnswer(inv -> {
            refreshed.add(inv.getArgument(0));
            return Optional.empty();
        });
    }

    @AfterEach
    void tearDown() {
        GroupTestSupport.logout();
    }

    @Test
    @DisplayName("팔린 매물과 작성 중은 건너뛴다 — 안 쓰는 전망에 국토부 호출을 쓸 이유가 없다")
    void skipsSoldOutAndDrafts() {
        // given
        final Long alive = create("살아있는단지");
        final Long sold = create("팔린단지");
        propertyService.updateStatus(sold, ListingStatus.SOLD_OUT);

        // when
        job.refreshAll();

        // then
        assertThat(refreshed).contains(alive);
        assertThat(refreshed).doesNotContain(sold);
    }

    @Test
    @DisplayName("한 매물이 터져도 나머지는 돈다 — 한 건 때문에 그달 전체를 거를 수 없다")
    void oneFailureDoesNotStopTheRest() {
        // given
        final Long first = create("첫단지");
        final Long second = create("둘째단지");
        when(priceForecastService.refresh(anyLong())).thenAnswer(inv -> {
            final Long id = inv.getArgument(0);
            if (id.equals(first)) {
                throw new IllegalStateException("국토부가 안 받는다");
            }
            refreshed.add(id);
            return Optional.empty();
        });

        // when
        job.refreshAll();

        // then
        assertThat(refreshed).contains(second);
    }

    private Long create(String name) {
        return propertyService.create(new PropertyRequest(
                name, null, DealType.SALE, 1_140_000_000L, null,
                "서울시 도로명주소", null, new BigDecimal("37.5"), new BigDecimal("127.0"),
                null, null, null, 5, null, null,
                null, null, 2018, null, null,
                null, null, null, 3, null, null, null, null, null, null, null, null, null,
                null, null, null)).id();
    }
}
