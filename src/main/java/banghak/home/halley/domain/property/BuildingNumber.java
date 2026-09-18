package banghak.home.halley.domain.property;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class BuildingNumber {

    /** 공백이 아닌 토막 중 `동` 으로 끝나는 첫 번째.  */
    private static final Pattern DONG = Pattern.compile("(?<![\\p{L}\\p{N}])([\\p{L}\\p{N}]{1,10}동)(?![\\p{L}\\p{N}])");

    public static Optional<String> of(String dongHo) {
        if (dongHo == null || dongHo.isBlank()) {
            return Optional.empty();
        }
        final Matcher matcher = DONG.matcher(dongHo.trim());
        return matcher.find() ? Optional.of(matcher.group(1)) : Optional.empty();
    }

    private BuildingNumber() {
    }
}
