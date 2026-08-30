package banghak.home.halley.application.port.out.external;

/**
 * Slack 알림 (설계 I96).
 *
 * <p><b>보낼 곳을 함께 받습니다.</b> 알림도 그룹 경계를 지켜야 하는데, 웹훅이 하나로 고정돼
 * 있으면 우리 매물이 남의 채널에 뜹니다.
 */
public interface SlackPort {

    /**
     * @param webhookUrl 보낼 곳. 비어 있으면 <b>보내지 않습니다</b> — 전역 주소로
     *                   흘려보내면 그게 곧 누수입니다
     * @return 실제로 보냈는지
     */
    boolean send(String webhookUrl, String text);
}
