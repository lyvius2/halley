package banghak.home.halley.adapter.inbound.web.dto;

public record NotificationSettingsResponse(
        boolean enabled,
        boolean notifyPropertyCreated,
        boolean baseUrlConfigured
) {
}
