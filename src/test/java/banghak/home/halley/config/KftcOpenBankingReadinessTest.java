package banghak.home.halley.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("금융결제원 오픈뱅킹 준비 상태")
class KftcOpenBankingReadinessTest {

    @Test
    @DisplayName("필수 설정과 서비스 확인이 끝나면 연동 준비 상태가 된다")
    void isReadyWhenAllRequirementsAreConfigured() {
        // given
        final KftcOpenBankingProperties properties = configuredProperties();

        // when
        final KftcOpenBankingReadiness readiness = properties.readiness();

        // then
        assertThat(readiness.isReady()).isTrue();
        assertThat(readiness.missingRequirements()).isEmpty();
    }

    @Test
    @DisplayName("서비스 신청 확인이나 환경 변수가 없으면 누락 항목을 알린다")
    void reportsMissingRequirements() {
        // given
        final KftcOpenBankingProperties properties = new KftcOpenBankingProperties();
        properties.setEnabled(true);
        properties.setApiKey("api-key");

        // when
        final KftcOpenBankingReadiness readiness = properties.readiness();

        // then
        assertThat(readiness.isReady()).isFalse();
        assertThat(readiness.missingRequirements())
                .contains("KFTC_CLIENT_ID", "KFTC_CLIENT_SECRET", "KFTC_CLIENT_USE_CODE",
                        "KFTC_CALLBACK_URL", "KFTC_OAUTH_SERVICE_CONFIRMED=true",
                        "KFTC_BALANCE_INQUIRY_SERVICE_CONFIRMED=true");
    }

    @Test
    @DisplayName("연동이 비활성화되면 설정이 있어도 준비 상태가 아니다")
    void isNotReadyWhenIntegrationIsDisabled() {
        // given
        final KftcOpenBankingProperties properties = configuredProperties();
        properties.setEnabled(false);

        // when
        final KftcOpenBankingReadiness readiness = properties.readiness();

        // then
        assertThat(readiness.isReady()).isFalse();
        assertThat(readiness.missingRequirements()).isEmpty();
    }

    private static KftcOpenBankingProperties configuredProperties() {
        final KftcOpenBankingProperties properties = new KftcOpenBankingProperties();
        properties.setEnabled(true);
        properties.setApiKey("api-key");
        properties.setClientId("client-id");
        properties.setClientSecret("client-secret");
        properties.setClientUseCode("client-use-code");
        properties.setCallbackUrl("https://halley.example.com/api/open-banking/oauth/callback");
        properties.setOauthServiceConfirmed(true);
        properties.setBalanceInquiryServiceConfirmed(true);
        return properties;
    }
}
