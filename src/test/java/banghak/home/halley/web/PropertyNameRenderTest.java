package banghak.home.halley.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 매물 이름은 <b>한 곳에서만</b> 만든다 (설계 I269).
 *
 * <p>같은 단지 매물이 여럿이면 동·호가 <b>유일한 구분</b>입니다. 그런데 상세 모달만
 * 동·호를 붙이고 목록·지도·나머지 모달 여덟 곳은 단지명만 보여 줬습니다 —
 * <b>같은 규칙이 여러 벌</b>이라 갈린 것입니다([I265]).
 *
 * <p>{@code propertyTitle()} 하나로 모았고, 여기서 그것이 유지되는지 봅니다.
 */
@DisplayName("매물 이름을 한 곳에서 만드는가 (설계 I269)")
class PropertyNameRenderTest {

    private static final Path TEMPLATE = Path.of("src/main/resources/templates/index.mustache");

    /** {@code x-text="…"} 안의 식. */
    private static final Pattern X_TEXT = Pattern.compile("x-text=\"([^\"]*)\"");
    /**
     * {@code …property?.name} 꼴 — 이름을 <b>직접</b> 꺼내 쓴 자리.
     *
     * <p>{@code propertyTitle(x.property)} 는 {@code .name} 이 없어 안 걸립니다.
     */
    private static final Pattern RAW_NAME =
            Pattern.compile("[Pp]roperty\\s*\\??\\.\\s*name\\b");

    @Test
    @DisplayName("매물 이름을 직접 꺼내 쓰는 자리가 없다")
    void everyPropertyNameGoesThroughPropertyTitle() throws IOException {
        final String template = Files.readString(TEMPLATE);

        final List<String> raw = new ArrayList<>();
        final Matcher expressions = X_TEXT.matcher(template);
        int seen = 0;
        while (expressions.find()) {
            seen++;
            final String expression = expressions.group(1);
            if (RAW_NAME.matcher(expression).find()) {
                raw.add(expression);
            }
        }

        // 검사가 실제로 무언가를 보고 있는가 — 0건이면 통과가 뜻이 없다
        assertThat(seen).as("x-text 를 하나도 못 찾았다면 정규식이 어긋난 것이다")
                .isGreaterThan(50);
        assertThat(template).as("propertyTitle 이 아예 안 쓰이면 이 검사는 아무것도 안 지킨다")
                .contains("propertyTitle(");
        assertThat(raw)
                .as("동·호가 빠져 같은 단지 매물이 구별되지 않는다 — propertyTitle() 을 쓸 것")
                .isEmpty();
    }
}
