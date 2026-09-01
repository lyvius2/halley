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
    /** Slack 에 실을 코멘트 길이 (설계 I201). 넘으면 자른다 — 채널이 덮인다 */
    private static final int COMMENT_PREVIEW_CHARS = 300;

    /** 알림에 붙일 매물 주소의 앞부분 (설계 I189). 비우면 링크를 안 단다 */
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

    /**
     * 매물이 지워졌다 (설계 I96).
     *
     * <p><b>이름을 인자로 받습니다.</b> 알림은 커밋 뒤에 나가는데 그때는 이미 매물이 없어
     * 조회로는 이름을 알 수 없습니다.
     */
    public void sendPropertyDeleted(Long groupId, String propertyName) {
        send(NotificationEventType.PROPERTY_DELETED, null, groupId,
                ":wastebasket: 매물이 삭제되었습니다 — " + text(propertyName));
    }

    /**
     * 누가 뭐라고 썼는지까지 (설계 I201).
     *
     * <p>전에는 "의견을 남겼습니다"까지만 갔습니다. <b>무슨 의견인지 보려면 들어가야</b>
     * 했는데, 대개 그 한 줄이 알림의 전부입니다.
     *
     * @param content 남기거나 고친 글. <b>null 이면 지운 것</b>이라 실을 내용이 없다
     */
    public void sendCommentCreated(Long propertyId, String nickname, String content) {
        sendForProperty(NotificationEventType.COMMENT_CREATED, propertyId, property -> {
            final String head = ":speech_balloon: " + text(nickname) + "님이 "
                    + text(property.name()) + "에 의견을";
            return content == null || content.isBlank()
                    ? head + " 지웠습니다"
                    : head + " 남겼습니다\n\n" + quote(content);
        });
    }

    /** @param score 1~5. 점수를 빼면 "평가했다"만 남아 좋다는 건지 나쁘다는 건지 모른다 */
    public void sendComfortScored(Long propertyId, String nickname, Integer score) {
        sendForProperty(NotificationEventType.COMFORT_SCORED, propertyId,
                property -> ":sparkles: " + text(nickname) + "님이 "
                        + text(property.name()) + "의 공간 쾌적함을 "
                        + (score == null ? "평가했습니다" : score + "점으로 평가했습니다 (5점 만점)"));
    }

    /**
     * 남의 글을 Slack 인용으로 (설계 I201).
     *
     * <p><b>줄마다 `>` 를 붙입니다.</b> 첫 줄에만 붙이면 두 번째 줄부터 인용이 풀려
     * 우리가 쓴 문장인지 사용자가 쓴 문장인지 구분이 사라집니다.
     *
     * <p><b>자릅니다.</b> 코멘트는 2,000자까지 들어가는데 그대로 실으면 채널이 덮입니다.
     */
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

    /**
     * Slack 이 태그로 읽는 세 글자를 막는다 (설계 I201).
     *
     * <p>우리가 `<!channel>` 을 쓰므로 <b>사람이 쓴 글에 `<...>` 가 있으면</b>
     * Slack 이 그것도 태그로 읽습니다. 단지명이나 코멘트에서 온 값만 지납니다 —
     * 메시지 전체에 걸면 우리가 쓴 `<!channel>` 까지 깨집니다.
     */
    private String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /** 웹훅이 실제로 닿는지 확인한다 (설계 I96). 그룹 설정 화면에서 부른다. */
    public boolean testSend(String webhookUrl) {
        return slackPort.send(webhookUrl, ":tada: Halley에서 테스트 메시지를 보냅니다.");
    }

    /**
     * 지금 알림이 어떤 상태인지 (설계 I215).
     *
     * <p>스위치가 환경변수에만 있어, 켜 두고도 <b>매물 등록 알림만 안 오는</b> 이유를
     * 화면에서 알 길이 없었습니다. 값을 바꾸는 것이 아니라 <b>보여 주기만</b> 합니다 —
     * 배포로 정하는 값을 화면에서 고칠 수 있는 척하면 그것도 거짓말입니다.
     */
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

    /**
     * 재시도 대상(RETRYING, 3회 미만) 알림을 5분 주기 스케줄러가 재발송한다 (설계 12.2).
     */
    public void resendRetrying() {
        for (final NotificationLog log : notificationLogRepository.findRetrying(50)) {
            if (!shouldSend()) {
                return;
            }
            final String text = rebuildText(log);
            if (text == null) {
                continue;
            }
            // 재발송도 원래 나가야 했던 곳으로 보낸다 (설계 I96).
            // 전역으로 돌리면 재시도 한 번에 남의 채널로 새어 나간다
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

    /**
     * 재발송할 알림이 원래 나가야 했던 곳 (설계 I96).
     *
     * <p>매물에 딸린 알림이면 그 매물의 그룹, 아니면 운영자에게 갑니다. 그룹 웹훅이
     * 그 사이에 지워졌으면 <b>보내지 않습니다</b> — 옛 알림을 엉뚱한 곳에 흘리지 않습니다.
     */
    private String webhookForRetry(NotificationLog log) {
        if (log.propertyId() == null) {
            // 매물에 딸리지 않은 알림은 보낼 곳이 없다 (설계 I96) — 시스템 알림을 두지 않는다
            return null;
        }
        return propertyRepository.findById(log.propertyId())
                .map(p -> webhookOfGroup(p.groupId()))
                .orElse(null);
    }

    /**
     * 그룹의 알림이 나갈 곳 (설계 I96).
     *
     * <p><b>없으면 보내지 않습니다.</b> 전역 웹훅으로 흘려보내면 그게 곧 누수입니다 —
     * 우리 매물이 남의 채널에 뜹니다.
     */
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

    /**
     * 사람을 부르고, 볼 곳을 알려 준다 (설계 I191 · I189).
     *
     * <p><b>`@channel` 을 붙입니다.</b> 알림이 그냥 흘러가면 아무도 안 봅니다 —
     * 매물이 올라온 것을 <b>그날 알아야</b> 의미가 있습니다.
     * 테스트 메시지는 예외입니다 — 연결을 확인하려고 채널 전체를 부를 이유가 없습니다.
     *
     * <p><b>볼 곳까지 짚어 줍니다 (설계 I201).</b> 쾌적함 알림은 채점 화면으로,
     * 코멘트 알림은 코멘트 화면으로 갑니다 — 어디로 갈지는 알림 종류가 압니다
     * (`NotificationEventType.linkSuffix`). 전에는 전부 매물 첫 화면이라
     * 거기서 <b>다시 찾아 들어가야</b> 했습니다.
     *
     * <p>삭제 알림에는 링크를 안 답니다 — <b>이미 없는 매물</b>입니다.
     */
    String decorate(NotificationEventType eventType, Long propertyId, String text) {
        final StringBuilder sb = new StringBuilder("<!channel> ").append(text);
        final String suffix = eventType == null ? null : eventType.linkSuffix();
        if (propertyId != null && suffix != null && baseUrl != null && !baseUrl.isBlank()) {
            sb.append('\n').append(baseUrl.replaceAll("/+$", ""))
                    .append("/properties/").append(propertyId).append(suffix);
        }
        return sb.toString();
    }

    /**
     * 알림 기능이 켜져 있는지 (설계 I96).
     *
     * <p><b>전역 웹훅 주소는 더 이상 보지 않습니다.</b> 보낼 곳은 매물의 그룹마다 다르므로
     * 여기서 전역 주소로 막으면 그룹 웹훅을 넣어 둔 사람도 알림을 못 받습니다.
     * 주소가 없는 경우는 발송 직전에 각자 걸러집니다.
     */
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
