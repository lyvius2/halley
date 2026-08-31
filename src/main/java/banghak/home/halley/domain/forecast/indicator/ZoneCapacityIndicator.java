package banghak.home.halley.domain.forecast.indicator;

import banghak.home.halley.domain.forecast.FactorWeight;
import banghak.home.halley.domain.forecast.ForecastDirection;
import banghak.home.halley.domain.forecast.PriceFactor;
import banghak.home.halley.domain.landuse.LandUse;
import banghak.home.halley.domain.landuse.LandUseConflict;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.Optional;

/**
 * 용도지역과 재건축 여력 (설계 I131).
 *
 * <p>용도지역은 <b>재건축 사업성의 뿌리</b>입니다. 제1종일반주거(상한 150%)와
 * 제3종일반주거(300%)는 같은 단지라도 이야기가 완전히 다릅니다.
 *
 * <p><b>지금은 상한만 압니다.</b> 현재 용적률은 건축물대장이 있어야 알 수 있어(구현 4-b),
 * 그때까지는 <b>여유를 계산하지 않고 방향도 주지 않습니다</b>(`FLAT`).
 * 상한만 알고 "여유가 있다"고 말하면 <b>없는 정보를 지어내는 것</b>입니다.
 *
 * <p>그래도 요인으로 내는 이유는 LLM에게 <b>맥락</b>이 되기 때문입니다 —
 * 준주거와 제1종일반주거는 같은 추세라도 다르게 읽어야 합니다.
 */
public class ZoneCapacityIndicator implements PriceIndicator {

    /** 용도지역명에 이 말이 들어가면 주거지역으로 본다. */
    private static final String RESIDENTIAL = "주거지역";
    private static final String COMMERCIAL = "상업지역";

    private final Map<String, BigDecimal> farLimits;

    /**
     * @param farLimits 용도지역명 → 용적률 상한(비율). <b>지자체 조례라 지역마다 다릅니다</b> —
     *                  `regulation_param`에서 주입합니다. 모르는 용도지역이면 상한 없이 이름만 씁니다
     */
    public ZoneCapacityIndicator(Map<String, BigDecimal> farLimits) {
        this.farLimits = farLimits == null ? Map.of() : farLimits;
    }

    @Override
    public String code() {
        return "ZONE_CAPACITY";
    }

    @Override
    public Optional<PriceFactor> evaluate(ForecastInput input) {
        return zoneOf(input).map(zone -> new PriceFactor(
                "용도지역",
                // 상한만으로는 방향을 말할 수 없다. 여유는 건축물대장이 붙어야 안다
                ForecastDirection.FLAT,
                FactorWeight.LOW,
                evidence(zone)));
    }

    /**
     * 실제 적용되는 용도지역 하나.
     *
     * <p><b>`INCLUDED`만 봅니다</b>(설계 I69). 저촉·접함은 옆 필지의 것이라
     * 이 매물에 적용되지 않습니다 — 다 보여 주면 "여러 용도지역에 걸쳐 있다"로 읽힙니다.
     */
    private Optional<LandUse> zoneOf(ForecastInput input) {
        if (input.landUses() == null) {
            return Optional.empty();
        }
        return input.landUses().stream()
                .filter(l -> l.conflict() == LandUseConflict.INCLUDED)
                .filter(l -> l.zoneName() != null)
                .filter(l -> l.zoneName().contains(RESIDENTIAL) || l.zoneName().contains(COMMERCIAL))
                .findFirst();
    }

    private String evidence(LandUse zone) {
        final BigDecimal limit = farLimits.get(zone.zoneName());
        if (limit == null) {
            // 상한을 모르면 <b>모른다고 씁니다</b>. 임의의 값을 넣지 않습니다
            return String.format("%s (용적률 상한은 지자체 조례 확인 필요)", zone.zoneName());
        }
        return String.format("%s · 용적률 상한 %s%% (현재 용적률은 건축물대장 연동 전이라 미산출)",
                zone.zoneName(),
                limit.multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_UP).toPlainString());
    }
}
