package banghak.home.halley.adapter.inbound.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 모바일 상세에서 사진이 매물 정보를 밀어내지 않는가 (설계 I288).
 *
 * <p>사진 띠(`.m2-photos`)는 가로로 이어 붙는 격자라 폭이 사진 수만큼 늘어납니다.
 * 칸이 <b>내용보다 좁아질 수 있어야</b> 그 폭이 격자를 밀어내지 않습니다 —
 * `1fr` 은 `minmax(auto, 1fr)` 이라 좁아지지 않고, 360px 화면에서 격자가 950px 로
 * 부풀어 <b>매물 정보가 옆으로 밀려 안 보였습니다.</b>
 *
 * <p>화면 배치는 Java 로 재현할 수 없어(브라우저로 재서 확인했습니다) <b>막아 둔 표시가
 * 남아 있는지</b>만 지킵니다. 지우면 같은 버그가 조용히 돌아옵니다.
 */
@DisplayName("상세 격자가 사진에 밀리지 않는다 (설계 I288)")
class DetailGridCssTest {

    private static final Path CSS = Path.of("src/main/resources/static/css/app.css");

    @Test
    @DisplayName("칸이 내용보다 좁아질 수 있게 막아 뒀다")
    void keepsTheDetailGridFromBeingPushedWide() throws Exception {
        // given
        final String css = Files.readString(CSS, StandardCharsets.UTF_8);

        // then — 둘 중 하나만 있어도 막히지만, 나중에 격자를 바꿔도 버티게 둘 다 둔다
        assertThat(css)
                .as("모바일 격자가 minmax(0, ...) 을 잃으면 사진이 칸을 밀어낸다")
                .contains(".m2-grid { grid-template-columns: minmax(0, 1fr); }");
        assertThat(css)
                .as("칸이 내용보다 좁아질 수 없으면 안의 가로 스크롤이 격자를 밀어낸다")
                .contains(".m2-sec { min-width: 0; }");
    }

    @Test
    @DisplayName("넓은 화면 격자도 같은 보호를 유지한다")
    void keepsTheSameGuardOnWideScreens() throws Exception {
        // given
        final String css = Files.readString(CSS, StandardCharsets.UTF_8);

        // then
        assertThat(css).contains(".m2-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr));");
    }
}
