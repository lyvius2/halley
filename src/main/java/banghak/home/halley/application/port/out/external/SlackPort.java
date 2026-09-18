package banghak.home.halley.application.port.out.external;

public interface SlackPort {

    boolean send(String webhookUrl, String text);
}
