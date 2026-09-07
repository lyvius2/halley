package banghak.home.halley.domain.reference;

/**
 * 월별 캐시에 담기는 거래 종류.
 *
 * 매매와 전세는 같은 서비스의 다른 오퍼레이션이고 응답 모양도 같습니다.
 * 캐시 키에 이 값을 넣지 않으면 둘이 서로를 덮어씁니다.
 */
public enum CachedDealType {
    /** 매매. 금액은 거래가. */
    TRADE,
    /** 순수 전세. 금액은 보증금입니다 — 매매가가 아닙니다. */
    JEONSE
}
