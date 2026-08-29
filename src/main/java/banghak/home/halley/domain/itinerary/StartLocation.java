package banghak.home.halley.domain.itinerary;

import java.math.BigDecimal;

/** 임장 출발지. 계획을 만들기 전 단계라 영속 대상이 아니며 사용자별 캐시에 담는다 (설계 I52). */
public record StartLocation(String address, BigDecimal lat, BigDecimal lng) {
}
