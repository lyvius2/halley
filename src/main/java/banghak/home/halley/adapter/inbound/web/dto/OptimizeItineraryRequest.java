package banghak.home.halley.adapter.inbound.web.dto;

import banghak.home.halley.domain.itinerary.TravelMode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * 경로를 짜 달라는 요청.
 *
 * @param visitDate    임장 날짜. <b>시각만으로는 요일을 모릅니다</b> — 화요일 14시와
 *                     일요일 14시는 다른 길입니다 (설계 I196). null 이면 지금 기준
 * @param windowStart  출발 시각. null 이면 09:00
 * @param stayMinutes  매물 한 곳당 머무는 시간. 뒤 구간의 출발 시각을 미룹니다
 */
public record OptimizeItineraryRequest(
        List<Long> propertyIds,
        TravelMode travelMode,
        BigDecimal startLat,
        BigDecimal startLng,
        LocalDate visitDate,
        LocalTime windowStart,
        Integer stayMinutes
) {
}
