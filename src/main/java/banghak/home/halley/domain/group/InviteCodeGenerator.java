package banghak.home.halley.domain.group;

import java.security.SecureRandom;

/**
 * 초대 코드를 만든다 — 숫자와 영문 대소문자가 섞인 8자리 (설계 I89 · 규칙 8).
 *
 * <p><b>{@link SecureRandom}을 씁니다.</b> 코드 하나를 맞히면 남의 그룹에 들어가 그 그룹의
 * 매물을 전부 보게 됩니다. 예측 가능한 난수를 쓸 자리가 아닙니다.
 *
 * <p>사람이 옮겨 적는 값이라 <b>헷갈리는 글자를 뺍니다</b> — `0`·`O`·`o`, `1`·`l`·`I`.
 * 잘못 적으면 왜 안 되는지 알기 어렵습니다.
 */
public final class InviteCodeGenerator {

    private static final String ALPHABET =
            "23456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz";
    private static final int LENGTH = 8;
    private static final SecureRandom RANDOM = new SecureRandom();

    public static String generate() {
        final StringBuilder code = new StringBuilder(LENGTH);
        for (int i = 0; i < LENGTH; i++) {
            code.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return code.toString();
    }

    private InviteCodeGenerator() {
    }
}
