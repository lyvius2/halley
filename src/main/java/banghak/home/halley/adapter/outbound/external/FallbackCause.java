package banghak.home.halley.adapter.outbound.external;

import java.util.regex.Pattern;

/** FallbackFactory가 받은 원인을 로그 한 줄로 요약한다. 폴백이 원인을 삼키면 외부 연동 실패가 */
public final class FallbackCause {

 /** 인증키를 가린다. */
    private static final Pattern SECRET = Pattern.compile(
            "(?i)(serviceKey|apiKey|api_key|authKey|auth_key|access_token|client_secret|key)=[^&\\s\\]]*");

    private FallbackCause() {
    }

    public static String describe(Throwable cause) {
        if (cause == null) {
            return "원인 미상";
        }
        return cause.getClass().getSimpleName() + ": " + mask(cause.getMessage());
    }

 /** 쿼리 문자열에 실린 비밀을 지운다. 어떤 파라미터였는지는 남긴다. 원인 추적에 필요하다. */
    public static String mask(String message) {
        if (message == null) {
            return null;
        }
        return SECRET.matcher(message).replaceAll("$1=***");
    }
}
