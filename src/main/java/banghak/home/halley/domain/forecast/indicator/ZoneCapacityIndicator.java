package banghak.home.halley.domain.forecast.indicator;

import banghak.home.halley.domain.forecast.FactorWeight;
import banghak.home.halley.domain.forecast.ForecastDirection;
import banghak.home.halley.domain.forecast.PriceFactor;
import banghak.home.halley.domain.building.BuildingLedger;
import banghak.home.halley.domain.landuse.LandUse;
import banghak.home.halley.domain.landuse.LandUseConflict;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;

/**
 * 용도지역과 재건축 여력 (설계 I131).
 *
 * <p>용도지역은 <b>재건축 사업성의 뿌리</b>입니다. 제1종일반주거(상한 150%)와
 * 제3종일반주거(300%)는 같은 단지라도 이야기가 완전히 다릅니다.
 *
 * <p>건축물대장(I132)이 붙어 <b>현재 용적률을 실측</b>으로 압니다.
 * `여유 = 조례 상한 − 현재 용적률`.
 *
 * <h4>연식과 함께 봅니다</h4>
 *
 * <p><b>신축에 용적률 여유가 크다고 재건축 호재가 아닙니다.</b> 실측(동탄역시범호반써밋)에서
 * 2015년 준공에 여유가 127%p였는데, 재건축은 수십 년 뒤 이야기입니다 —
 * "여유를 안 쓰고 지었다"는 뜻일 뿐입니다.
 *
 * <p>그래서 <b>연식이 기준에 못 미치면 여유를 말하지 않고</b> 용도지역만 씁니다.
 * 안 그러면 신축 단지마다 "재건축 여력 큼"이 뜹니다.
 *
 * <p>대장을 못 받았을 때도 마찬가지입니다 — <b>근사값으로 채우지 않습니다.</b>
 */
public class ZoneCapacityIndicator implements PriceIndicator {

    /** 용도지역명에 이 말이 들어가면 주거지역으로 본다. */
    private static final String RESIDENTIAL = "주거지역";
    private static final String COMMERCIAL = "상업지역";

    private final Map<String, BigDecimal> farLimits;
    private final int redevelopmentAgeYears;

    /**
     * @param farLimits             용도지역명 → 용적률 상한(비율). <b>지자체 조례라 지역마다
     *                              다릅니다</b> — `regulation_param`에서 주입합니다
     * @param redevelopmentAgeYears 이 연식 이상이라야 용적률 여유를 말한다 (기본 30)
     */
    public ZoneCapacityIndicator(Map<String, BigDecimal> farLimits, int redevelopmentAgeYears) {
        this.farLimits = farLimits == null ? Map.of() : farLimits;
        this.redevelopmentAgeYears = redevelopmentAgeYears;
    }

    @Override
    public String code() {
        return "ZONE_CAPACITY";
    }

    @Override
    public Optional<PriceFactor> evaluate(ForecastInput input) {
        final Optional<LandUse> zone = zoneOf(input);
        if (zone.isEmpty()) {
            return Optional.empty();
        }
        final BigDecimal limit = farLimits.get(zone.get().zoneName());
        final BigDecimal headroom = headroom(limit, input.ledger());

        return Optional.of(new PriceFactor(
                "용도지역",
                // 여유가 크고 연식이 찼을 때만 방향을 준다. 그 밖에는 맥락으로만 쓴다
                headroom != null && headroom.signum() > 0
                        ? ForecastDirection.UP : ForecastDirection.FLAT,
                headroom != null ? FactorWeight.MEDIUM : FactorWeight.LOW,
                evidence(zone.get(), limit, input.ledger(), headroom)));
    }

    /**
     * 재건축 여력.
     *
     * <p><b>연식이 안 찼으면 내지 않습니다.</b> 신축의 용적률 여유는 재건축과 무관합니다.
     *
     * @return 상한을 모르거나, 대장이 없거나, 연식이 모자라면 {@code null}
     */
    private BigDecimal headroom(BigDecimal limitRatio, BuildingLedger ledger) {
        if (limitRatio == null || ledger == null || ledger.floorAreaRatio() == null) {
            return null;
        }
        final Integer age = ledger.ageYears(LocalDate.now());
        if (age == null || age < redevelopmentAgeYears) {
            return null;
        }
        return limitRatio.multiply(BigDecimal.valueOf(100)).subtract(ledger.floorAreaRatio());
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

    private String evidence(LandUse zone, BigDecimal limit, BuildingLedger ledger, BigDecimal headroom) {
        if (limit == null) {
            // 상한을 모르면 <b>모른다고 씁니다</b>. 임의의 값을 넣지 않습니다
            return String.format("%s (용적률 상한은 지자체 조례 확인 필요)", zone.zoneName());
        }
        final String cap = percent(limit.multiply(BigDecimal.valueOf(100)));
        if (ledger == null || ledger.floorAreaRatio() == null) {
            return String.format("%s · 용적률 상한 %s%% (현재 용적률은 건축물대장을 못 받아 미산출)",
                    zone.zoneName(), cap);
        }
        final String now = percent(ledger.floorAreaRatio());
        final Integer age = ledger.ageYears(LocalDate.now());
        if (headroom == null) {
            // 연식이 안 찼다 — 여유는 있지만 재건축 이야기가 아니다
            return String.format("%s · 상한 %s%% / 현재 %s%% (준공 %d년차, 재건축 논의 시점은 아님)",
                    zone.zoneName(), cap, now, age == null ? 0 : age);
        }
        return String.format("%s · 상한 %s%% / 현재 %s%% → 여유 %s%%p (준공 %d년차)",
                zone.zoneName(), cap, now, percent(headroom), age);
    }

    private String percent(BigDecimal value) {
        return value.setScale(0, RoundingMode.HALF_UP).toPlainString();
    }
}
