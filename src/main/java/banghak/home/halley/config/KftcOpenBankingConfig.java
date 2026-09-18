package banghak.home.halley.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** 금융결제원 오픈뱅킹 설정을 등록한다. */
@Configuration
@EnableConfigurationProperties(KftcOpenBankingProperties.class)
public class KftcOpenBankingConfig {
}
