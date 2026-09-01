package banghak.home.halley.adapter.inbound.web.dto;

/**
 * 알림이 <b>왜 안 오는지</b> 화면에서 보이게 (설계 I215).
 *
 * <p>두 스위치가 환경변수에만 있어, 켜 두고도 <b>매물 등록 알림만 안 오는</b> 상황을
 * 화면에서 알 길이 없었습니다. 실제로 그 일을 겪었습니다.
 *
 * @param enabled               `SLACK_ENABLED` — 이게 꺼져 있으면 <b>아무것도</b> 안 나갑니다
 * @param notifyPropertyCreated `SLACK_NOTIFY_PROPERTY_CREATED` — 매물 등록만 따로 있는 스위치
 * @param baseUrlConfigured     `APP_BASE_URL` — 없으면 알림에 <b>링크가 안 붙습니다</b>
 */
public record NotificationSettingsResponse(
        boolean enabled,
        boolean notifyPropertyCreated,
        boolean baseUrlConfigured
) {
}
