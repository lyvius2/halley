package banghak.home.halley.application.service;

import banghak.home.halley.adapter.inbound.web.dto.PropertyRequest;
import banghak.home.halley.adapter.inbound.web.dto.ScoredPropertyPage;
import banghak.home.halley.adapter.outbound.persistence.UserGroupRepository;
import banghak.home.halley.adapter.outbound.persistence.UserRepository;
import banghak.home.halley.domain.property.DealType;
import banghak.home.halley.domain.property.ListingStatus;
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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 안 볼 매물을 <b>치워 둔다</b> (설계 I241).
 *
 * <p>지우는 것과 다릅니다 — 코멘트도 채점도 그대로 남고 언제든 되돌립니다.
 * 그래서 확인해야 할 것은 둘입니다: <b>정말 사라지는가</b>, 그리고 <b>정말 돌아오는가.</b>
 */
@SpringBootTest
@ActiveProfiles("local")
@DisplayName("매물 아카이빙 (설계 I241)")
class PropertyArchiveTest {

    @MockitoBean
    private PropertyEnrichmentService propertyEnrichmentService;

    @Autowired private PropertyListService propertyListService;
    @Autowired private PropertyService propertyService;
    @Autowired private UserGroupRepository userGroupRepository;
    @Autowired private UserRepository userRepository;

    private Long archivedId;
    private Long keptId;

    @BeforeEach
    void setUp() {
        GroupTestSupport.loginAsGroupMember(userGroupRepository, userRepository);
        archivedId = propertyService.create(request("치운매물", DealType.SALE)).id();
        keptId = propertyService.create(request("남긴매물", DealType.SALE)).id();
    }

    @AfterEach
    void tearDown() {
        GroupTestSupport.logout();
    }

    @Test
    @DisplayName("아카이빙하면 전체·매매·전세 탭에서 사라진다")
    void archivedLeavesTheNormalTabs() {
        propertyService.updateStatus(archivedId, ListingStatus.ARCHIVED);

        assertThat(ids(all())).doesNotContain(archivedId).contains(keptId);
        assertThat(ids(propertyListService.page(DealType.SALE, PropertySort.DEFAULT, 0, 30)))
                .as("거래유형 탭에서도 빠져야 한다 — 한 곳만 거르면 다른 탭에서 되살아난다")
                .doesNotContain(archivedId);
    }

    @Test
    @DisplayName("아카이빙 탭에는 치운 것만 보인다")
    void theArchiveTabShowsOnlyArchived() {
        propertyService.updateStatus(archivedId, ListingStatus.ARCHIVED);

        assertThat(ids(archived()))
                .containsExactly(archivedId);
    }

    @Test
    @DisplayName("되돌리면 원래 탭으로 돌아온다")
    void archivingIsReversible() {
        propertyService.updateStatus(archivedId, ListingStatus.ARCHIVED);
        propertyService.updateStatus(archivedId, ListingStatus.ACTIVE);

        assertThat(ids(all())).contains(archivedId);
        assertThat(ids(archived()))
                .as("되돌렸는데 아카이빙 탭에 남아 있으면 두 곳에 다 있는 셈이다")
                .doesNotContain(archivedId);
    }

    /**
     * 뱃지가 없으면 <b>치웠다는 사실 자체를 잊습니다</b> (설계 I241).
     */
    @Test
    @DisplayName("치운 건수는 어느 탭에서 봐도 실려 온다")
    void archivedCountRidesAlongOnEveryTab() {
        propertyService.updateStatus(archivedId, ListingStatus.ARCHIVED);

        assertThat(all().archivedTotal())
                .as("전체 탭에서도 치운 건수를 알아야 아카이빙 탭에 뱃지를 단다")
                .isEqualTo(1);
        assertThat(propertyListService.page(DealType.SALE, PropertySort.DEFAULT, 0, 30).archivedTotal())
                .isEqualTo(1);
    }

    @Test
    @DisplayName("치운 매물은 지도에도 안 찍힌다")
    void archivedLeavesTheMap() {
        propertyService.updateStatus(archivedId, ListingStatus.ARCHIVED);

        assertThat(propertyListService.pins(null))
                .as("목록에서 사라졌는데 지도에 남아 있으면 눌러도 카드가 없다")
                .extracting(p -> p.id())
                .doesNotContain(archivedId);
        assertThat(propertyListService.pins(null, true))
                .extracting(p -> p.id())
                .containsExactly(archivedId);
    }

    private ScoredPropertyPage all() {
        return propertyListService.page(null, PropertySort.DEFAULT, 0, 30);
    }

    private ScoredPropertyPage archived() {
        return propertyListService.page(null, PropertySort.DEFAULT, 0, 30, true);
    }

    private java.util.List<Long> ids(ScoredPropertyPage page) {
        return page.items().stream().map(r -> r.property().id()).toList();
    }

    private PropertyRequest request(String name, DealType dealType) {
        return new PropertyRequest(
                name, null, dealType, 500_000_000L, null,
                "서울시 도로명주소", null, new BigDecimal("37.5"), new BigDecimal("127.0"),
                new BigDecimal("84.9"), new BigDecimal("59.9"), null, 5, null, null,
                null, null, 2018, null, null,
                null, null, null, 3, null, null, null, null, null, null, null, null, null,
                null, null, null);
    }
}
