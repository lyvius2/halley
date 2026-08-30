package banghak.home.halley.application.service;

import banghak.home.halley.adapter.inbound.web.dto.PropertyRequest;
import banghak.home.halley.adapter.inbound.web.dto.PropertyResponse;
import banghak.home.halley.adapter.outbound.persistence.ListingCheckLogRepository;
import banghak.home.halley.adapter.outbound.persistence.PropertyRepository;
import banghak.home.halley.adapter.outbound.persistence.UserRepository;
import banghak.home.halley.application.port.out.cache.EditVersionStore;
import banghak.home.halley.domain.geo.GeoSearchResult;
import banghak.home.halley.domain.property.DealType;
import banghak.home.halley.domain.property.Property;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PropertyServiceGeocodeTest {

    private final PropertyRepository propertyRepository = mock(PropertyRepository.class);
    private final ListingCheckLogRepository listingCheckLogRepository = mock(ListingCheckLogRepository.class);
    private final EditVersionStore editVersionStore = mock(EditVersionStore.class);
    private final GeoService geoService = mock(GeoService.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);

    private final UserRepository userRepository = mock(UserRepository.class);
    private final AgentService agentService = mock(AgentService.class);
    private final PropertyAccessGuard propertyAccessGuard = mock(PropertyAccessGuard.class);

    private final PropertyService propertyService = new PropertyService(
            propertyAccessGuard, propertyRepository, userRepository, agentService,
            listingCheckLogRepository, editVersionStore, geoService, eventPublisher);

    @BeforeEach
    void stubGroup() {
        // 매물은 그룹에 딸린다. 이 테스트는 지오코딩만 보므로 그룹은 있다고 친다 (설계 I87)
        when(propertyAccessGuard.currentGroupId()).thenReturn(Optional.of(1L));
    }

    @Test
    @DisplayName("좌표가 없고 주소가 있으면 주소로 지오코딩한 좌표가 저장된다")
    void createGeocodesAddressWhenCoordsMissing() {
        // given
        final PropertyRequest request = requestWithoutCoords("서울시 성북구 석관동 407");
        when(geoService.geocode("서울시 성북구 석관동 407"))
                .thenReturn(Optional.of(new GeoSearchResult(
                        "서울 성북구 석관동 407", "서울시 성북구 석관동 407",
                        new BigDecimal("37.612345"), new BigDecimal("127.065432"), "1129013600", null)));
        when(propertyRepository.save(any(Property.class))).thenAnswer(inv -> inv.getArgument(0));

        // when
        final PropertyResponse created = propertyService.create(request);

        // then
        final Property saved = capturedSaved();
        assertThat(saved.lat()).isEqualByComparingTo("37.612345");
        assertThat(saved.lng()).isEqualByComparingTo("127.065432");
        assertThat(created.lat()).isEqualByComparingTo("37.612345");
        verify(geoService).geocode("서울시 성북구 석관동 407");
    }

    @Test
    @DisplayName("좌표가 명시되어 있으면 주소가 있어도 지오코딩하지 않는다")
    void createKeepsExplicitCoords() {
        // given
        final PropertyRequest request = requestWithCoords("서울시 성북구 석관동 407");
        when(propertyRepository.save(any(Property.class))).thenAnswer(inv -> inv.getArgument(0));

        // when
        propertyService.create(request);

        // then
        final Property saved = capturedSaved();
        assertThat(saved.lat()).isEqualByComparingTo("37.500000");
        assertThat(saved.lng()).isEqualByComparingTo("127.000000");
        verify(geoService, never()).geocode(any());
    }

    @Test
    @DisplayName("지오코딩에 실패해도 좌표 없이 매물이 등록된다")
    void createFallsBackToNullCoordsOnGeocodeFailure() {
        // given
        final PropertyRequest request = requestWithoutCoords("서울시 성북구 석관동 407");
        when(geoService.geocode(any())).thenReturn(Optional.empty());
        when(propertyRepository.save(any(Property.class))).thenAnswer(inv -> inv.getArgument(0));

        // when
        final PropertyResponse created = propertyService.create(request);

        // then
        final Property saved = capturedSaved();
        assertThat(saved.lat()).isNull();
        assertThat(saved.lng()).isNull();
        assertThat(created.lat()).isNull();
        verify(geoService).geocode("서울시 성북구 석관동 407");
    }

    private Property capturedSaved() {
        final ArgumentCaptor<Property> captor = ArgumentCaptor.forClass(Property.class);
        verify(propertyRepository).save(captor.capture());
        return captor.getValue();
    }

    private static PropertyRequest requestWithCoords(String address) {
        return new PropertyRequest(
                "한빛아파트", null, DealType.SALE, 550_000_000L, null, null,
                address, address, new BigDecimal("37.5"), new BigDecimal("127.0"),
                null, null, null, 5, null, null,
                null, null, 2018, null, null,
                null, null, null, 3, null, null, null, null, null, null, null, null, null,
                null, null, null);
    }

    private static PropertyRequest requestWithoutCoords(String address) {
        return new PropertyRequest(
                "한빛아파트", null, DealType.SALE, 550_000_000L, null, null,
                address, address, null, null,
                null, null, null, 5, null, null,
                null, null, 2018, null, null,
                null, null, null, 3, null, null, null, null, null, null, null, null, null,
                null, null, null);
    }
}
