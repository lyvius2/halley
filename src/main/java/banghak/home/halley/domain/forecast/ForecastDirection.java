package banghak.home.halley.domain.forecast;

/**
 * 가격이 어느 쪽으로 움직일 것으로 보는가 (설계 I130).
 *
 * <p><b>{@code UNCERTAIN}은 1급 시민입니다.</b> 재료가 모자라면 모른다고 답할 수 있어야 합니다 —
 * 선택지에 없으면 사람도 모델도 <b>반드시 셋 중 하나를 고릅니다.</b>
 */
public enum ForecastDirection {

    UP("상승"),
    DOWN("하락"),
    FLAT("횡보"),
    /** 판단하지 않았다. <b>'약한 전망'이 아니라 '모른다'입니다.</b> */
    UNCERTAIN("판단 보류");

    private final String label;

    ForecastDirection(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
