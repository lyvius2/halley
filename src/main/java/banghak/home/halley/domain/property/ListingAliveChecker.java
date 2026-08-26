package banghak.home.halley.domain.property;

/**
 * 매물 생존 확인 전략 인터페이스. 네이버 판정 로직은 구현 변경에 취약하므로 전략 교체를 위해 분리한다.
 */
public interface ListingAliveChecker {

    ListingCheckResult check(String sourceUrl);
}
