package banghak.home.halley.domain.property;

import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record Property(
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
        SourceType sourceType,
        String sourceUrl,
        String naverArticleNo,
        String rawPasteText,
        String parserVersion,
        JsonNode parseConfidence,
        boolean isDraft,
        ListingStatus listingStatus,
        boolean active,
        Instant lastCheckedAt,
        Integer checkFailStreak,
        Instant soldDetectedAt,
        Long createdBy,
        Instant createdAt
) {
}
