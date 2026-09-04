package banghak.home.halley.application.service;

import banghak.home.halley.adapter.inbound.web.dto.PropertyRequest;
import banghak.home.halley.adapter.inbound.web.dto.PropertyResponse;
import banghak.home.halley.adapter.outbound.persistence.UserGroupRepository;
import banghak.home.halley.adapter.outbound.persistence.UserRepository;
import banghak.home.halley.application.port.out.external.KakaoLocalPort;
import banghak.home.halley.domain.geo.GeoSearchResult;
import banghak.home.halley.domain.geo.PoiResult;
import banghak.home.halley.domain.property.DealType;
import banghak.home.halley.support.GroupTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 같은 단지라도 <b>동이 다르면 자리가 다르다</b> (설계 I268).
 *
 * <p>주소검색은 동을 무시합니다 — 실제 카카오 응답으로 확인했습니다.
 *
 * <pre>
 * [address] 정릉동 1037        37.60163325, 127.01085810
 * [address] 정릉동 1037 102동   37.60163325, 127.01085810   ← 같다
 * [keyword] 한화포레나정릉 102동 37.60146161, 127.01087812
 * [keyword] 한화포레나정릉 104동 37.60153734, 127.01037873   ← 45m 떨어져 있다
 * </pre>
 */
@SpringBootTest
@ActiveProfiles("local")
@DisplayName("동별 좌표 (설계 I268)")
class BuildingCoordinateTest {

    /** 보정이 끼면 좌표를 다시 덮어쓸 수 있다. */
    @MockitoBean
    private PropertyEnrichmentService propertyEnrichmentService;

    /** 단지 대표점 — 주소검색이 돌려주는 값. */
    private static final BigDecimal BASE_LAT = new BigDecimal("37.60163325");
    private static final BigDecimal BASE_LNG = new BigDecimal("127.01085810");

    @TestConfiguration
    static class Kakao {

        @Bean
        @Primary
        KakaoLocalPort kakaoLocalPort() {
            return new KakaoLocalPort() {

                @Override
                public List<GeoSearchResult> searchAddress(String query) {
                    // 동을 붙이든 말든 같은 좌표를 준다 — 실제 카카오가 그렇다
                    return List.of(new GeoSearchResult("서울 성북구 정릉동 1037", null,
                            BASE_LAT, BASE_LNG, "1129013500", null));
                }

                @Override
                public List<PoiResult> searchCategory(String code, double x, double y, int radius) {
                    return List.of();
                }

                @Override
                public List<PoiResult> searchKeyword(String query, String code,
                                                     double x, double y, int radius) {
                    if (query.endsWith("102동")) {
                        return List.of(PoiResult.of("한화포레나정릉아파트 102동", null, 19,
                                "127.01087812", "37.60146161"));
                    }
                    if (query.endsWith("104동")) {
                        return List.of(PoiResult.of("한화포레나정릉아파트 104동", null, 43,
                                "127.01037873", "37.60153734"));
                    }
                    // 이름이 겹치는 <b>남의 단지</b>만 걸린 경우 (설계 I268)
                    if (query.endsWith("101동")) {
                        return List.of(PoiResult.of("래미안아트리치아파트 999동", null, 280,
                                "127.06220409", "37.60654172"));
                    }
                    return List.of();
                }
            };
        }
    }

    @Autowired private PropertyService propertyService;
    @Autowired private UserGroupRepository userGroupRepository;
    @Autowired private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        GroupTestSupport.loginAsGroupMember(userGroupRepository, userRepository);
    }

    @AfterEach
    void tearDown() {
        GroupTestSupport.logout();
    }

    @Test
    @DisplayName("102동과 104동은 서로 다른 자리에 찍힌다")
    void differentBuildingsGetDifferentCoordinates() {
        final PropertyResponse dong102 = propertyService.create(request("102동"));
        final PropertyResponse dong104 = propertyService.create(request("104동"));

        assertThat(dong102.lat()).isEqualByComparingTo("37.60146161");
        assertThat(dong104.lat()).isEqualByComparingTo("37.60153734");
        assertThat(dong102.lng())
                .as("같은 단지라고 좌표까지 같으면 핀이 포개져 뒤엣것은 누를 수도 없다")
                .isNotEqualByComparingTo(dong104.lng());
    }

    @Test
    @DisplayName("호까지 붙여도 동으로 찾는다")
    void stripsTheUnitNumber() {
        final PropertyResponse withUnit = propertyService.create(request("102동 1503호"));

        // 호까지 붙여 물으면 카카오가 아무것도 안 준다 — 실제로 그렇다
        assertThat(withUnit.lat()).isEqualByComparingTo("37.60146161");
    }

    @Test
    @DisplayName("이름이 안 맞으면 단지 좌표를 그대로 쓴다 — 남의 단지에 찍지 않는다")
    void keepsTheComplexCoordinateWhenTheNameDoesNotMatch() {
        // '101동' 을 물었는데 돌아온 것은 '999동' 이다. 680m 떨어진 남의 단지였다
        final PropertyResponse other = propertyService.create(request("101동"));

        assertThat(other.lat())
                .as("엉뚱한 곳에 찍힌 핀은 화면에 드러나지 않는다")
                .isEqualByComparingTo(BASE_LAT);
        assertThat(other.lng()).isEqualByComparingTo(BASE_LNG);
    }

    @Test
    @DisplayName("동을 모르면 단지 좌표를 쓴다")
    void keepsTheComplexCoordinateWithoutABuilding() {
        final PropertyResponse noDong = propertyService.create(request("1503호"));

        assertThat(noDong.lat()).isEqualByComparingTo(BASE_LAT);
    }

    @Test
    @DisplayName("동을 고치면 핀도 옮겨 간다 (설계 I269)")
    void movesThePinWhenTheBuildingIsEdited() {
        final PropertyResponse created = propertyService.create(request("102동"));
        assertThat(created.lat()).isEqualByComparingTo("37.60146161");

        // 수정 폼은 좌표 칸을 그대로 실어 보낸다 — 그래서 예전에는 핀이 안 움직였다
        final PropertyResponse edited = propertyService.update(created.id(),
                withCoordinates("104동", created.lat(), created.lng()), null);

        assertThat(edited.lat())
                .as("동을 고쳤는데 핀이 그대로면 같은 자리에 두 매물이 겹친다")
                .isEqualByComparingTo("37.60153734");
        assertThat(edited.lng()).isEqualByComparingTo("127.01037873");
    }

    @Test
    @DisplayName("사람이 찍은 좌표는 동을 고쳐도 지키다")
    void keepsCoordinatesTheUserMovedByHand() {
        final PropertyResponse created = propertyService.create(request("102동"));

        // 좌표를 손으로 고쳐 놓았다. 동 이름 때문에 그 뜻을 뒤집으면 안 된다
        final PropertyResponse edited = propertyService.update(created.id(),
                withCoordinates("104동", new BigDecimal("37.70000000"), new BigDecimal("127.20000000")),
                null);

        assertThat(edited.lat()).isEqualByComparingTo("37.70000000");
        assertThat(edited.lng()).isEqualByComparingTo("127.20000000");
    }

    @Test
    @DisplayName("동을 안 고쳤으면 좌표를 다시 찾지 않는다")
    void leavesCoordinatesAloneWhenTheBuildingIsUnchanged() {
        final PropertyResponse created = propertyService.create(request("102동"));

        final PropertyResponse edited = propertyService.update(created.id(),
                withCoordinates("102동", created.lat(), created.lng()), null);

        assertThat(edited.lat()).isEqualByComparingTo(created.lat());
    }

    private PropertyRequest withCoordinates(String dongHo, BigDecimal lat, BigDecimal lng) {
        return new PropertyRequest(
                "한화포레나정릉", dongHo, DealType.SALE, 800_000_000L, null,
                null, "서울시 성북구 정릉동 1037", lat, lng,
                null, new BigDecimal("84.9"), null, 5, null, null,
                null, null, 2020, null, null,
                null, null, null, 3, null, null, null, null, null, null, null, null, null,
                null, null, null);
    }

    private PropertyRequest request(String dongHo) {
        return new PropertyRequest(
                "한화포레나정릉", dongHo, DealType.SALE, 800_000_000L, null,
                null, "서울시 성북구 정릉동 1037", null, null,
                null, new BigDecimal("84.9"), null, 5, null, null,
                null, null, 2020, null, null,
                null, null, null, 3, null, null, null, null, null, null, null, null, null,
                null, null, null);
    }
}
