package banghak.home.halley.application.service;

import banghak.home.halley.adapter.inbound.web.dto.PropertyPinResponse;
import banghak.home.halley.adapter.inbound.web.dto.ScoredPropertyPage;
import banghak.home.halley.adapter.inbound.web.dto.ScoredPropertyResponse;
import banghak.home.halley.adapter.outbound.persistence.UserGroupRepository;
import banghak.home.halley.adapter.outbound.persistence.UserRepository;
import banghak.home.halley.adapter.inbound.web.dto.PropertyRequest;
import banghak.home.halley.domain.property.DealType;
import banghak.home.halley.domain.property.PropertySort;
import banghak.home.halley.support.GroupTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 30건씩 잘라 보낸다 (설계 I240).
 *
 * <p>자르기의 위험은 <b>느려지는 것이 아니라 빠지는 것</b>입니다. 쪽마다 다른 순서로
 * 자르면 같은 매물이 두 쪽에 나오거나 <b>어느 쪽에도 안 나옵니다</b> — 그리고
 * 안 나온 것은 <b>아무도 모릅니다.</b> 이 프로젝트에서 가장 자주 겪은 실패의 모양입니다
 * ([I219] 10건만 받고 있었다).
 */
@SpringBootTest
@ActiveProfiles("local")
@DisplayName("매물 목록 쪽 나누기 (설계 I240)")
class PropertyListPagingTest {

    private static final int TOTAL = 35;

    @MockitoBean
    private PropertyEnrichmentService propertyEnrichmentService;

    @Autowired private PropertyListService propertyListService;
    @Autowired private PropertyService propertyService;
    @Autowired private UserGroupRepository userGroupRepository;
    @Autowired private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        GroupTestSupport.loginAsGroupMember(userGroupRepository, userRepository);
        for (int i = 0; i < TOTAL; i++) {
            propertyService.create(request("쪽매물" + String.format("%02d", i), 300_000_000L + i));
        }
    }

    @AfterEach
    void tearDown() {
        GroupTestSupport.logout();
    }

    @Test
    @DisplayName("첫 쪽은 30건이고, 더 있다고 말한다")
    void firstPageHoldsThirty() {
        final ScoredPropertyPage first = propertyListService.page(null, PropertySort.PRICE, 0, 30);

        assertThat(first.items()).hasSize(30);
        assertThat(first.total()).isEqualTo(TOTAL);
        assertThat(first.hasNext()).isTrue();
    }

    @Test
    @DisplayName("마지막 쪽은 남은 것만 담고, 더 없다고 말한다")
    void lastPageEndsTheScroll() {
        final ScoredPropertyPage last = propertyListService.page(null, PropertySort.PRICE, 1, 30);

        assertThat(last.items()).hasSize(TOTAL - 30);
        assertThat(last.hasNext())
                .as("여기서 더 있다고 하면 화면이 빈 쪽을 영원히 부른다")
                .isFalse();
    }

    /**
     * <b>이 테스트가 이 기능의 전부입니다.</b>
     *
     * <p>쪽을 다 이어 붙이면 자르기 전과 <b>같은 것이 같은 순서로</b> 나와야 합니다.
     * 하나라도 빠지면 화면에서는 그냥 없는 매물이 됩니다.
     */
    @Test
    @DisplayName("쪽을 다 이으면 하나도 빠지지 않는다")
    void everyPropertyAppearsExactlyOnce() {
        for (final PropertySort sort : PropertySort.values()) {
            final List<Long> joined = new ArrayList<>();
            for (int page = 0; ; page++) {
                final ScoredPropertyPage slice = propertyListService.page(null, sort, page, 30);
                slice.items().stream().map(r -> r.property().id()).forEach(joined::add);
                if (!slice.hasNext()) {
                    break;
                }
            }
            final List<Long> whole = propertyListService.page(null, sort, 0, 100).items().stream()
                    .map(r -> r.property().id())
                    .toList();

            assertThat(joined)
                    .as("%s 정렬 — 쪽을 이은 것이 전체와 같아야 한다", sort)
                    .containsExactlyElementsOf(whole)
                    .doesNotHaveDuplicates()
                    .hasSize(TOTAL);
        }
    }

    /**
     * 화면이 시키는 대로 다 주면 자른 의미가 없습니다 (설계 I240).
     */
    @Test
    @DisplayName("한 번에 전부 달라고 해도 100건까지만 준다")
    void refusesToHandOverEverythingAtOnce() {
        final ScoredPropertyPage huge = propertyListService.page(null, PropertySort.PRICE, 0, 100_000);

        assertThat(huge.size()).isEqualTo(100);
    }

    @Test
    @DisplayName("마지막 쪽을 지나쳐 물으면 빈 쪽이 온다")
    void pastTheEndIsEmptyNotAnError() {
        final ScoredPropertyPage beyond = propertyListService.page(null, PropertySort.PRICE, 99, 30);

        assertThat(beyond.items()).isEmpty();
        assertThat(beyond.hasNext()).isFalse();
        assertThat(beyond.total()).isEqualTo(TOTAL);
    }

    /**
     * 지도는 <b>잘리기 전</b>을 봐야 합니다 (설계 I240).
     */
    @Test
    @DisplayName("지도용 목록은 잘리지 않는다")
    void pinsCoverEverything() {
        final List<PropertyPinResponse> pins = propertyListService.pins(null);

        assertThat(pins)
                .as("지도가 첫 쪽만 찍으면 매물이 사라진 것처럼 보인다")
                .hasSize(TOTAL);
    }

    @Test
    @DisplayName("싼 순으로 세우면 쪽을 넘어가도 계속 싼 순이다")
    void orderHoldsAcrossPages() {
        final List<Long> prices = new ArrayList<>();
        for (int page = 0; ; page++) {
            final ScoredPropertyPage slice = propertyListService.page(null, PropertySort.PRICE, page, 30);
            slice.items().stream()
                    .map(ScoredPropertyResponse::property)
                    .forEach(p -> prices.add(p.priceDeposit()));
            if (!slice.hasNext()) {
                break;
            }
        }

        assertThat(prices)
                .as("쪽 안에서만 세우면 2쪽의 1등이 1쪽의 꼴찌보다 싸진다")
                .isSorted();
    }

    private PropertyRequest request(String name, long price) {
        return new PropertyRequest(
                name, null, DealType.SALE, price, null,
                "서울시 도로명주소", null, new BigDecimal("37.5"), new BigDecimal("127.0"),
                new BigDecimal("84.9"), new BigDecimal("59.9"), null, 5, null, null,
                null, null, 2018, null, null,
                null, null, null, 3, null, null, null, null, null, null, null, null, null,
                null, null, null);
    }
}
