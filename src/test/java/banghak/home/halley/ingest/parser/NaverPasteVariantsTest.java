package banghak.home.halley.ingest.parser;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 붙여넣기 <b>모양</b>이 달라도 읽어내는가 (설계 I233 · I283).
 *
 * <p>같은 화면인데 사람이 어디를 긁었는지에 따라 글의 모양이 달라집니다 — 들여쓰기가
 * 있기도 없기도 하고, 라벨에 버튼 글자가 붙기도 하고, 값끼리 공백 없이 이어지기도 합니다.
 * 여기서는 <b>그 모양들</b>을 최소한으로 잘라 두고 각각을 지킵니다.
 *
 * <p>매물 하나를 통째로 두지 않는 이유는, 그러면 <b>그 매물을 위한 시험</b>이 되어
 * 무엇을 지키는 시험인지 이름만 보고는 알 수 없기 때문입니다. 실제 붙여넣기 원문은
 * 개인정보가 있어 저장소에 두지 않습니다([I83]).
 */
@DisplayName("붙여넣기 모양 (설계 I283)")
class NaverPasteVariantsTest {

    private final NaverListingTextParser parser =
            new NaverListingTextParser(NaverListingTextParser.defaultExtractors());

    @Test
    @DisplayName("제목이 한 줄로 붙어 와도 단지명·동·거래유형을 가른다")
    void splitsAGluedTitle() {
        // given — 이름·동·유형·가격이 공백 없이 이어진다
        final ParsedListing parsed = parser.parse("""
                한빛단지 102동매매 8억 8,0003,481만원/3.3㎡평당가 도움말
                기본 정보
                매매가
                8억 8,000만원
                """);

        // then
        assertThat(parsed.field("name").value()).isEqualTo("한빛단지");
        assertThat(parsed.field("dongHo").value()).isEqualTo("102동");
        assertThat(parsed.field("dealType").value()).isEqualTo("매매");
        assertThat(parsed.field("priceDeposit").value()).isEqualTo(880_000_000L);
    }

    @Test
    @DisplayName("동이 없는 제목은 줄 전체가 단지명이다")
    void keepsTheWholeTitleWhenThereIsNoDong() {
        // given
        final ParsedListing parsed = parser.parse("한빛단지매매 5억\n");

        // then
        assertThat(parsed.field("name").value()).isEqualTo("한빛단지");
        assertThat(parsed.field("dongHo").value()).isNull();
    }

    @Test
    @DisplayName("해당층이 밴드(저·중·고)로 와도 읽는다")
    void readsAFloorBand() {
        // given
        final ParsedListing parsed = parser.parse("""
                한빛단지
                해당층/총층
                고/19층
                """);

        // then
        assertThat(parsed.field("floor").value()).isEqualTo("고/19");
    }

    @Test
    @DisplayName("라벨과 값이 한 줄에 붙고 뒤에 군더더기가 따라와도 금액만 읽는다")
    void readsAnAmountGluedToItsLabel() {
        // given — 대출 계산기 칸은 라벨과 값을 붙여 쓰고 뒤에 규제 표기가 따라온다
        final ParsedListing parsed = parser.parse("""
                한빛단지
                대출 금액
                KB시세 7억 3,000만원투기과열LTV 40%
                """);

        // then
        assertThat(parsed.field("kbPrice").value()).isEqualTo(730_000_000L);
    }

    @Test
    @DisplayName("라벨 뒤에 접기 버튼 글자가 붙어도 그 블록을 찾는다")
    void findsABlockWhoseLabelCarriesUiText() {
        // given — `배정 초등학교상세내용 숨기기`
        final ParsedListing parsed = parser.parse("""
                한빛단지
                배정 초등학교상세내용 숨기기
                서울한빛초등학교분류공립
                거리
                1179m도보 19분지도보기
                """);

        // then
        assertThat(parsed.field("school").value()).isEqualTo("서울한빛초등학교");
        assertThat(parsed.field("schoolMinutes").value()).isEqualTo(19);
    }

    @Test
    @DisplayName("중개보수는 줄 앞이 들여쓰여 있어도 읽는다")
    void readsAnIndentedBrokerageFee() {
        // given
        final ParsedListing parsed = parser.parse("""
                한빛단지
                중개 보수
                    최대 352만원 (VAT 별도)
                    상한 요율
                    0.4%
                """);

        // then
        assertThat(parsed.field("brokerageFee").value()).isEqualTo(3_520_000L);
        assertThat((BigDecimal) parsed.field("brokerageRate").value())
                .isEqualByComparingTo(new BigDecimal("0.4"));
    }

    @Test
    @DisplayName("역 이름에 노선이 붙어 와도 역만 모아 잇는다")
    void picksStationNamesOnly() {
        // given — 노선이 공백 없이 붙는 모양과 도보시간이 붙는 모양이 섞여 온다
        final ParsedListing parsed = parser.parse("""
                한빛단지
                지하철
                마들역7호선
                537m도보 9분
                노원역4호선7호선
                796m도보 14분
                버스
                """);

        // then
        assertThat(parsed.field("subway").value()).isEqualTo("마들역/노원역");
        assertThat(parsed.field("subwayMinutes").value()).isEqualTo(9);
    }

    /**
     * 공급면적이 전용면적 칸에 들어가면 국토부 실거래가 <b>한 건도 안 맞습니다</b> —
     * 그런데 화면에는 "거래 내역이 없습니다" 로만 보입니다 (설계 I233).
     *
     * <p>요약 줄에도 숫자가 있어(`재건축71㎡ (전용49)고/15층남향`) 거기서 집으면
     * <b>49 로 잘립니다.</b> 상세표의 49.94 를 읽어야 합니다.
     */
    @Test
    @DisplayName("요약 줄의 대략치가 아니라 상세표의 면적을 각자 제 자리에 넣는다")
    void readsBothAreasFromTheDetailTable() {
        // given
        final ParsedListing parsed = parser.parse("""
                한빛단지 714동
                매매 7억 5,000
                재건축71㎡ (전용49)고/15층남향
                공급면적
                71.02㎡면적 단위 변경평
                전용면적
                49.94㎡ (전용률 70%)
                """);

        // then
        final BigDecimal supply = (BigDecimal) parsed.field("areaSupplyM2").value();
        final BigDecimal exclusive = (BigDecimal) parsed.field("areaExclusiveM2").value();
        assertThat(supply).isEqualByComparingTo(new BigDecimal("71.02"));
        assertThat(exclusive).isEqualByComparingTo(new BigDecimal("49.94"));
        assertThat(exclusive).as("전용이 공급보다 클 수는 없다").isLessThan(supply);
    }
}
