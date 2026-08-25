package banghak.home.halley.domain.scoring.support;

import banghak.home.halley.domain.property.DealType;
import banghak.home.halley.domain.property.FloorBand;
import banghak.home.halley.domain.property.ListingStatus;
import banghak.home.halley.domain.property.MoveInType;
import banghak.home.halley.domain.property.Property;
import banghak.home.halley.domain.property.SourceType;

import java.math.BigDecimal;
import java.time.LocalDate;

public final class PropertyBuilder {

    private Integer floorNo = 5;
    private FloorBand floorBand = null;
    private Integer approvalYear = 2020;
    private BigDecimal parkingPerHousehold = new BigDecimal("1.0");
    private MoveInType moveInType = MoveInType.IMMEDIATE;
    private LocalDate moveInDate = null;
    private Integer buildingCount = 3;
    private Long priceDeposit = 500_000_000L;

    public PropertyBuilder floorNo(Integer value) {
        this.floorNo = value;
        return this;
    }

    public PropertyBuilder floorBand(FloorBand value) {
        this.floorBand = value;
        return this;
    }

    public PropertyBuilder approvalYear(Integer value) {
        this.approvalYear = value;
        return this;
    }

    public PropertyBuilder parkingPerHousehold(BigDecimal value) {
        this.parkingPerHousehold = value;
        return this;
    }

    public PropertyBuilder moveInType(MoveInType value) {
        this.moveInType = value;
        return this;
    }

    public PropertyBuilder moveInDate(LocalDate value) {
        this.moveInDate = value;
        return this;
    }

    public PropertyBuilder buildingCount(Integer value) {
        this.buildingCount = value;
        return this;
    }

    public PropertyBuilder priceDeposit(Long value) {
        this.priceDeposit = value;
        return this;
    }

    public Property build() {
        return new Property(
                null, "테스트", null, DealType.SALE, priceDeposit, null, null,
                "서울시", null, null, null, null, null, null,
                floorNo, null, floorBand,
                null, null, approvalYear, moveInType, moveInDate,
                parkingPerHousehold, null, null, buildingCount, null,
                SourceType.MANUAL, null, null, null, null, null,
                false, ListingStatus.ACTIVE, true,
                null, 0, null, null, null);
    }
}
