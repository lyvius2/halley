package banghak.home.halley.domain.property;

import java.util.Optional;

/** 사람이 적어 넣은 층. */
public record FloorValue(String raw, Integer floorNo, FloorBand band) {

    private static final String LOW = "저";
    private static final String MID = "중";
    private static final String HIGH = "고";

 /** 적을 수 있는 것. 숫자, 또는 저·중·고 한 글자. */
    public static boolean isValid(String raw) {
        return of(raw).map(v -> v.floorNo() != null || v.band() != null).orElse(false);
    }

 /** 입력값을 숫자 층 또는 층수 구간으로 변환한다. */
    public static Optional<FloorValue> of(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        final String trimmed = raw.trim();
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

 /** 화면에 그대로 쓰는 글자. 3 또는 저. */
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
