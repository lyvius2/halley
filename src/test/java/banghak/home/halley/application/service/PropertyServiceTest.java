package banghak.home.halley.application.service;

import banghak.home.halley.adapter.inbound.web.dto.PropertyRequest;
import banghak.home.halley.adapter.inbound.web.dto.PropertyResponse;
import banghak.home.halley.adapter.inbound.web.exception.InvalidPropertyRequestException;
import banghak.home.halley.adapter.inbound.web.exception.NotFoundListingsException;
import banghak.home.halley.adapter.outbound.persistence.PropertyRepository;
import banghak.home.halley.domain.property.DealType;
import banghak.home.halley.domain.property.ListingStatus;
import banghak.home.halley.domain.property.SourceType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

@SpringBootTest
@ActiveProfiles("local")
class PropertyServiceTest {

    @Autowired
    private PropertyService propertyService;

    @Autowired
    private PropertyRepository propertyRepository;

    @Test
    @DisplayName("매물을 등록하면 수기 출처/판매중 상태로 저장되고 목록에 조회된다")
    void createAndList() {
        // given
        final PropertyRequest request = request("한빛아파트", DealType.SALE, 550_000_000L);

        // when
        final PropertyResponse created = propertyService.create(request);

        // then
        assertThat(created.id()).isNotNull();
        assertThat(created.sourceType()).isEqualTo(SourceType.MANUAL);
        assertThat(created.listingStatus()).isEqualTo(ListingStatus.ACTIVE);
        assertThat(created.active()).isTrue();
        assertThat(propertyService.list()).extracting(PropertyResponse::name).contains("한빛아파트");
    }

    @Test
    @DisplayName("거래유형이 없으면 InvalidPropertyRequestException이 발생한다")
    void createRequiresDealType() {
        // given
        final PropertyRequest request = new PropertyRequest(
                "거래유형 없음", null, null, 100_000_000L, null, null,
                null, null, null, null,
                null, null, null, null, null, null,
                null, null, null, null, null,
                null, null, null, null, null);

        // when
        final InvalidPropertyRequestException ex = catchThrowableOfType(
                () -> propertyService.create(request), InvalidPropertyRequestException.class);

        // then
        assertThat(ex).isNotNull();
        assertThat(ex.getMessage()).isEqualTo("거래유형은 필수입니다");
    }

    @Test
    @DisplayName("매물명이 공백이면 InvalidPropertyRequestException이 발생한다")
    void createRequiresName() {
        // when
        final InvalidPropertyRequestException ex = catchThrowableOfType(
                () -> propertyService.create(request("  ", DealType.JEONSE, 300_000_000L)),
                InvalidPropertyRequestException.class);

        // then
        assertThat(ex).isNotNull();
        assertThat(ex.getMessage()).isEqualTo("매물명은 필수입니다");
    }

    @Test
    @DisplayName("매물을 수정하면 수정된 값이 반영되고 수기 출처는 유지된다")
    void getAndUpdate() {
        // given
        final PropertyResponse created = propertyService.create(request("수정 전", DealType.SALE, 400_000_000L));
        final PropertyRequest updateRequest = new PropertyRequest(
                "수정 후", "102동 501호", DealType.JEONSE, 300_000_000L, null, 15,
                "서울시 새주소", null, new BigDecimal("37.6"), new BigDecimal("127.1"),
                new BigDecimal("84.9"), new BigDecimal("59.9"), "중층", 5, 20, null,
                "2/1", "남향", 2021, null, null,
                new BigDecimal("1.1"), 500, "지역난방", 8, 700_000_000L);

        // when
        final PropertyResponse updated = propertyService.update(created.id(), updateRequest);

        // then
        assertThat(updated.name()).isEqualTo("수정 후");
        assertThat(updated.dealType()).isEqualTo(DealType.JEONSE);
        assertThat(updated.buildingCount()).isEqualTo(8);
        assertThat(updated.sourceType()).isEqualTo(SourceType.MANUAL);
    }

    @Test
    @DisplayName("존재하지 않는 매물을 수정하면 NotFoundListingsException이 발생한다")
    void updateNotFound() {
        // when
        final NotFoundListingsException ex = catchThrowableOfType(
                () -> propertyService.update(999_999L, request("없음", DealType.SALE, 100_000_000L)),
                NotFoundListingsException.class);

        // then
        assertThat(ex).isNotNull();
        assertThat(ex.getCode()).isEqualTo("NOT_FOUND");
    }

    @Test
    @DisplayName("매물을 삭제하면 저장소에서 제거된다")
    void delete() {
        // given
        final PropertyResponse created = propertyService.create(request("삭제 대상", DealType.SALE, 100_000_000L));

        // when
        propertyService.delete(created.id());

        // then
        assertThat(propertyRepository.findById(created.id())).isEmpty();
    }

    private PropertyRequest request(String name, DealType dealType, Long priceDeposit) {
        return new PropertyRequest(
                name, null, dealType, priceDeposit, null, null,
                "서울시 도로명주소", null, new BigDecimal("37.5"), new BigDecimal("127.0"),
                null, null, null, 5, null, null,
                null, null, 2018, null, null,
                null, null, null, 3, null);
    }
}
