package banghak.home.halley.application.port.out.external;

/** Slack 알림. */
public interface SlackPort {

 /** 흘려보내면 그게 곧 누수입니다 */
    boolean send(String webhookUrl, String text);
}
