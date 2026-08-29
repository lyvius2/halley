package banghak.home.halley.application.port.out.cache;

import banghak.home.halley.domain.itinerary.StartLocation;

import java.util.Optional;

/**
 * 임장 출발지 캐시 (설계 I52 — TTL 7일).
 *
 * <p>계획으로 저장되기 전의 입력값이라 DB에 둘 성격이 아니고, 매번 주소를 다시 검색하는 것도 번거롭다.
 * 사용자별로 마지막 출발지를 캐시해 임장 플래너를 열면 그대로 채워 준다.
 */
public interface StartLocationCache {

    Optional<StartLocation> get(long userId);

    void put(long userId, StartLocation location);
}
