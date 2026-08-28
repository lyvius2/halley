package banghak.home.halley.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 기동 시 외부 연동 키의 주입 여부를 한 줄로 남긴다. 키가 비어 있으면 어댑터가 외부 호출 없이
 * 빈 결과를 반환하는데(설계 INTERFACE_MANUAL 6장), 그 상태가 로그에 드러나지 않으면
 * "API 결과가 안 나온다"의 원인이 키 미주입인지 호출 실패인지 구분할 수 없다.
 */
@Slf4j
@Component
public class ExternalApiKeyReporter implements ApplicationRunner {

    private final Map<String, String> keys = new LinkedHashMap<>();

    public ExternalApiKeyReporter(@Value("${kakao.js-key:}") String kakaoJsKey,
                                  @Value("${kakao.rest-key:}") String kakaoRestKey,
                                  @Value("${odsay.api-key:}") String odsayApiKey,
                                  @Value("${ministry.service-key:}") String ministryServiceKey,
                                  @Value("${slack.webhook-url:}") String slackWebhookUrl) {
        keys.put("kakao.js-key", kakaoJsKey);
        keys.put("kakao.rest-key", kakaoRestKey);
        keys.put("odsay.api-key", odsayApiKey);
        keys.put("ministry.service-key", ministryServiceKey);
        keys.put("slack.webhook-url", slackWebhookUrl);
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
