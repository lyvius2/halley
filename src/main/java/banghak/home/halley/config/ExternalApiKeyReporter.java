package banghak.home.halley.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Component
public class ExternalApiKeyReporter implements ApplicationRunner {

    private final Map<String, String> keys = new LinkedHashMap<>();

    public ExternalApiKeyReporter(@Value("${kakao.js-key:}") String kakaoJsKey,
                                  @Value("${kakao.rest-key:}") String kakaoRestKey,
                                  @Value("${odsay.api-key:}") String odsayApiKey,
                                  @Value("${ministry.service-key:}") String ministryServiceKey,
                                  @Value("${vworld.api-key:}") String vworldApiKey,
                                  @Value("${llm.claude.api-key:}") String claudeApiKey,
                                  @Value("${law.oc:}") String lawOc,
                                  @Value("${fss.api-key:}") String fssApiKey,
                                  @Value("${ecos.api-key:}") String ecosApiKey) {
        keys.put("kakao.js-key", kakaoJsKey);
        keys.put("kakao.rest-key", kakaoRestKey);
        keys.put("odsay.api-key", odsayApiKey);
        keys.put("ministry.service-key", ministryServiceKey);
        keys.put("vworld.api-key", vworldApiKey);
        keys.put("llm.claude.api-key", claudeApiKey);
        keys.put("law.oc", lawOc);
        keys.put("fss.api-key", fssApiKey);
        keys.put("ecos.api-key", ecosApiKey);
    }

    @Override
    public void run(ApplicationArguments args) {
        keys.forEach((name, value) -> log.info("External API key {} : {}", name, mask(value)));
    }

    static String mask(String value) {
        if (value == null || value.isBlank()) {
            return "NOT SET (this integration returns empty results)";
        }
        if (value.length() <= 8) {
            return "set (****)";
        }
        return "set (" + value.substring(0, 4) + "****" + value.substring(value.length() - 4) + ")";
    }
}
