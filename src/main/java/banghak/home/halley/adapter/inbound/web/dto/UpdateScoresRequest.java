package banghak.home.halley.adapter.inbound.web.dto;

import java.math.BigDecimal;
import java.util.Map;

public record UpdateScoresRequest(Map<String, BigDecimal> scores) {
}
