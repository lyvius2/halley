package banghak.home.halley.adapter.outbound.external.odsay;

import banghak.home.halley.application.port.out.external.OdsayTransitPort;
import banghak.home.halley.config.exception.TransitSearchFailedException;
import banghak.home.halley.domain.scoring.TransitResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class OdsayTransitAdapter implements OdsayTransitPort {

    private final OdsayTransitFeignClient client;
    private final String apiKey;
    private final ObjectMapper objectMapper;

    public OdsayTransitAdapter(OdsayTransitFeignClient client,
                               @Value("${odsay.api-key:}") String apiKey,
                               ObjectMapper objectMapper) {
        this.client = client;
        this.apiKey = apiKey;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean isEnabled() {
        return apiKey != null && !apiKey.isBlank();
    }

    @Override
    public TransitResult findTransit(double startX, double startY, double endX, double endY) {
        if (apiKey == null || apiKey.isBlank()) {
            return TransitResult.missing();
        }
        final String json = client.findTransit(apiKey, startX, startY, endX, endY);
        if (json == null) {
            return TransitResult.missing();
        }
        return TransitResult.mapResult(parse(json));
    }

    private JsonNode parse(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (JacksonException e) {
            throw new TransitSearchFailedException("ODsay 응답 파싱에 실패했습니다");
        }
    }
}
