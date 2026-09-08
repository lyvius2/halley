package banghak.home.halley.config;

import java.util.ArrayList;
import java.util.List;

/** 금융결제원 오픈뱅킹 연동을 시작하기 전의 설정 점검 결과다. */
public record KftcOpenBankingReadiness(boolean enabled, List<String> missingRequirements) {

    public KftcOpenBankingReadiness {
        missingRequirements = List.copyOf(missingRequirements);
    }

    /** 모든 환경 설정과 서비스 확인 항목이 갖춰졌는지 반환한다. */
    public boolean isReady() {
        return enabled && missingRequirements.isEmpty();
    }

    static KftcOpenBankingReadiness assess(KftcOpenBankingProperties properties) {
        final List<String> missingRequirements = new ArrayList<>();
        addIfBlank(missingRequirements, properties.getApiKey(), "KFTC_API_KEY");
        addIfBlank(missingRequirements, properties.getClientId(), "KFTC_CLIENT_ID");
        addIfBlank(missingRequirements, properties.getClientSecret(), "KFTC_CLIENT_SECRET");
        addIfBlank(missingRequirements, properties.getClientUseCode(), "KFTC_CLIENT_USE_CODE");
        addIfBlank(missingRequirements, properties.getCallbackUrl(), "KFTC_CALLBACK_URL");
        addIfBlank(missingRequirements, properties.getTokenEncryptionKey(), "KFTC_TOKEN_ENCRYPTION_KEY");
        if (!properties.isOauthServiceConfirmed()) {
            missingRequirements.add("KFTC_OAUTH_SERVICE_CONFIRMED=true");
        }
        if (!properties.isBalanceInquiryServiceConfirmed()) {
            missingRequirements.add("KFTC_BALANCE_INQUIRY_SERVICE_CONFIRMED=true");
        }
        return new KftcOpenBankingReadiness(properties.isEnabled(), missingRequirements);
    }

    private static void addIfBlank(List<String> missingRequirements, String value, String name) {
        if (value == null || value.isBlank()) {
            missingRequirements.add(name);
        }
    }
}
