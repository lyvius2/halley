package banghak.home.halley.application.service;

import banghak.home.halley.adapter.outbound.persistence.NotificationLogRepository;
import banghak.home.halley.adapter.outbound.persistence.PropertyRepository;
import banghak.home.halley.application.port.out.external.SlackPort;
import banghak.home.halley.config.SlackProperties;
import banghak.home.halley.domain.notification.NotificationEventType;
import banghak.home.halley.domain.notification.NotificationLog;
import banghak.home.halley.domain.notification.NotificationStatus;
import banghak.home.halley.domain.property.DealType;
import banghak.home.halley.domain.property.Property;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Service
public class NotificationService {

    private final SlackPort slackPort;
    private final SlackProperties slackProperties;
    private final PropertyRepository propertyRepository;
    private final NotificationLogRepository notificationLogRepository;
    private final ObjectMapper objectMapper;

    public NotificationService(SlackPort slackPort,
                               SlackProperties slackProperties,
                               PropertyRepository propertyRepository,
                               NotificationLogRepository notificationLogRepository,
                               ObjectMapper objectMapper) {
        this.slackPort = slackPort;
        this.slackProperties = slackProperties;
        this.propertyRepository = propertyRepository;
        this.notificationLogRepository = notificationLogRepository;
        this.objectMapper = objectMapper;
    }

    public void sendPropertyCreated(Long propertyId) {
        if (!shouldSend() || !slackProperties.isNotifyPropertyCreated()) {
            return;
        }
        final Property property = propertyRepository.findById(propertyId).orElse(null);
        if (property == null) {
            return;
        }
        sendEvent(NotificationEventType.PROPERTY_CREATED, propertyId, buildCreatedMessage(property));
    }

    public void sendListingsSoldOut(List<Property> soldOut) {
        if (!shouldSend() || !slackProperties.isNotifySoldOut()) {
            return;
        }
        final StringBuilder sb = new StringBuilder(":house: 판매완료 감지\n\n");
        for (final Property p : soldOut) {
            sb.append("• ").append(p.name()).append("  ")
                    .append(p.priceDeposit() == null ? "" : fmtWon(p.priceDeposit()))
                    .append('\n');
        }
        sendEvent(NotificationEventType.LISTING_SOLD_OUT, null, sb.toString());
    }

    public void sendBatchBlocked() {
        if (!shouldSend()) {
            return;
        }
        sendEvent(NotificationEventType.BATCH_BLOCKED, null, ":no_entry: 생존 확인 배치가 봇 차단(403/429)으로 중단되었습니다.");
    }

    public void sendBatchCircuitOpen() {
        if (!shouldSend()) {
            return;
        }
        sendEvent(NotificationEventType.BATCH_CIRCUIT_OPEN, null,
                ":warning: 전체 매물의 과반이 GONE 판정 — 배치 서킷 개방, 상태 변경 없음.");
    }

    public void sendBatchSummary(int total, int alive, int gone, int error) {
        if (!shouldSend()) {
            return;
        }
        sendEvent(NotificationEventType.BATCH_SUMMARY, null,
                "점검 " + total + "건 / 정상 " + alive + " · GONE " + gone + " · 오류 " + error);
    }

    public boolean testSend() {
        return slackPort.send(":tada: Halley에서 테스트 메시지를 보냅니다.");
    }

    private void sendEvent(NotificationEventType eventType, Long propertyId, String text) {
        final NotificationLog log = notificationLogRepository.save(new NotificationLog(
                null, eventType, propertyId, "slack",
                NotificationStatus.RETRYING, 0, null, payload(propertyId), null, null));

        final boolean sent = slackPort.send(text);
        if (sent) {
            notificationLogRepository.updateStatus(log.id(), NotificationStatus.SENT, null, Instant.now());
        } else {
            notificationLogRepository.updateStatus(log.id(), NotificationStatus.FAILED, "Slack 전송 실패", null);
        }
    }

    private boolean shouldSend() {
        return slackProperties.isEnabled()
                && slackProperties.getWebhookUrl() != null
                && !slackProperties.getWebhookUrl().isBlank();
    }

    private ObjectNode payload(Long propertyId) {
        return objectMapper.createObjectNode().put("propertyId", propertyId);
    }

    private String buildCreatedMessage(Property p) {
        final StringBuilder sb = new StringBuilder();
        sb.append(":house_with_garden: 새 매물이 등록되었습니다\n\n");
        sb.append('*').append(p.name() == null ? "-" : p.name()).append('*');
        final String deal = dealLabel(p.dealType());
        if (deal != null) {
            sb.append("  ").append(deal);
        }
        final String price = p.priceDeposit() == null ? null : fmtWon(p.priceDeposit());
        if (price != null) {
            sb.append(' ').append(price);
        }
        sb.append('\n');
        sb.append("전용 ").append(p.areaExclusiveM2() == null ? "-" : plain(p.areaExclusiveM2()) + "㎡");
        if (p.floorNo() != null) {
            sb.append(" · ").append(p.floorNo()).append('/')
                    .append(p.floorTotal() == null ? "?" : p.floorTotal()).append("층");
        }
        if (p.direction() != null) {
            sb.append(" · ").append(p.direction());
        }
        sb.append('\n');
        if (p.addressJibun() != null) {
            sb.append(p.addressJibun()).append('\n');
        }
        return sb.toString();
    }

    private String dealLabel(DealType type) {
        if (type == null) {
            return null;
        }
        return switch (type) {
            case SALE -> "매매";
            case JEONSE -> "전세";
            case MONTHLY -> "월세";
        };
    }

    private String plain(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    private String fmtWon(Long won) {
        final long eok = won / 100_000_000;
        final long man = (won % 100_000_000) / 10_000;
        final StringBuilder sb = new StringBuilder();
        if (eok > 0) {
            sb.append(eok).append("억 ");
        }
        if (man > 0) {
            sb.append(String.format("%,d", man)).append("만원");
        }
        if (sb.isEmpty()) {
            sb.append("0원");
        }
        return sb.toString().trim();
    }
}
