package banghak.home.halley.adapter.outbound.external;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("폴백 원인 로그 (설계 I140)")
class FallbackCauseTest {

    /** 실제 운영 로그에서 그대로 가져왔다. 키만 바꿨다. */
    private static final String REAL_LOG = "[429 Too Many Requests] during [GET] to "
            + "[https://apis.data.go.kr/1613000/RTMSDataSvcAptTradeDev/getRTMSDataSvcAptTradeDev"
            + "?serviceKey=9tT70SMRnHu4/piUAVCVyo3NJXWq2Dgi%2BsJ7Rrj03Fg%3D%3D"
            + "&LAWD_CD=41450&DEAL_YMD=202308] [MinistryReferenceFeignClient#fetchTrade]";

    @Test
    @DisplayName("인증키를 로그에 남기지 않는다 — 로그는 지우기 어렵고 여러 곳으로 복사된다")
    void masksServiceKey() {
        final String masked = FallbackCause.mask(REAL_LOG);

        assertThat(masked).doesNotContain("9tT70SMRnHu4");
        assertThat(masked).contains("serviceKey=***");
    }

    @Test
    @DisplayName("원인 추적에 필요한 것은 남긴다 — 어느 법정동, 어느 달, 무슨 응답")
    void keepsEverythingElse() {
        final String masked = FallbackCause.mask(REAL_LOG);

        assertThat(masked).contains("429 Too Many Requests");
        assertThat(masked).contains("LAWD_CD=41450");
        assertThat(masked).contains("DEAL_YMD=202308");
        assertThat(masked).contains("RTMSDataSvcAptTradeDev");
    }

    @Test
    @DisplayName("이름이 다른 비밀도 가린다")
    void masksOtherSecretNames() {
        assertThat(FallbackCause.mask("...?apiKey=abc123&x=1")).contains("apiKey=***").doesNotContain("abc123");
        assertThat(FallbackCause.mask("...?client_secret=zzz")).doesNotContain("zzz");
        assertThat(FallbackCause.mask("...?ACCESS_TOKEN=zzz")).doesNotContain("zzz");
    }

    @Test
    @DisplayName("비밀이 없으면 그대로 둔다")
    void leavesPlainMessages() {
        assertThat(FallbackCause.mask("connect timed out")).isEqualTo("connect timed out");
        assertThat(FallbackCause.mask(null)).isNull();
    }
}
