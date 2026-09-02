package banghak.home.halley.application.service;

import banghak.home.halley.domain.forecast.indicator.ForecastInput;
import banghak.home.halley.domain.property.DealType;
import banghak.home.halley.domain.property.ListingStatus;
import banghak.home.halley.domain.property.Property;
import banghak.home.halley.domain.property.ReferenceTrade;
import banghak.home.halley.domain.property.SourceType;
import banghak.home.halley.domain.reference.CachedDealType;
import banghak.home.halley.domain.reference.MonthlyTrades;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 실거래 지표가 <b>왜</b> 빠졌는지 말한다 (설계 I253).
 *
 * <p>이유가 넷인데 화면도 로그도 아무 말이 없었습니다 — 자료를 못 받았는지,
 * 단지명이 안 맞는지, 평형이 다른지, 그냥 거래가 드문지.
 * <b>사람이 LLM 산문을 읽고 짐작해야 했습니다.</b>
 *
 * <p>실제로 겪은 화면이 그것입니다 — 모달에 실거래 8건이 버젓이 떠 있는데
 * 전망은 "인근 실거래 비교 자료가 없습니다"라고 했습니다.
 */
@SpringBootTest
@ActiveProfiles("local")
@DisplayName("실거래 지표가 빠진 이유 (설계 I253)")
class TradeGapNoteTest {

    @Autowired
    private PriceForecastService service;

    @Test
    @DisplayName("자료를 못 받았으면 그렇게 말한다")
    void saysWhenNothingWasFetched() {
        assertThat(service.tradeGapNote(input(List.of())))
                .isEqualTo("이 지역의 실거래 자료를 받아 두지 못해 실거래 지표를 넣지 못했습니다");
    }

    @Test
    @DisplayName("단지명이 안 맞으면 몇 건을 받았는지까지 말한다")
    void saysWhenTheNameDoesNotMatch() {
        final var note = service.tradeGapNote(input(months("다른단지", "58.59", 12, 3)));

        assertThat(note)
                .contains("실거래 36건을 받았지만")
                .contains("'송산'과 이름이 맞는 거래가 없어")
                .contains("국토부 표기가 다를 수 있습니다");
    }

    @Test
    @DisplayName("평형이 다르면 이름은 맞았다는 것까지 말한다")
    void saysWhenOnlyTheAreaIsOff() {
        final var note = service.tradeGapNote(input(months("송산", "114.8", 12, 3)));

        assertThat(note)
                .contains("실거래 36건 중 이름이 맞는 것은 36건이지만")
                .contains("전용 58.59㎡와 맞는 평형이 없어");
    }

    /**
     * <b>이 경우가 송산이었습니다.</b> 이름도 평형도 맞는데 거래 자체가 드물어
     * 창을 넓혀도 표본이 안 찼습니다.
     */
    @Test
    @DisplayName("거래가 흩어져 있으면 총량이 아니라 그 사실을 말한다")
    void saysWhenTradesAreSpreadThin() {
        // 12개월에 1건씩 — 총 12건이나 3개월 구간마다 3건을 못 채운다
        final var note = service.tradeGapNote(input(months("송산", "58.59", 12, 1)));

        assertThat(note)
                .as("12건이 있는데 '12건뿐'이라고 하면 틀린 말이다")
                .contains("이름·면적이 맞는 실거래는 12건이지만")
                .contains("비교 구간마다 3건을 채우지 못해")
                .contains("여러 달에 흩어져 있습니다");
    }

    private ForecastInput input(List<MonthlyTrades> months) {
        return new ForecastInput(property(), months, List.of(), List.of(), List.of(), null);
    }

    private List<MonthlyTrades> months(String name, String area, int monthCount, int perMonth) {
        return java.util.stream.IntStream.rangeClosed(1, monthCount)
                .mapToObj(m -> new MonthlyTrades("11110", YearMonth.now().minusMonths(m),
                        CachedDealType.TRADE,
                        Collections.nCopies(perMonth, new ReferenceTrade(
                                name, 700_000_000L, new BigDecimal(area), 5, LocalDate.now())),
                        Instant.now()))
                .toList();
    }

    private Property property() {
        return new Property(
                1L, "송산", null, DealType.SALE, 760_000_000L, null,
                null, "서울 노원구 상계동 100", new BigDecimal("37.5"), new BigDecimal("127.0"),
                null, new BigDecimal("58.59"), null, 18, 18, null, null, null,
                1998, null, null, null, 345, null, null, null,
                null, null, null, null, null,
                null, null, null,
                null, null, null,
                SourceType.MANUAL, null, null, null, null, null,
                false, ListingStatus.ACTIVE, true, null, 0, null, null, null, 1L, Instant.now());
    }
}
