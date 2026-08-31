package banghak.home.halley.adapter.outbound.external;

import java.util.regex.Pattern;

/**
 * FallbackFactory가 받은 원인을 로그 한 줄로 요약한다. 폴백이 원인을 삼키면 외부 연동 실패가
 * "결과가 비어 있음"으로만 보여 원인 추적이 불가능해지므로, 모든 폴백은 이 형식으로 남긴다.
 */
public final class FallbackCause {

    /**
     * 인증키를 가린다 (설계 I140).
     *
     * <p>Feign의 예외 메시지에는 <b>요청 URL이 통째로 들어갑니다.</b> 공공데이터포털은
     * 인증키를 쿼리 파라미터로 받으므로, 그대로 남기면 운영 로그에 <b>키 원문이 찍힙니다</b> —
     * 실제로 429 로그에서 국토부 키가 그렇게 노출됐습니다.
     *
     * <p>로그는 지우기 어렵고 여러 곳으로 복사됩니다. <b>애초에 안 남기는 편이 낫습니다.</b>
     */
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

    /** 쿼리 문자열에 실린 비밀을 지운다. 어떤 파라미터였는지는 남긴다 — 원인 추적에 필요하다. */
    public static String mask(String message) {
        if (message == null) {
            return null;
        }
        return SECRET.matcher(message).replaceAll("$1=***");
    }
}
