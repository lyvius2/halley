package banghak.home.halley.adapter.inbound.web.dto;

/**
 * 알림이 왜 안 오는지 화면에서 보이게.
 *
 * 두 스위치가 환경변수에만 있어, 켜 두고도 매물 등록 알림만 안 오는 상황을
 * 화면에서 알 길이 없었습니다. 실제로 그 일을 겪었습니다.
 *
 * @param enabled               `SLACK_ENABLED` — 이게 꺼져 있으면 아무것도 안 나갑니다
 * @param notifyPropertyCreated `SLACK_NOTIFY_PROPERTY_CREATED` — 매물 등록만 따로 있는 스위치
 * @param baseUrlConfigured     `APP_BASE_URL` — 없으면 알림에 링크가 안 붙습니다
 */
public record NotificationSettingsResponse(
        boolean enabled,
        boolean notifyPropertyCreated,
        boolean baseUrlConfigured
) {
}
