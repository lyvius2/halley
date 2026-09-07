package banghak.home.halley.adapter.inbound.web.dto;

import banghak.home.halley.domain.property.DealType;

import java.math.BigDecimal;

/** 지도에 찍을 것만 담은 매물. */
public record PropertyPinResponse(
        Long id,
        String name,
 /** 같은 단지 매물을 지도에서 가르는 유일한 값. */
        String dongHo,
        DealType dealType,
        Long priceDeposit,
        BigDecimal areaExclusiveM2,
        BigDecimal lat,
        BigDecimal lng,
        boolean visited,
        boolean visitedByComfort,
        boolean active,
        boolean draft
) {
}
