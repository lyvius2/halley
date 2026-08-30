package banghak.home.halley.domain.property;

/**
 * 거래 유형 (설계 I94).
 *
 * <p><b>월세는 취급하지 않습니다.</b> 이 앱은 집을 <b>사는</b> 결정을 돕습니다 — 매매와,
 * 매매 전에 거쳐 가는 전세까지입니다. 월세는 담보도 자산도 아니라 LTV·DSR·취득세 같은
 * 계산이 통째로 의미가 없습니다.
 */
public enum DealType {
    SALE,
    JEONSE
}
