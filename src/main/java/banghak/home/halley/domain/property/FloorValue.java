package banghak.home.halley.domain.property;

import java.util.Optional;

/**
 * 사람이 적어 넣은 층 (설계 I286).
 *
 * <p>층은 <b>숫자만이 아닙니다.</b> 네이버는 저층 매물의 층수를 감춰 `저/15층` 처럼
 * <b>밴드</b>로 줍니다. 채점은 이미 밴드를 다루는데(`FloorScorer` — 저=0, 중·고=만점)
 * <b>들어오는 길이 숫자뿐</b>이라, 밴드로 적힌 매물은 파싱도 저장도 되지 않았습니다.
 *
 * <p>적힌 것을 그대로 두고({@code raw}), 숫자면 {@code floorNo} 로 밴드면
 * {@code floorBand} 로 나눕니다. <b>둘 다 되는 값은 없습니다.</b>
 *
 * <p>가르는 규칙을 여기 한 곳에 둡니다 — 등록·수정·붙여넣기가 각자 가르면
 * 언젠가 한 곳이 어긋납니다.
 */
public record FloorValue(String raw, Integer floorNo, FloorBand band) {

    private static final String LOW = "저";
    private static final String MID = "중";
    private static final String HIGH = "고";

    /** 적을 수 있는 것 — 숫자, 또는 저·중·고 한 글자. */
    public static boolean isValid(String raw) {
        return of(raw).map(v -> v.floorNo() != null || v.band() != null).orElse(false);
    }

    /**
     * 적힌 값을 가른다. 비었거나 알 수 없는 글자면 비어 있는 값을 돌려준다 —
     * <b>예외를 던지지 않습니다.</b> 파싱 실패는 MISSING 으로 남기는 것이 이 프로젝트의
     * 규칙입니다(AGENTS.md).
     *
     * <p><b>`층` 이 붙어 와도 읽습니다.</b> 이 칸은 원래 붙여넣기에서 본 글자를 그대로
     * 담던 자리라 `중층` · `3층` 처럼 옵니다. 화면 입력은 숫자와 저·중·고 로 좁혔지만,
     * 예전에 담긴 값과 다른 경로로 들어오는 값까지 못 읽으면 <b>멀쩡한 층이 사라집니다.</b>
     */
    public static Optional<FloorValue> of(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        final String trimmed = raw.trim();
        // 뒤에 붙은 `층` 은 표기일 뿐 값이 아니다
        final String bare = trimmed.endsWith("층") && trimmed.length() > 1
                ? trimmed.substring(0, trimmed.length() - 1).trim()
                : trimmed;
        final FloorBand band = bandOf(bare);
        if (band != null) {
            return Optional.of(new FloorValue(bare, null, band));
        }
        try {
            return Optional.of(new FloorValue(bare, Integer.parseInt(bare), null));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private static FloorBand bandOf(String raw) {
        return switch (raw) {
            case LOW -> FloorBand.LOW;
            case MID -> FloorBand.MID;
            case HIGH -> FloorBand.HIGH;
            default -> null;
        };
    }

    /** 화면에 그대로 쓰는 글자 — `3` 또는 `저`. */
    public static String label(Integer floorNo, FloorBand band) {
        if (band != null) {
            return switch (band) {
                case LOW -> LOW;
                case MID -> MID;
                case HIGH -> HIGH;
            };
        }
        return floorNo == null ? null : String.valueOf(floorNo);
    }
}
