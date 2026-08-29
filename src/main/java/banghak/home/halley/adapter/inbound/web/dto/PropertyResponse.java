package banghak.home.halley.adapter.inbound.web.dto;

import banghak.home.halley.domain.property.DealType;
import banghak.home.halley.domain.property.FloorBand;
import banghak.home.halley.domain.property.ListingStatus;
import banghak.home.halley.domain.property.MoveInType;
import banghak.home.halley.domain.property.Property;
import banghak.home.halley.domain.property.SchoolSource;
import banghak.home.halley.domain.property.SourceType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record PropertyResponse(
        Long id,
        String name,
        String dongHo,
        DealType dealType,
        Long priceDeposit,
        Long priceMonthly,
        Integer maintenanceFee,
        String addressRoad,
        String addressJibun,
        BigDecimal lat,
        BigDecimal lng,
        BigDecimal areaSupplyM2,
        BigDecimal areaExclusiveM2,
        String floorRaw,
        Integer floorNo,
        Integer floorTotal,
        FloorBand floorBand,
        String roomBath,
        String direction,
        Integer approvalYear,
        MoveInType moveInType,
        LocalDate moveInDate,
        BigDecimal parkingPerHousehold,
        Integer totalHouseholds,
        String heatingType,
        Integer buildingCount,
        Long kbPrice,
        Long brokerageFee,
        BigDecimal brokerageRate,
        Long acquisitionTax,
        Long propertyTax,
        String comprehensiveTax,
        String schoolName,
        Integer schoolWalkMinutes,
        SchoolSource schoolSource,
        String pnu,
        Long officialPrice,
        Integer officialPriceYear,
        SourceType sourceType,
        String sourceUrl,
        ListingStatus listingStatus,
        boolean active,
        boolean isDraft,
        Instant soldDetectedAt,
        Instant createdAt,
        Long createdBy,
        String createdByNickname,
        Long editVersion
) {

    public static PropertyResponse from(Property p, String createdByNickname, Long editVersion) {
        return new PropertyResponse(
                p.id(), p.name(), p.dongHo(), p.dealType(),
                p.priceDeposit(), p.priceMonthly(), p.maintenanceFee(),
                p.addressRoad(), p.addressJibun(), p.lat(), p.lng(),
                p.areaSupplyM2(), p.areaExclusiveM2(), p.floorRaw(), p.floorNo(), p.floorTotal(), p.floorBand(),
                p.roomBath(), p.direction(), p.approvalYear(), p.moveInType(), p.moveInDate(),
                p.parkingPerHousehold(), p.totalHouseholds(), p.heatingType(), p.buildingCount(), p.kbPrice(),
                p.brokerageFee(), p.brokerageRate(), p.acquisitionTax(), p.propertyTax(), p.comprehensiveTax(),
                p.schoolName(), p.schoolWalkMinutes(), p.schoolSource(), p.pnu(),
                p.officialPrice(), p.officialPriceYear(),
                p.sourceType(), p.sourceUrl(), p.listingStatus(), p.active(), p.isDraft(), p.soldDetectedAt(), p.createdAt(),
                p.createdBy(), createdByNickname, editVersion);
    }
}
