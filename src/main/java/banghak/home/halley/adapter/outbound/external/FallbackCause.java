package banghak.home.halley.adapter.outbound.external;

/**
 * FallbackFactory가 받은 원인을 로그 한 줄로 요약한다. 폴백이 원인을 삼키면 외부 연동 실패가
 * "결과가 비어 있음"으로만 보여 원인 추적이 불가능해지므로, 모든 폴백은 이 형식으로 남긴다.
 */
public final class FallbackCause {

    private FallbackCause() {
    }

    public static String describe(Throwable cause) {
        if (cause == null) {
            return "원인 미상";
        }
        return cause.getClass().getSimpleName() + ": " + cause.getMessage();
    }
}
