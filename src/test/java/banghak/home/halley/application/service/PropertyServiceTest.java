package banghak.home.halley.application.service;

import banghak.home.halley.adapter.inbound.web.dto.PropertyRequest;
import banghak.home.halley.adapter.inbound.web.dto.PropertyResponse;
import banghak.home.halley.config.exception.InvalidPropertyRequestException;
import banghak.home.halley.config.exception.NotFoundListingsException;
import banghak.home.halley.adapter.outbound.persistence.PropertyRepository;
import banghak.home.halley.domain.property.DealType;
import banghak.home.halley.domain.property.ListingCheckLog;
import banghak.home.halley.domain.property.ListingStatus;
import banghak.home.halley.domain.property.ListingVerdict;
import banghak.home.halley.domain.property.SourceType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
                null, null, null, null, null,
                null, null, null);

        // when
        final InvalidPropertyRequestException ex = assertThrows(
                InvalidPropertyRequestException.class, () -> propertyService.create(request));

        // then
        assertThat(ex).isNotNull();
        assertThat(ex.getMessage()).isEqualTo("거래유형은 필수입니다");
    }

    @Test
    @DisplayName("판매완료 매물을 판매중으로 복구하면 실패 스트릭이 리셋된다")
    void restoreSoldOut() {
        // given
        final PropertyResponse created = propertyService.create(request("복구 테스트", DealType.SALE, 400_000_000L));
        propertyRepository.updateListingStatus(created.id(), ListingStatus.SOLD_OUT, false, 3, Instant.now());
        assertThat(propertyRepository.findById(created.id()).orElseThrow().listingStatus())
                .isEqualTo(ListingStatus.SOLD_OUT);

        // when
        final PropertyResponse restored = propertyService.updateStatus(created.id(), ListingStatus.ACTIVE);

        // then
        assertThat(restored.listingStatus()).isEqualTo(ListingStatus.ACTIVE);
        final var saved = propertyRepository.findById(created.id()).orElseThrow();
        assertThat(saved.active()).isTrue();
        assertThat(saved.checkFailStreak()).isZero();
        assertThat(saved.soldDetectedAt()).isNull();
    }

    @Test
    @DisplayName("점검 이력과 최근 판매완료 목록을 조회한다")
    void checkLogsAndRecentSoldOut() {
        // given
        final PropertyResponse created = propertyService.create(request("이력 테스트", DealType.SALE, 400_000_000L));
        propertyRepository.updateListingStatus(created.id(), ListingStatus.SOLD_OUT, false, 3, Instant.now());

        // when
        final var logs = propertyService.checkLogs(created.id());
        final var recent = propertyService.recentSoldOut();

        // then
        assertThat(logs).isEmpty();
        assertThat(recent).extracting(PropertyResponse::id).contains(created.id());
    }

    @Test
    @DisplayName("매물명이 공백이면 InvalidPropertyRequestException이 발생한다")
    void createRequiresName() {
        // when
        final InvalidPropertyRequestException ex = assertThrows(
                InvalidPropertyRequestException.class,
                () -> propertyService.create(request("  ", DealType.JEONSE, 300_000_000L)));

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
                new BigDecimal("1.1"), 500, "지역난방", 8, 700_000_000L,
                null, null, null);

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
        final NotFoundListingsException ex = assertThrows(
                NotFoundListingsException.class,
                () -> propertyService.update(999_999L, request("없음", DealType.SALE, 100_000_000L)));

        // then
        assertThat(ex).isNotNull();
        assertThat(ex.getCode()).isEqualTo("NOT_FOUND");
    }

    @Test
    @DisplayName("rawPasteText가 있으면 PASTE 출처로 저장하고 원문을 보존한다")
    void createFromPastePreservesRawText() {
        // given
        final PropertyRequest base = request("한빛아파트", DealType.SALE, 500_000_000L);
        final PropertyRequest pasteRequest = new PropertyRequest(
                base.name(), base.dongHo(), base.dealType(), base.priceDeposit(), base.priceMonthly(),
                base.maintenanceFee(), base.addressRoad(), base.addressJibun(), base.lat(), base.lng(),
                base.areaSupplyM2(), base.areaExclusiveM2(), base.floorRaw(), base.floorNo(), base.floorTotal(),
                base.floorBand(), base.roomBath(), base.direction(), base.approvalYear(), base.moveInType(),
                base.moveInDate(), base.parkingPerHousehold(), base.totalHouseholds(), base.heatingType(),
                base.buildingCount(), base.kbPrice(),
                null, "A12345678", "매매\n매매가\n15억");

        // when
        final PropertyResponse created = propertyService.create(pasteRequest);

        // then
        assertThat(created.sourceType()).isEqualTo(SourceType.PASTE);
        assertThat(propertyRepository.findById(created.id()).orElseThrow().rawPasteText())
                .isEqualTo("매매\n매매가\n15억");
        assertThat(propertyRepository.findById(created.id()).orElseThrow().naverArticleNo())
                .isEqualTo("A12345678");
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
                null, null, null, 3, null,
                null, null, null);
    }
}
