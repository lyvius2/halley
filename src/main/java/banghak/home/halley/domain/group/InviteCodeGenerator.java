package banghak.home.halley.domain.group;

import java.security.SecureRandom;

/** 초대 코드를 만든다. 숫자와 영문 대소문자가 섞인 8자리. */
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
