package banghak.home.halley.domain.property;

import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/** 지번주소에서 법정동과 번지를 뽑는다. */
public record JibunAddress(String legalDong, int bonbun, int bubun) {

 /** 938 또는 138-2. 국토부 jibun 도 같은 모양이다 */
    private static final Pattern BUNJI = Pattern.compile("^(\\d+)(?:-(\\d+))?$");
    private static final Pattern SIGUNGU = Pattern.compile(".+[시군구]$");


    public static Optional<JibunAddress> of(String address) {
        if (address == null || address.isBlank()) {
            return Optional.empty();
        }
        final String[] tokens = address.trim().split("\\s+");
        int sigungu = -1;
        for (int i = 0; i < tokens.length; i++) {
            if (tokens[i].length() > 1 && SIGUNGU.matcher(tokens[i]).matches()) {
                sigungu = i;
            }
        }
        if (sigungu < 0 || sigungu + 2 >= tokens.length + 1) {
            return Optional.empty();
        }
        final String dong = sigungu + 1 < tokens.length ? tokens[sigungu + 1] : null;
        final String bunji = sigungu + 2 < tokens.length ? tokens[sigungu + 2] : null;
        if (dong == null || bunji == null) {
            return Optional.empty();
        }
        return bunjiOf(bunji).map(parts -> new JibunAddress(dong, parts[0], parts[1]));
    }

 /** 국토부 응답의 umdNm·jibun 에서. */
    public static Optional<JibunAddress> of(String legalDong, String jibun) {
        if (legalDong == null || legalDong.isBlank()) {
            return Optional.empty();
        }
        return bunjiOf(jibun)
                .map(parts -> new JibunAddress(legalDong.trim(), parts[0], parts[1]));
    }

    private static Optional<int[]> bunjiOf(String bunji) {
        if (bunji == null) {
            return Optional.empty();
        }
        final var matcher = BUNJI.matcher(bunji.trim());
        if (!matcher.matches()) {
            return Optional.empty();
        }
        return Optional.of(new int[]{
                Integer.parseInt(matcher.group(1)),
                matcher.group(2) == null ? 0 : Integer.parseInt(matcher.group(2))});
    }

 /** 같은 동의 같은 번지인가. 동만 같은 것은 같다고 하지 않는다 */
    public boolean sameLot(JibunAddress other) {
        return other != null
                && Objects.equals(legalDong, other.legalDong)
                && bonbun == other.bonbun
                && bubun == other.bubun;
    }

    public boolean sameDong(JibunAddress other) {
        return other != null && Objects.equals(legalDong, other.legalDong);
    }
}
