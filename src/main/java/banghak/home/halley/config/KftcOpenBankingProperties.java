package banghak.home.halley.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/** 금융결제원 오픈뱅킹 연동의 환경 설정값이다. */
@Getter
@Setter
@ConfigurationProperties(prefix = "kftc.open-banking")
public class KftcOpenBankingProperties {
    private boolean enabled;
    private String environment = "test";
    private String apiKey = "";
    private String clientId = "";
    private String clientSecret = "";
    private String clientUseCode = "";
    private String callbackUrl = "";
    private String tokenEncryptionKey = "";
    private Duration oauthStateTtl = Duration.ofMinutes(10);
    private boolean oauthServiceConfirmed;
    private boolean balanceInquiryServiceConfirmed;

    /** 현재 설정으로 OAuth와 잔액조회 구현을 시작할 수 있는지 확인한다. */
    public KftcOpenBankingReadiness readiness() {
        return KftcOpenBankingReadiness.assess(this);
    }
}
