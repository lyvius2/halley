package banghak.home.halley.domain.reference;

/**
 * 월별 캐시에 담기는 거래 종류 (설계 I131).
 *
 * <p>매매와 전세는 <b>같은 서비스의 다른 오퍼레이션</b>이고 응답 모양도 같습니다.
 * 캐시 키에 이 값을 넣지 않으면 <b>둘이 서로를 덮어씁니다.</b>
 */
public enum CachedDealType {
    /** 매매. 금액은 거래가. */
    TRADE,
    /** 순수 전세. <b>금액은 보증금입니다</b> — 매매가가 아닙니다. */
    JEONSE
}
