package banghak.home.halley.application.service;

import banghak.home.halley.adapter.inbound.web.dto.NotificationLogResponse;
import banghak.home.halley.adapter.inbound.web.dto.NotificationSettingsResponse;
import banghak.home.halley.adapter.inbound.web.dto.ScoredPropertyResponse;
import banghak.home.halley.adapter.outbound.persistence.NotificationLogRepository;
import banghak.home.halley.adapter.outbound.persistence.PropertyRepository;
import banghak.home.halley.adapter.outbound.persistence.UserRepository;
import banghak.home.halley.application.port.out.external.SlackPort;
import banghak.home.halley.config.SlackProperties;
import banghak.home.halley.domain.notification.NotificationEventType;
import banghak.home.halley.domain.notification.NotificationLog;
import banghak.home.halley.domain.notification.NotificationStatus;
import banghak.home.halley.domain.property.DealType;
import banghak.home.halley.adapter.outbound.persistence.UserGroupRepository;
import banghak.home.halley.domain.group.UserGroup;
import banghak.home.halley.domain.property.Property;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

@Service
public class NotificationService {

    private final SlackPort slackPort;
    private final UserGroupRepository userGroupRepository;
    private final SlackProperties slackProperties;
    private final PropertyRepository propertyRepository;
    private final UserRepository userRepository;
    private final ScoringService scoringService;
    private final NotificationLogRepository notificationLogRepository;
    private final ObjectMapper objectMapper;
 /** Slack 에 실을 코멘트 길이. 넘으면 자른다. 채널이 덮인다 */
    private static final int COMMENT_PREVIEW_CHARS = 300;

 /** 알림에 붙일 매물 주소의 앞부분. 비우면 링크를 안 단다 */
    private final String baseUrl;

    public NotificationService(SlackPort slackPort,
                               UserGroupRepository userGroupRepository,
                               SlackProperties slackProperties,
                               PropertyRepository propertyRepository,
                               UserRepository userRepository,
                               ScoringService scoringService,
                               NotificationLogRepository notificationLogRepository,
                               ObjectMapper objectMapper,
                               @org.springframework.beans.factory.annotation.Value("${app.base-url:}")
                               String baseUrl) {
        this.slackPort = slackPort;
        this.userGroupRepository = userGroupRepository;
        this.slackProperties = slackProperties;
        this.propertyRepository = propertyRepository;
        this.userRepository = userRepository;
        this.scoringService = scoringService;
        this.notificationLogRepository = notificationLogRepository;
        this.objectMapper = objectMapper;
        this.baseUrl = baseUrl;
    }

    public void sendPropertyCreated(Long propertyId) {
        if (!shouldSend() || !slackProperties.isNotifyPropertyCreated()) {
            return;
        }
        final Property property = propertyRepository.findById(propertyId).orElse(null);
        if (property == null) {
            return;
        }
        final String webhook = webhookOfGroup(property.groupId());
        if (webhook == null) {
            return;
        }
        sendEvent(NotificationEventType.PROPERTY_CREATED, propertyId,
                buildCreatedMessage(property), webhook);
    }

 /** 매물이 지워졌다. */
    public void sendPropertyDeleted(Long groupId, String propertyName) {
        send(NotificationEventType.PROPERTY_DELETED, null, groupId,
                ":wastebasket: 매물이 삭제되었습니다 — " + text(propertyName));
    }

 /** 누가 뭐라고 썼는지까지. */
    public void sendCommentCreated(Long propertyId, String nickname, String content) {
        sendForProperty(NotificationEventType.COMMENT_CREATED, propertyId, property -> {
            final String head = ":speech_balloon: " + text(nickname) + "님이 "
                    + text(property.name()) + "에 의견을";
            return content == null || content.isBlank()
                    ? head + " 지웠습니다"
                    : head + " 남겼습니다\n\n" + quote(content);
        });
    }


    public void sendComfortScored(Long propertyId, String nickname, Integer score) {
        sendForProperty(NotificationEventType.COMFORT_SCORED, propertyId,
                property -> ":sparkles: " + text(nickname) + "님이 "
                        + text(property.name()) + "의 공간 쾌적함을 "
                        + (score == null ? "평가했습니다" : score + "점으로 평가했습니다 (5점 만점)"));
    }

 /** 남의 글을 Slack 인용으로. */
    private String quote(String content) {
        final String trimmed = content.strip();
        final String shown = trimmed.length() > COMMENT_PREVIEW_CHARS
                ? trimmed.substring(0, COMMENT_PREVIEW_CHARS) + "…"
                : trimmed;
        return escape(shown).lines().map(line -> "> " + line)
                .collect(java.util.stream.Collectors.joining("\n"));
    }

 /** 매물을 찾아 그 그룹으로 보낸다. 매물이 없으면 보낼 곳도 없다. */
    private void sendForProperty(NotificationEventType eventType, Long propertyId,
                                 java.util.function.Function<Property, String> message) {
        if (!shouldSend()) {
            return;
        }
        propertyRepository.findById(propertyId).ifPresent(property ->
                send(eventType, propertyId, property.groupId(), message.apply(property)));
    }

    private void send(NotificationEventType eventType, Long propertyId, Long groupId, String text) {
        if (!shouldSend()) {
            return;
        }
        final String webhook = webhookOfGroup(groupId);
        if (webhook == null) {
            return;
        }
        sendEvent(eventType, propertyId, text, webhook);
    }

    private String text(String value) {
        return value == null || value.isBlank() ? "(이름 없음)" : escape(value);
    }

 /** Slack 이 태그로 읽는 세 글자를 막는다. */
    private String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

 /** 웹훅이 실제로 닿는지 확인한다. 그룹 설정 화면에서 부른다. */
    public boolean testSend(String webhookUrl) {
        return slackPort.send(webhookUrl, ":tada: Halley에서 테스트 메시지를 보냅니다.");
    }

 /** 지금 알림이 어떤 상태인지. */
    public NotificationSettingsResponse notificationSettings() {
        return new NotificationSettingsResponse(
                slackProperties.isEnabled(),
                slackProperties.isNotifyPropertyCreated(),
                baseUrl != null && !baseUrl.isBlank());
    }

    public List<NotificationLogResponse> recentNotifications() {
        return notificationLogRepository.findLatest(50).stream()
                .map(log -> new NotificationLogResponse(
                        log.id(), log.eventType(), log.propertyId(), log.status(),
                        log.retryCount(), log.errorMessage(), log.createdAt(), log.sentAt()))
                .toList();
    }

 /** 재시도 대상(RETRYING, 3회 미만) 알림을 5분 주기 스케줄러가 재발송한다. */
    public void resendRetrying() {
        for (final NotificationLog log : notificationLogRepository.findRetrying(50)) {
            if (!shouldSend()) {
                return;
            }
            final String text = rebuildText(log);
            if (text == null) {
                continue;
            }
            final String webhook = webhookForRetry(log);
            if (webhook == null) {
                continue;
            }
            final boolean sent = slackPort.send(webhook, text);
            if (sent) {
                notificationLogRepository.updateStatus(log.id(), NotificationStatus.SENT, null, Instant.now());
            } else {
                notificationLogRepository.markRetry(log.id(), log.retryCount() + 1, "Slack 전송 실패");
            }
        }
    }

    private String rebuildText(NotificationLog log) {
        return switch (log.eventType()) {
            case PROPERTY_CREATED -> log.propertyId() == null ? null
                    : propertyRepository.findById(log.propertyId()).map(this::buildCreatedMessage).orElse(null);
            case LISTING_SOLD_OUT -> "판매완료 알림 (재전송)";
            case PROPERTY_DELETED -> "매물 삭제 알림 (재전송)";
            case COMMENT_CREATED -> "코멘트 알림 (재전송)";
            case COMFORT_SCORED -> "쾌적함 평가 알림 (재전송)";
        };
    }

 /** 재발송할 알림이 원래 나가야 했던 곳. */
    private String webhookForRetry(NotificationLog log) {
        if (log.propertyId() == null) {
            return null;
        }
        return propertyRepository.findById(log.propertyId())
                .map(p -> webhookOfGroup(p.groupId()))
                .orElse(null);
    }

 /** 그룹의 알림이 나갈 곳. */
    private String webhookOfGroup(Long groupId) {
        if (groupId == null) {
            return null;
        }
        return userGroupRepository.findById(groupId)
                .filter(UserGroup::hasWebhook)
                .map(UserGroup::slackWebhookUrl)
                .orElse(null);
    }

    private void sendEvent(NotificationEventType eventType, Long propertyId, String text,
                           String webhookUrl) {
        final NotificationLog log = notificationLogRepository.save(new NotificationLog(
                null, eventType, propertyId, "slack",
                NotificationStatus.RETRYING, 0, null, payload(propertyId), null, null));

        final boolean sent = slackPort.send(webhookUrl, decorate(eventType, propertyId, text));
        if (sent) {
            notificationLogRepository.updateStatus(log.id(), NotificationStatus.SENT, null, Instant.now());
        } else {
            notificationLogRepository.markRetry(log.id(), log.retryCount(), "Slack 전송 실패");
        }
    }

 /** 사람을 부르고, 볼 곳을 알려 준다. */
    String decorate(NotificationEventType eventType, Long propertyId, String text) {
        final StringBuilder sb = new StringBuilder("<!channel> ").append(text);
        final String suffix = eventType == null ? null : eventType.linkSuffix();
        if (propertyId != null && suffix != null && baseUrl != null && !baseUrl.isBlank()) {
            sb.append('\n').append(baseUrl.replaceAll("/+$", ""))
                    .append("/#/properties/").append(propertyId).append(suffix);
        }
        return sb.toString();
    }

 /** 알림 기능이 켜져 있는지. */
    private boolean shouldSend() {
        return slackProperties.isEnabled();
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
        if (p.createdBy() != null) {
            final String nickname = userRepository.findById(p.createdBy())
                    .map(u -> u.nickname()).orElse(null);
            if (nickname != null) {
                sb.append("등록: ").append(nickname).append('\n');
            }
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
        };
    }

    private String plain(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    private long fmtScore(BigDecimal score) {
        return score == null ? 0 : score.setScale(0, java.math.RoundingMode.HALF_UP).longValue();
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
