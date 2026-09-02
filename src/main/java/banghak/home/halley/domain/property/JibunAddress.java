package banghak.home.halley.domain.property;

import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * 지번주소에서 <b>법정동과 번지</b>를 뽑는다 (설계 I257).
 *
 * <pre>
 * 서울시 성북구 정릉동 1037      →  정릉동 · 1037
 * 서울시 성북구 안암동3가 138     →  안암동3가 · 138
 * 경기도 하남시 망월동 938        →  망월동 · 938
 * </pre>
 *
 * <h4>왜 필요한가</h4>
 *
 * <p>국토부 실거래를 찾을 때 <b>단지명이 열쇠</b>였습니다. 그런데 이름이 통째로
 * 바뀐 단지가 있습니다 — `한화포레나정릉` 은 대우 `푸르지오` → 한화 `꿈에그린` →
 * `포레나` 로 이어져, 국토부에 옛 이름으로 남아 있으면 <b>글자가 하나도 안 겹칩니다.</b>
 *
 * <p>그런데 국토부 응답에 <b>법정동과 번지가 이미 옵니다.</b>
 * ({@code umdNm}·{@code jibun}) 그걸 안 쓰고 있었습니다.
 *
 * <h4>동 이름은 마지막 시·군·구 다음 토막이다</h4>
 *
 * <p>정규식으로 {@code 동|읍|면} 에서 끊으면 <b>`안암동3가` 가 `안암동` 이 됩니다.</b>
 * 국토부는 `안암동3가` 로 주므로 그렇게 끊으면 영영 안 맞습니다.
 *
 * <p>공백으로 잘라 <b>마지막 시·군·구</b>를 찾고 그 다음 토막을 통째로 씁니다 —
 * `서울시 노원구` 처럼 둘이 있으면 뒤엣것이 기준입니다.
 */
public record JibunAddress(String legalDong, int bonbun, int bubun) {

    /** `938` 또는 `138-2`. 국토부 {@code jibun} 도 같은 모양이다 */
    private static final Pattern BUNJI = Pattern.compile("^(\\d+)(?:-(\\d+))?$");
    private static final Pattern SIGUNGU = Pattern.compile(".+[시군구]$");

    /**
     * @return 동과 번지를 <b>둘 다</b> 못 뽑으면 비어 있다 — 반쪽으로는 가릴 수 없다
     */
    public static Optional<JibunAddress> of(String address) {
        if (address == null || address.isBlank()) {
            return Optional.empty();
        }
        final String[] tokens = address.trim().split("\\s+");
        int sigungu = -1;
        for (int i = 0; i < tokens.length; i++) {
            // `서울시 노원구` 처럼 둘이면 뒤엣것이 기준이다
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

    /** 국토부 응답의 {@code umdNm}·{@code jibun} 에서 (설계 I257). */
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

    /** 같은 동의 같은 번지인가. <b>동만 같은 것은 같다고 하지 않는다</b> */
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
