package banghak.home.halley.domain.property;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("동/호에서 동만 뽑는다 (설계 I268)")
class BuildingNumberTest {

    @Test
    @DisplayName("호가 붙어 있으면 버린다 — 붙여서 물으면 결과가 없다")
    void dropsTheUnitNumber() {
        assertThat(BuildingNumber.of("102동 1503호")).contains("102동");
        assertThat(BuildingNumber.of("102동")).contains("102동");
        assertThat(BuildingNumber.of(" 705동 ")).contains("705동");
    }

    @Test
    @DisplayName("숫자가 아닌 동도 있다")
    void acceptsNonNumericBuildings() {
        assertThat(BuildingNumber.of("가동")).contains("가동");
        assertThat(BuildingNumber.of("A동 201호")).contains("A동");
    }

    @Test
    @DisplayName("동을 모르면 비어 있다 — 건물을 가릴 수 없다")
    void emptyWhenThereIsNoBuilding() {
        assertThat(BuildingNumber.of("1503호")).isEmpty();
        assertThat(BuildingNumber.of("")).isEmpty();
        assertThat(BuildingNumber.of(null)).isEmpty();
    }
}
