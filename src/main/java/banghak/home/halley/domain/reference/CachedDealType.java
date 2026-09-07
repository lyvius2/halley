package banghak.home.halley.domain.reference;

/** 월별 캐시에 담기는 거래 종류. */
public enum CachedDealType {
 /** 매매. 금액은 거래가. */
    TRADE,
 /** 순수 전세. 금액은 보증금입니다. 매매가가 아닙니다. */
    JEONSE
}
