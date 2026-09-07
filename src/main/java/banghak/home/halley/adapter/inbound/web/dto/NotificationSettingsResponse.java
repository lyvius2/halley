package banghak.home.halley.adapter.inbound.web.dto;

/** 알림이 왜 안 오는지 화면에서 보이게. */
public record NotificationSettingsResponse(
        boolean enabled,
        boolean notifyPropertyCreated,
        boolean baseUrlConfigured
) {
}
