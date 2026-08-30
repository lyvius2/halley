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
        /** 이 매물이 속한 그룹 (설계 I87). 같은 그룹의 회원만 볼 수 있다 */
        Long groupId,
        /**
         * 등록자 닉네임 스냅샷 (설계 I88). 회원이 탈퇴하면 users 행이 사라지므로
         * 조회로는 이름을 알 수 없다. 화면에 남아야 하는 값이라 여기에 복사해 둔다
         */
        String createdByNickname,
        Long createdBy,
        Instant createdAt
) {

    /** 그룹을 옮긴다 (설계 I87). 54개 필드를 손으로 나열하면 순서 하나 틀려도 조용히 잘못 저장된다. */
    public Property withGroupId(Long groupId) {
        return new Property(id(), name(), dongHo(), dealType(), priceDeposit(), priceMonthly(), maintenanceFee(), addressRoad(), addressJibun(), lat(), lng(), areaSupplyM2(), areaExclusiveM2(), floorRaw(), floorNo(), floorTotal(), floorBand(), roomBath(), direction(), approvalYear(), moveInType(), moveInDate(), parkingPerHousehold(), totalHouseholds(), heatingType(), buildingCount(), kbPrice(), brokerageFee(), brokerageRate(), acquisitionTax(), propertyTax(), comprehensiveTax(), schoolName(), schoolWalkMinutes(), schoolSource(), pnu(), officialPrice(), officialPriceYear(), sourceType(), sourceUrl(), naverArticleNo(), rawPasteText(), parserVersion(), parseConfidence(), isDraft(), listingStatus(), active(), lastCheckedAt(), checkFailStreak(), soldDetectedAt(), groupId, createdByNickname(), createdBy(), createdAt());
    }

}
